package org.fengzh.tools.net.revsever.remote;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.fengzh.tools.net.core.ChannelForward;
import org.fengzh.tools.net.core.ChannelForward.ConnectStatusHandler;
import org.fengzh.tools.net.core.ChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoteMain {

	private static final Logger logger = LoggerFactory
			.getLogger(RemoteMain.class);

	/**
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) {
		String controlHost = args[0];
		int controlPort = Integer.parseInt(args[1]);
		final List<String> proxyHosts = new ArrayList<String>();
		final List<Integer> proxyPorts = new ArrayList<Integer>();
		for (int i = 2; i < args.length - 1; i += 2) {
			String proxyHost = args[i];
			int proxyPort = Integer.parseInt(args[i + 1]);
			proxyHosts.add(proxyHost);
			proxyPorts.add(proxyPort);
		}
		RemoteAgent remoteAgent = new RemoteAgent(new RemoteAgentHandler() {

			public void onConnected(SocketChannel remoteChannel,
					Selector selector, int id) throws IOException {
				if (id < 0 || id >= proxyHosts.size()) {
					id = 0;
				}
				final String proxyHost = proxyHosts.get(id);
				final int proxyPort = proxyPorts.get(id);
				ChannelForward forward = new ChannelForward(remoteChannel);
				forward.setAddress(new InetSocketAddress(proxyHost, proxyPort));
				forward.setConnectStatusHandler(new ConnectStatusHandler() {

					public void connected(SelectionKey key) {
						logger.debug("Connected to proxy {}:{}", proxyHost,
								proxyPort);
					}

					public void connectError(SelectionKey key) {
						logger.warn("Connect to proxy error {}:{}", proxyHost,
								proxyPort);
					}
				});
				forward.startForward(selector);
			}
		});
		Selector selector;
		try {
			selector = Selector.open();
		} catch (IOException e) {
			logger.error("Cannot open selector");
			return;
		}
		remoteAgent.setControlAddress(new InetSocketAddress(controlHost,
				controlPort));
		logger.info("Starting remote agent");
		try {
			remoteAgent.start(selector);
		} catch (IOException e) {
			logger.error("Start remote agent failure", e);
		}
		while (selector.isOpen()) {
			logger.trace("Selecting events.....");
			int count;
			try {
				count = selector
						.select(remoteAgent.getIdleCount() < RemoteAgent.MAX_IDLE ? 5 * 1000L
								: 1 * 60 * 1000L);
				if (count == 0) {
					remoteAgent.prepareIdleConnections(selector);
					continue;
				}
			} catch (IOException e) {
				logger.warn("Selector processing get error", e);
				continue;
			}
			logger.trace("Got selecting events count {}", count);
			for (Iterator<SelectionKey> keys = selector.selectedKeys()
					.iterator(); keys.hasNext();) {
				SelectionKey key = keys.next();
				keys.remove();
				if (!key.isValid())
					continue;
				Object attachement = key.attachment();
				if (attachement instanceof ChannelHandler) {
					ChannelHandler handler = (ChannelHandler) attachement;
					logger.trace("Processing key ops '{}' with handler [{}]",
							key.interestOps(), handler);
					try {
						handler.onProcessing(key);
					} catch (IOException e) {
						logger.error("Onprocess falure", e);
					}
				} else {
					logger.error(
							"Cannot process key ops '{}' due to attachment is [{}]",
							key.interestOps(), attachement);
				}
			}
		}
	}
}
