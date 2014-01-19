package org.fengzh.tools.net.revsever;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.fengzh.tools.net.core.ChannelExchange;
import org.fengzh.tools.net.core.ChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class VirtualServer implements ChannelHandler {

	private static final Logger logger = LoggerFactory
			.getLogger(VirtualServer.class);

	private List<InetSocketAddress> localAddresses = new ArrayList<InetSocketAddress>();
	private List<ServerSocketChannel> serverChannels = new ArrayList<ServerSocketChannel>();
	private ControlServer controlServer;
	private Map<SocketChannel, Long> pendingSockets = new LinkedHashMap<SocketChannel, Long>();

	public VirtualServer(ControlServer controlServer) {
		if (controlServer == null) {
			throw new IllegalStateException("no control server configured");
		}
		this.controlServer = controlServer;
	}

	public void addLocalAddress(String localHost, int port) {
		localAddresses.add(new InetSocketAddress(localHost, port));
	}

	public boolean isRunning() {
		return !serverChannels.isEmpty();
	}

	public synchronized void start(Selector selector) throws IOException {
		if (!serverChannels.isEmpty())
			return;
		for (InetSocketAddress localAddress : localAddresses) {
			ServerSocketChannel channel = null;
			try {
				channel = ServerSocketChannel.open();
				channel.socket().bind(localAddress);
				logger.info("Virtaul server binds to {} successful.",
						localAddress);
				channel.configureBlocking(false);
				channel.register(selector, SelectionKey.OP_ACCEPT, this);
			} catch (IOException e) {
				logger.error("Start virtual server failure on " + localAddress,
						e);
				close(channel);
				throw e;
			}
			serverChannels.add(channel);
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

	public synchronized void stop() {
		for (int i = serverChannels.size() - 1; i >= 0; i--) {
			ServerSocketChannel channel = serverChannels.remove(i);
			;
			if (channel != null) {
				close(channel);
				logger.info("Virtual server stopped on {}",
						localAddresses.get(i));
			}
		}
	}

	public void onProcessing(SelectionKey key) throws IOException {
		if (!key.isAcceptable())
			return;
		ServerSocketChannel channel = (ServerSocketChannel) key.channel();
		int id = serverChannels.indexOf(channel);
		SocketChannel localChannel = channel.accept();
		SocketChannel remoteChannel = controlServer.receiveRemoteChannel();
		logger.trace("==> Retrive remote channel: {}", remoteChannel);
		if (remoteChannel == null) {
			pendingSockets.put(localChannel,
					new Long(10 * 1000L + System.currentTimeMillis()));
		} else {
			linkChannels(localChannel, remoteChannel, key.selector(), id);
		}
	}

	private void linkChannels(SocketChannel local, SocketChannel remote,
			Selector selector, int id) throws IOException {
		new ChannelExchange(local, remote, selector);
		// ping remote
		logger.debug("Start ping remote {}", remote.socket());
		ByteBuffer pingBuffer = ByteBuffer.allocate(1);
		pingBuffer.put((byte) id);
		pingBuffer.flip();
		remote.write(pingBuffer);
		logger.debug("End ping remote {}", remote.socket());
	}

	public boolean link(SocketChannel remote, SelectionKey key)
			throws IOException {
		if (pendingSockets.isEmpty())
			return false;
		SocketChannel local = pendingSockets.keySet().iterator().next();
		pendingSockets.remove(local);
		int id = serverChannels.indexOf(key.channel());
		linkChannels(local, remote, key.selector(), id);
		return true;
	}

}
