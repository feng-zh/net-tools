package org.fengzh.tools.net.core;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.NoRouteToHostException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChannelForward implements ChannelHandler, Closeable {

	public static interface ConnectStatusHandler {

		public void connected(SelectionKey key);

		public void connectError(SelectionKey key);

	}

	private static final Logger logger = LoggerFactory.getLogger(ChannelForward.class.getName());
	private InetSocketAddress address;
	private long timeout = Long.MAX_VALUE;
	private int connectTimeout = Integer.getInteger("connectTimeout", 3);
	private SocketChannel localChannel;
	private ConnectStatusHandler connectStatusHandler;
	private boolean forwarding = false;
	private SelectionKey forwardingKey = null;

	public ChannelForward(SocketChannel local) {
		this.localChannel = local;
	}

	public void setConnectStatusHandler(ConnectStatusHandler connectStatusHandler) {
		this.connectStatusHandler = connectStatusHandler;
	}

	public void startForward(Selector selector) throws IOException {
		SocketChannel remote = null;
		try {
			remote = SocketChannel.open();
			remote.configureBlocking(false);
			forwardingKey = remote.register(selector, SelectionKey.OP_CONNECT, this);
			remote.connect(getAddress());
			timeout = connectTimeout * 1000L + System.currentTimeMillis();
			logger.debug("Connecting to remote: {}", getAddress());
		} catch (NoRouteToHostException e) {
			// maybe Internet access lost
			logger.error("Cannot forward connection dueo to no route error", e);
			// cannot connect to remote
			close(remote);
			forwardingKey.cancel();
			forwardingKey = null;
			throw e;
		} catch (IOException e) {
			logger.warn("Cannot forward connection", e);
			// cannot connect to remote
			close(remote);
			if (connectStatusHandler != null) {
				connectStatusHandler.connectError(forwardingKey);
			}
			forwardingKey.cancel();
			forwardingKey = null;
		}
	}

	public boolean checkValidConnecting() {
		SelectionKey key = forwardingKey;
		if (key == null)
			return false;
		if (key != null && timeout - System.currentTimeMillis() <= 0) {
			// connecting is timeout
			if (key.isValid() && !key.isConnectable() && (key.interestOps() & SelectionKey.OP_CONNECT) != 0) {
				logger.info("Close timeout connection: {}", address);
				try {
					((SocketChannel) key.channel()).close();
				} catch (IOException e) {
					logger.warn("Close timeout connection got error", e);
				}
				if (connectStatusHandler != null) {
					connectStatusHandler.connectError(key);
				}
				key.cancel();
				forwardingKey = null;
				return false;
			}
		}
		return true;
	}

	public void onProcessing(SelectionKey key) throws IOException {
		SocketChannel remote = (SocketChannel) key.channel();
		boolean connected = false;
		if (key == forwardingKey) {
			forwardingKey = null;
		}
		try {
			connected = remote.finishConnect();
			if (connectStatusHandler != null && connected) {
				connectStatusHandler.connected(key);
			}
		} catch (IOException e) {
			logger.warn("Cannot forward due to remote {}: {}", this.getAddress(), e);
			close(remote);
			if (!connected && connectStatusHandler != null) {
				connectStatusHandler.connectError(key);
			}
			return;
		}
		logger.info("Remote connection is ready: " + remote.socket().getRemoteSocketAddress());
		try {
			new ChannelExchange(localChannel, remote, key.selector());
			forwarding = true;
		} catch (IOException e) {
			logger.warn("Cannot forward due to local " + localChannel.socket().getInetAddress(), e);
			close(localChannel);
			close(remote);
		}
	}

	public void close() throws IOException {
		if (localChannel.isOpen()) {
			close(localChannel);
		}
	}

	private static void close(Closeable closeHandler) {
		try {
			if (closeHandler != null) {
				closeHandler.close();
			}
		} catch (IOException e) {
			logger.debug("close error", e);
		}
	}

	public void setAddress(InetSocketAddress address) {
		this.address = address;
	}

	public InetSocketAddress getAddress() {
		return address;
	}

	public boolean isForwarding() {
		return forwarding;
	}

	public SelectionKey getForwardingKey() {
		return forwardingKey;
	}
}
