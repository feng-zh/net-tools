package org.fengzh.tools.net.dnsbridge;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import org.fengzh.tools.net.dnsbridge.DnsMessage.Resource;
import org.fengzh.tools.net.dnsbridge.DnsMessage.SOAData;

public class NotFoundDnsProcessor implements DnsProcessor {

	private DnsProcessor nextProcessor;
	private Set<InetAddress> filterIps = new HashSet<InetAddress>();
	private static byte[] InvalidDnsRequest = { 0x00, 0x12, 0x01, 0x00, 0x00,
			0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x07, 0x74, 0x74, 0x74,
			0x74, 0x74, 0x74, 0x74, 0x03, 0x63, 0x63, 0x63, 0x00, 0x00, 0x01,
			0x00, 0x01 };

	@Override
	public void init(Properties prop) {
		nextProcessor = new CustomizedDnsProcessor();
		nextProcessor.init(prop);
		findInvalidIps();
		System.err.println("Filter Not Found IP Set(" + filterIps.size()
				+ "): " + filterIps);
	}

	private void findInvalidIps() {
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
				return true;
			}
		};
		try {
			nextProcessor.process(new DatagramPacket(InvalidDnsRequest,
					InvalidDnsRequest.length), reply);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void process(DatagramPacket requestPacket, final DnsReply reply)
			throws IOException {
		nextProcessor.process(requestPacket, new DnsReply() {

			@Override
			public boolean response(DatagramPacket responsePacket)
					throws IOException {
				InetAddress respAddr = findRespAddr(responsePacket.getData(),
						responsePacket.getLength());
				if (filterIps.contains(respAddr)) {
					System.err.println("Debug: Filtered Not Found IP '"
							+ respAddr + "'.");
					return reply.response(createResponse(new DnsMessage(
							responsePacket.getData(), responsePacket
									.getLength())));
				}
				return reply.response(responsePacket);
			}
		});
		return;
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

	private DatagramPacket createResponse(DnsMessage message) {
		message.setQr(false);
		message.setRa(true);
		message.setRcode(3);
		message.setAnList(new Resource[0]);
		Resource answer = new Resource();
		answer.setRclass(1);
		answer.setRttl(900);
		answer.setRname(".");
		answer.setRtype(DnsMessage.Type.SOA.getTypeValue());
		SOAData soaData = new DnsMessage.SOAData();
		soaData.setExpire(604800);
		soaData.setMinimum(86400);
		soaData.setMname("a.root-servers.net.");
		soaData.setRefresh(1800);
		soaData.setRetry(900);
		soaData.setRname("nstld.verisign-grs.com.");
		soaData.setSerial(2013091501);
		byte[] byteArray = DnsMessage.Type.SOA.toByteArray(soaData);
		answer.setRdBuf(byteArray);
		answer.setBufLength(byteArray.length);
		answer.setRdOffset(0);
		answer.setRdlength(byteArray.length);
		message.setArList(new Resource[] { answer });
		byte[] data = message.generate();
		DatagramPacket packet = new DatagramPacket(data, data.length);
		return packet;
	}

	@Override
	public void close() throws IOException {
		nextProcessor.close();
	}

}
