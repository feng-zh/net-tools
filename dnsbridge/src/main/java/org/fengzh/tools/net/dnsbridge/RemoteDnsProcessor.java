package org.fengzh.tools.net.dnsbridge;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.Properties;

public class RemoteDnsProcessor implements DnsProcessor {

	private int timeout;
	private InetAddress remoteDnsServer;

	@Override
	public void init(Properties prop) {
		this.timeout = Integer.parseInt(prop.getProperty("ResposneTimeout"));
		try {
			this.remoteDnsServer = InetAddress.getByName(prop
					.getProperty("DnsServer"));
		} catch (UnknownHostException e) {
			throw new IllegalArgumentException("invalid 'DnsServer' property",
					e);
		}
	}

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public InetAddress getRemoteDnsServer() {
		return remoteDnsServer;
	}

	public void setRemoteDnsServer(InetAddress remoteDnsServer) {
		this.remoteDnsServer = remoteDnsServer;
	}

	public void batchProcess(Iterator<DatagramPacket> requestPackets,
			DnsReply reply) throws IOException {
		DatagramSocket remoteSocket = new DatagramSocket();
		remoteSocket.setSoTimeout(timeout);
		byte[] buf = new byte[65536];
		try {
			while (requestPackets.hasNext()) {
				DatagramPacket requestPacket = requestPackets.next();
				requestPacket.setAddress(remoteDnsServer);
				requestPacket.setPort(53);
				remoteSocket.send(requestPacket);
				boolean processed;
				do {
					DatagramPacket responsePacket = new DatagramPacket(buf,
							buf.length);
					try {
						remoteSocket.receive(responsePacket);
					} catch (SocketTimeoutException e) {
						System.err
								.println("WARN: Receive DNS Response timeout.  Please ignore this.");
						break;
					} catch (IOException e) {
						System.err.println(e);
						break;
					}
					processed = reply.response(responsePacket);
				} while (!processed);
			}
		} finally {
			remoteSocket.close();
		}
	}

	@Override
	public void process(DatagramPacket requestPacket, DnsReply reply)
			throws IOException {
		DatagramSocket remoteSocket = new DatagramSocket();
		remoteSocket.setSoTimeout(timeout);
		requestPacket.setAddress(remoteDnsServer);
		requestPacket.setPort(53);
		try {
			remoteSocket.send(requestPacket);
			byte[] buf = new byte[65536];
			boolean processed;
			do {
				DatagramPacket responsePacket = new DatagramPacket(buf,
						buf.length);
				try {
					remoteSocket.receive(responsePacket);
				} catch (SocketTimeoutException e) {
					System.err
							.println("WARN: Receive DNS Response timeout.  Please ignore this.");
					break;
				} catch (IOException e) {
					System.err.println(e);
					break;
				}
				processed = reply.response(responsePacket);
			} while (!processed);
		} finally {
			remoteSocket.close();
		}
	}

	@Override
	public void close() {
	}

}
