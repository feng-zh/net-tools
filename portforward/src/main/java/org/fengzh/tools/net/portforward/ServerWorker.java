package org.fengzh.tools.net.portforward;

import java.io.Closeable;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.fengzh.tools.net.core.ChannelForward;
import org.fengzh.tools.net.core.ChannelForward.ConnectStatusHandler;
import org.fengzh.tools.net.core.ChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ServerWorker implements Runnable, ServerWorkerMBean {

	private static final Logger logger = LoggerFactory
			.getLogger(ServerWorker.class.getName());

	private Selector selector;
	private InetSocketAddress[] remoteAddresses;
	private boolean[] badAddresses;
	private int currentAddressPos;
	private Set<ChannelForward> connectingPool = new HashSet<ChannelForward>();

	public ServerWorker(Selector selector, InetSocketAddress[] remoteAddresses) {
		this.selector = selector;
		this.remoteAddresses = remoteAddresses;
		this.badAddresses = new boolean[remoteAddresses.length];
		this.currentAddressPos = 0;
	}

	public void run() {
		while (selector.isOpen()) {
			int count;
			try {
				logger.trace("Selecting events.....");
				// if new connecting in progress, check them
				count = selector.select(connectingPool.isEmpty() ? 0 : 1000);
			} catch (IOException e) {
				logger.error("Cannot select new events", e);
				logger.info("Stopping server due to select error.");
				break;
			}
			if (Thread.interrupted()) {
				logger.info("Normally stop servers...");
				for (Iterator<SelectionKey> keys = selector.keys().iterator(); keys
						.hasNext();) {
					close(keys.next().channel());
				}
				logger.info("All channels are closed.");
				break;
			}
			if (count != 0) {
				for (Iterator<SelectionKey> keys = selector.selectedKeys()
						.iterator(); keys.hasNext();) {
					SelectionKey key = keys.next();
					keys.remove();
					if (!key.isValid())
						continue;
					if (key.isAcceptable()) {
						// accept new local connections
						handleAccept(key);
					} else if (key.attachment() instanceof ChannelHandler) {
						try {
							((ChannelHandler) key.attachment())
									.onProcessing(key);
						} catch (IOException e) {
							logger.warn("processing got error", e);
						}
					} else {
						logger.error(
								"unknown key processing with attachement: {}",
								key.attachment());
					}
				}
			}
			// check connection timeout
			if (!connectingPool.isEmpty()) {
				for (ChannelForward forward : new HashSet<ChannelForward>(
						connectingPool)) {
					if (!forward.checkValidConnecting()) {
						connectingPool.remove(forward);
					}
				}
			}
		}
	}

	private void handleAccept(SelectionKey key) {
		ServerSocketChannel server = (ServerSocketChannel) key.channel();
		SocketChannel local;
		try {
			local = server.accept();
			logger.info("New local incoming connection: {}", local.socket()
					.getRemoteSocketAddress());
		} catch (IOException e) {
			logger.error("Cannot accept new connection", e);
			return;
		}
		linkToRemote(local);
	}
	
	private InetSocketAddress getCurrentRemoteAddress() {
		int check = remoteAddresses.length;
		while (badAddresses[currentAddressPos] && check > 0) {
			currentAddressPos = (currentAddressPos + 1) % remoteAddresses.length;
			check--;
		}
		if (check == 0) {
			return null;
		} else {
			return remoteAddresses[currentAddressPos];
		}
	}

	private void linkToRemote(final SocketChannel local) {
		final ChannelForward forward = new ChannelForward(local);
		try {
			InetSocketAddress current = getCurrentRemoteAddress();
			if (current == null) {
				restBadAddress();
				current = getCurrentRemoteAddress();
			}
			forward.setAddress(current);
			forward.setConnectStatusHandler(new ConnectStatusHandler() {

				public void connected(SelectionKey key) {
				}

				public void connectError(SelectionKey key) {
					badAddresses[currentAddressPos] = true;
					InetSocketAddress available = getCurrentRemoteAddress();
					if (available!=null) {
						logger.info("Change address to next address: "
								+ available.getHostName() + ":"
								+ available.getPort());
						linkToRemote(local);
					} else {
						logger.error(
								"No suitable connections, closing local connection {}",
								local.socket().getInetAddress());
						try {
							forward.close();
						} catch (IOException e) {
							logger.debug("close error", e);
						}
					}
				}
			});
			forward.startForward(selector);
			connectingPool.add(forward);
		} catch (IOException e) {
			logger.info("Cannot forward connection", e);
			try {
				forward.close();
			} catch (IOException ioe) {
				logger.debug("close error", ioe);
			}
		}
	}

	private void restBadAddress() {
		Arrays.fill(badAddresses, false);
		logger.warn("reset bad address to inital values");
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

	public void setCurrentRemoteAddressPos(int currentAddrPos) {
		currentAddressPos = currentAddrPos % remoteAddresses.length;
	}

	public int getCurrentRemoteAddressPos() {
		return this.currentAddressPos;
	}

	public String getRemoteAddress() {
		return remoteAddresses[currentAddressPos % remoteAddresses.length]
				.toString();
	}
}
