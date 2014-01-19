package org.fengzh.tools.net.revsever.remote;

import java.io.Closeable;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.fengzh.tools.net.core.ChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteAgent implements ChannelHandler {

	private static final Logger logger = LoggerFactory
			.getLogger(RemoteAgent.class);

	private ScheduledExecutorService scheduler;

	private InetSocketAddress controlAddress;

	private RemoteAgentHandler processingHandler;

	private Set<SocketChannel> workingChannels = new HashSet<SocketChannel>();

	private Set<SocketChannel> idleChannels = new HashSet<SocketChannel>();

	public static int MAX_IDLE = 4;

	public RemoteAgent(RemoteAgentHandler handler) {
		this.processingHandler = handler;
	}

	public void start(Selector selector) throws IOException {
		scheduler = Executors.newSingleThreadScheduledExecutor();
		connect(selector);
	}

	public void stop() {
		scheduler.shutdown();
		// TODO
	}

	public int getIdleCount() {
		return idleChannels.size();
	}

	public synchronized void connect(Selector selector) throws IOException {
		SocketChannel controlChannel = null;
		try {
			controlChannel = SocketChannel.open();
			controlChannel.configureBlocking(false);
			controlChannel.register(selector, SelectionKey.OP_CONNECT, this);
			controlChannel.connect(getControlAddress(true));
			logger.debug("Remote agent is connecting to control server on {}.",
					getControlAddress(false));
		} catch (IOException e) {
			logger.warn("Start remote agent failure on "
					+ getControlAddress(false), e);
			close(controlChannel);
			throw e;
		} catch (Exception e) {
			logger.warn("Start remote agent failure on "
					+ getControlAddress(false), e);
			close(controlChannel);
			throw new IOException(e);
		}
	}

	private void close(Closeable closeable) {
		try {
			if (closeable != null) {
				closeable.close();
			}
		} catch (IOException e) {
			logger.debug("Close failure: " + closeable, e);
		}
	}

	private void validateChannels() {
		for (SocketChannel channel : new HashSet<SocketChannel>(workingChannels)) {
			if (!channel.isConnected()) {
				close(channel);
			}
			if (!channel.isOpen()) {
				workingChannels.remove(channel);
			}
		}
		for (SocketChannel channel : new HashSet<SocketChannel>(idleChannels)) {
			if (!channel.isConnected()) {
				close(channel);
			}
			if (!channel.isOpen()) {
				idleChannels.remove(channel);
			}
		}
	}

	public void onProcessing(SelectionKey key) throws IOException {
		final Selector selector = key.selector();
		SocketChannel controlChannel = (SocketChannel) key.channel();
		if (key.isConnectable()) {
			try {
				controlChannel.finishConnect();
			} catch (ConnectException e) {
				validateChannels();
				logger.debug(
						"Connect to control server failure, and waiting for reconnecting.",
						e);
				// connection failure
				scheduler.schedule(new Runnable() {

					public void run() {
						try {
							logger.debug("Start reconnecting");
							// wake up to enable register (not blocked)
							selector.wakeup();
							connect(selector);
						} catch (IOException e) {
							logger.error("Connect operation failure", e);
						}
					}
				}, 5, TimeUnit.SECONDS);
				return;
			} catch (IOException e) {
				// other IOE error
				throw e;
			}
			// Enable read now
			logger.info("Control server is connected on {}",
					controlChannel.socket());
			idleChannels.add(controlChannel);
			controlChannel.register(selector, SelectionKey.OP_READ,
					new ChannelHandler() {

						@Override
						public void onProcessing(SelectionKey key)
								throws IOException {
							// handle ping
							ByteBuffer buf = ByteBuffer.allocate(1);
							int count = -1;
							try {
								count = ((SocketChannel) key.channel())
										.read(buf);
							} catch (IOException e) {
								logger.warn("read ping message failed: {}",
										e.toString());
							}
							if (count == -1) {
								logger.debug("got eof or error in ping state");
								key.attach(-1);
								RemoteAgent.this.onProcessing(key);
							} else if (count == 1) {
								logger.debug("got ping message");
								key.channel().register(key.selector(),
										SelectionKey.OP_READ, RemoteAgent.this);
								// set channel id (http proxy or ssh, or others)
								key.attach(new Integer(buf.get(0)));
								RemoteAgent.this.onProcessing(key);
							}
						}
					});
		} else if (key.isReadable()) {
			// move from idle to working channel
			idleChannels.remove(controlChannel);
			workingChannels.add(controlChannel);
			// cancel this key
			key.cancel();
			connect(selector);
			// continue this reading/process
			processingHandler.onConnected(controlChannel, selector,
					((Integer) key.attachment()).intValue());
		}
	}

	public synchronized void prepareIdleConnections(Selector selector)
			throws IOException {
		boolean connecting = false;
		for (Iterator<SelectionKey> keys = selector.keys().iterator(); keys
				.hasNext();) {
			SelectionKey key = keys.next();
			if ((key.interestOps() & SelectionKey.OP_CONNECT) != 0
					&& key.isValid()) {
				connecting = true;
				break;
			}
		}
		int startCount = MAX_IDLE - idleChannels.size();
		if (startCount > 0 && !connecting) {
			connect(selector);
		}
	}

	public void setControlAddress(InetSocketAddress controlAddress) {
		this.controlAddress = controlAddress;
	}

	public synchronized InetSocketAddress getControlAddress(boolean refreshDns) {
		if (refreshDns) {
			InetSocketAddress newAddress = new InetSocketAddress(
					controlAddress.getHostName(), controlAddress.getPort());
			if (newAddress.getAddress() != null) {
				controlAddress = newAddress;
			} else {
				// unresolved, use the old one
			}
		}
		return controlAddress;
	}

	public static void main(String[] args) {
		System.out.println(new InetSocketAddress("ebanks.spdb.com.cn", 443));
	}

}
