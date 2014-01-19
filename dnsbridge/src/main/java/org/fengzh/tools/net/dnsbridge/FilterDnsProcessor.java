package org.fengzh.tools.net.dnsbridge;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.fengzh.tools.net.dnsbridge.DnsMessage.Resource;

public class FilterDnsProcessor implements DnsProcessor {

	private Set<InetAddress> filterIps = new HashSet<InetAddress>();;

	private RemoteDnsProcessor remoteProcessor;

	private static byte[] InvalidDnsRequest = { 0x00, 0x02, 0x01, 0x00, 0x00,
			0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x07, 0x74, 0x77, 0x69,
			0x74, 0x74, 0x65, 0x72, 0x03, 0x63, 0x6f, 0x6d, 0x00, 0x00, 0x01,
			0x00, 0x01 };

	@Override
	public void init(Properties prop) {
		remoteProcessor = new RemoteDnsProcessor();
		remoteProcessor.init(prop);
		RemoteDnsProcessor testRemoteProcessor = new RemoteDnsProcessor();
		testRemoteProcessor.init(prop);
		try {
			testRemoteProcessor.setRemoteDnsServer(InetAddress.getByName(prop
					.getProperty("TestDnsServer")));
		} catch (UnknownHostException e) {
			testRemoteProcessor.close();
			throw new IllegalArgumentException(
					"Invalid property 'TestDnsServer'", e);
		}
		testRemoteProcessor.setTimeout(Integer.parseInt(prop
				.getProperty("TestRespTimeout")));
		int testCount = Integer.parseInt(prop.getProperty("TestCount"));
		findInvalidIps(testRemoteProcessor, testCount);
		testRemoteProcessor.close();
		System.err.println("Filter Blocked IP Set(" + filterIps.size() + "): "
				+ filterIps);
	}

	private void findInvalidIps(RemoteDnsProcessor testRemoteProcessor,
			final int testCount) {
		final AtomicInteger index = new AtomicInteger();

		DnsReply reply = new DnsReply() {

			@Override
			public boolean response(DatagramPacket responsePacket)
					throws IOException {
				InetAddress respAddr = null;
				respAddr = findRespAddr(responsePacket.getData(),
						responsePacket.getLength());
				if (respAddr != null) {
					filterIps.add(respAddr);
				}
				return index.get() < testCount;
			}
		};
		try {
			testRemoteProcessor.batchProcess(new Iterator<DatagramPacket>() {

				@Override
				public void remove() {
					throw new UnsupportedOperationException();
				}

				@Override
				public DatagramPacket next() {
					index.incrementAndGet();
					return new DatagramPacket(InvalidDnsRequest,
							InvalidDnsRequest.length);
				}

				@Override
				public boolean hasNext() {
					return index.get() < testCount;
				}
			}, reply);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static InetAddress findRespAddr(byte[] buf, int len) {
		DnsMessage dnsMessage = null;
		try {
			dnsMessage = new DnsMessage(buf, len);
		} catch (Exception e) {
			// TODO
			e.printStackTrace();
			return null;
		}
		for (Resource answer : dnsMessage.getAnList()) {
			if (answer.getType() == DnsMessage.Type.A
					|| answer.getType() == DnsMessage.Type.AAAA) {
				// ipv4/ipv6
				return (InetAddress) answer.getRdata();
			}
		}
		return null;
	}

	@Override
	public void process(DatagramPacket requestPacket, final DnsReply reply)
			throws IOException {
		remoteProcessor.process(requestPacket, new DnsReply() {

			@Override
			public boolean response(DatagramPacket responsePacket)
					throws IOException {
				InetAddress respAddr = findRespAddr(responsePacket.getData(),
						responsePacket.getLength());
				if (filterIps.contains(respAddr)) {
					System.err
							.println("Debug: Filtered IP '" + respAddr + "'.");
					return false;
				}
				return reply.response(responsePacket);
			}
		});
	}

	@Override
	public void close() throws IOException {
		remoteProcessor.close();
	}

}
