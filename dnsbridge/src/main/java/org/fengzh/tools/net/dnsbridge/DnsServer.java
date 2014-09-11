package org.fengzh.tools.net.dnsbridge;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DnsServer implements Runnable {

	private static ExecutorService executor;

	private static DnsProcessor processor;

	private DatagramPacket requestPacket;

	private DatagramSocket server;

	public DnsServer(DatagramSocket server, DatagramPacket requestPacket) {
		this.server = server;
		this.requestPacket = requestPacket;
	}

	public static void main(String[] args) {
		Properties prop = new Properties();
		try {
			prop.load(DnsServer.class
					.getResourceAsStream("/dnsfilter.properties"));
		} catch (Exception e) {
			System.err.println("Can't find file 'dnsfilter.properties' due to "
					+ e);
			return;
		}
		processor = new NotFoundDnsProcessor();
		processor.init(prop);
		executor = Executors.newCachedThreadPool();
		DatagramSocket server;
		int dnsPort = Integer.getInteger("dnsPort", 53);
		try {
			server = new DatagramSocket(dnsPort, InetAddress.getByName(prop
					.getProperty("BindToIP")));
		} catch (SocketException e) {
			e.printStackTrace();
			System.err.println("Can't bind UDP port " + dnsPort + " to '"
					+ prop.getProperty("BindToIP") + "'.");
			return;
		} catch (UnknownHostException e) {
			e.printStackTrace();
			System.err.println("Can't find host '"
					+ prop.getProperty("BindToIP") + "'.");
			return;
		}
		System.err
				.println("Start finished.  You can set the dns server to 127.0.0.1 now.");
		while (true) {
			byte[] buf = new byte[65535];
			DatagramPacket dp = new DatagramPacket(buf, buf.length);
			try {
				server.receive(dp);
				executor.submit(new DnsServer(server, dp));
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public void run() {
		try {
			final SocketAddress requestAddress = requestPacket
					.getSocketAddress();
			logPacket(true, requestPacket);
			processor.process(requestPacket, new DnsReply() {

				@Override
				public boolean response(DatagramPacket responsePacket)
						throws IOException {
					responsePacket.setSocketAddress(requestAddress);
					logPacket(false, responsePacket);
					server.send(responsePacket);
					return true;
				}
			});
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void logPacket(boolean send, DatagramPacket dp) {
		if (Boolean.getBoolean("debug")) {
			StringBuffer buf = new StringBuffer();
			try {
				DnsMessage message = new DnsMessage(dp.getData(),
						dp.getLength());
				boolean wasNewLine = true;
				for (char c : message.toString().toCharArray()) {
					if (wasNewLine) {
						buf.append(send ? ">> " : "<< ");
					}
					buf.append(c);
					wasNewLine = (c == '\n');
				}
				System.err.println(buf.toString());
			} catch (RuntimeException e) {
				e.printStackTrace();
			}
		}
	}
}
