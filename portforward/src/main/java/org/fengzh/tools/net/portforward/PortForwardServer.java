package org.fengzh.tools.net.portforward;

import java.io.Closeable;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;

import javax.management.ObjectInstance;
import javax.management.ObjectName;

public class PortForwardServer implements Closeable {

	private InetSocketAddress[] remoteAddresses;
	private InetSocketAddress localAddress;
	private volatile Thread serverThread;
	private ServerSocketChannel serverChannel;
	private Selector selector;
	private ObjectInstance objectInstance;

	public PortForwardServer(String[] remoteHosts, int remotePort, int localPort)
			throws IllegalArgumentException, UnknownHostException {
		if (remotePort == 0 || localPort == 0)
			throw new IllegalArgumentException(
					"Remote/Local Port value out of range.");
		remoteAddresses = new InetSocketAddress[remoteHosts.length];
		int i = 0;
		for (String remoteHost : remoteHosts) {
			remoteAddresses[i++] = new InetSocketAddress(remoteHost, remotePort);
		}
		localAddress = new InetSocketAddress("127.0.0.1", localPort);
	}

	public synchronized void start() throws IOException {
		if (serverThread == null) {
			serverChannel = ServerSocketChannel.open();
			serverChannel.socket().bind(localAddress);
			serverChannel.configureBlocking(false);
			selector = Selector.open();
			serverChannel.register(selector, SelectionKey.OP_ACCEPT);
			ServerWorker serverWorker = new ServerWorker(selector,
					remoteAddresses);
			try {
				objectInstance = ManagementFactory.getPlatformMBeanServer()
						.registerMBean(
								serverWorker,
								ObjectName.getInstance(getClass().getPackage()
										.getName()
										+ ":type=ServerWorker,name="
										+ localAddress.getPort()));
			} catch (Exception e) {
				e.printStackTrace();
			}
			serverThread = new Thread(serverWorker);
			serverThread.start();
		} else {
			throw new IllegalStateException("Server was starting");
		}
	}

	private void stopping() {
		close(selector);
		selector = null;
		close(serverChannel);
		serverChannel = null;
		if (objectInstance != null) {
			try {
				ManagementFactory.getPlatformMBeanServer().unregisterMBean(
						objectInstance.getObjectName());
			} catch (Exception e) {
				e.printStackTrace();
			}
			objectInstance = null;
		}
	}

	private void close(Closeable closeHandler) {
		try {
			if (closeHandler != null) {
				closeHandler.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void close(Selector closeHandler) {
		try {
			if (closeHandler != null) {
				closeHandler.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public synchronized void close() throws IOException {
		if (serverThread != null) {
			serverThread.interrupt();
			serverThread = null;
			stopping();
		}
	}

}
