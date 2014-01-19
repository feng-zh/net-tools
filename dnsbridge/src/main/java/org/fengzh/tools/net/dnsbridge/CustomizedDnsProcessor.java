package org.fengzh.tools.net.dnsbridge;

import java.io.IOException;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.fengzh.tools.net.dnsbridge.DnsMessage.Question;
import org.fengzh.tools.net.dnsbridge.DnsMessage.Resource;
import org.fengzh.tools.net.dnsbridge.DnsMessage.Type;

public class CustomizedDnsProcessor implements DnsProcessor {

	private DnsProcessor nextProcessor;

	private Map<Pattern, InetAddress> mapping = new HashMap<Pattern, InetAddress>();

	private Map<String, InetAddress> fullMapping = new HashMap<String, InetAddress>();

	@SuppressWarnings("restriction")
	@Override
	public void init(Properties prop) {
		String customizedFile = prop.getProperty("CustomizedFile");
		InputStream resource = getClass().getClassLoader().getResourceAsStream(
				customizedFile);
		if (resource != null) {
			Scanner scanner = new Scanner(resource);
			try {
				while (scanner.hasNextLine()) {
					String line = scanner.nextLine().trim();
					StringTokenizer tokenizer = new StringTokenizer(line);
					InetAddress address = null;
					while (tokenizer.hasMoreTokens()) {
						String element = tokenizer.nextToken();
						if (element.startsWith("#")) {
							break;
						}
						if (address == null) {
							byte[] v6 = sun.net.util.IPAddressUtil
									.textToNumericFormatV6(element);
							if (v6 == null) {
								byte[] v4 = sun.net.util.IPAddressUtil
										.textToNumericFormatV4(element);
								if (v4 == null) {
									throw new IllegalArgumentException(
											"invalid IPv4 address: " + element);
								}
								try {
									address = InetAddress.getByAddress(v4);
								} catch (UnknownHostException e) {
									throw new IllegalArgumentException(
											"invalid address", e);
								}
							} else {
								try {
									address = InetAddress.getByAddress(v6);
								} catch (UnknownHostException e) {
									throw new IllegalArgumentException(
											"invalid address", e);
								}
							}
						} else {
							fullMapping.put(element, address);
							String patternStr = element.replace(".", "\\.");
							patternStr = patternStr.replace("*", ".*");
							try {
								Pattern pattern = Pattern.compile(patternStr);
								mapping.put(pattern, address);
								System.err.println("Install mapping: "
										+ pattern + " to " + address);
							} catch (PatternSyntaxException e) {
								throw new IllegalArgumentException(
										"invalid address pattern: " + element,
										e);
							}
						}
					}
				}
			} finally {
				scanner.close();
			}
		}
		nextProcessor = new FilterDnsProcessor();
		nextProcessor.init(prop);
	}

	@Override
	public void process(DatagramPacket requestPacket, final DnsReply reply)
			throws IOException {
		final DnsMessage dnsMessage = new DnsMessage(requestPacket.getData(),
				requestPacket.getLength());
		for (Question question : dnsMessage.getQdList()) {
			if (question.getType() == DnsMessage.Type.A
					&& question.getQclass() == 1) {
				// query ipv4 - INCLASS
				InetAddress[] targetAddress = getAddresses(question.getQname(),
						DnsMessage.Type.A);
				if (targetAddress != null && targetAddress.length > 0) {
					System.err.println("mapping from " + question.getQname()
							+ " to " + targetAddress[0]);
					reply.response(createResponse(dnsMessage, question,
							targetAddress));
					return;
				}
			} else if (question.getType() == DnsMessage.Type.AAAA
					&& question.getQclass() == 1) {
				// query ipv6 - INCLASS
				InetAddress[] targetAddress = getAddresses(question.getQname(),
						DnsMessage.Type.AAAA);
				if (targetAddress != null && targetAddress.length > 0) {
					System.err.println("mapping from " + question.getQname()
							+ " to " + targetAddress[0]);
					reply.response(createResponse(dnsMessage, question,
							targetAddress));
					return;
				}
			}
		}
		nextProcessor.process(requestPacket, new DnsReply() {

			@Override
			public boolean response(DatagramPacket responsePacket)
					throws IOException {
				DnsMessage dnsMessage = null;
				try {
					dnsMessage = new DnsMessage(responsePacket.getData(),
							responsePacket.getLength());
					for (int i = 0; i < dnsMessage.getAnList().length; i++) {
						Resource answer = dnsMessage.getAnList()[i];
						if (answer.getType() == DnsMessage.Type.A
								|| answer.getType() == DnsMessage.Type.AAAA) {
							// ipv4/ipv6
							String qname = answer.getRname();
							InetAddress[] targetAddress = getAddresses(qname,
									answer.getType());
							if (targetAddress != null
									&& targetAddress.length > 0) {
								System.err
										.println("mapping CNAME address from "
												+ qname + " to "
												+ targetAddress[0]);
								byte[] byteArray = answer.getType()
										.toByteArray(targetAddress[0]);
								answer.setRdBuf(byteArray);
								answer.setBufLength(byteArray.length);
								answer.setRdOffset(0);
								answer.setRdlength(byteArray.length);
								byte[] data = dnsMessage.generate();
								return reply.response(new DatagramPacket(data,
										data.length));
							}
						}
					}
				} catch (Exception e) {
					// TODO
					e.printStackTrace();
				}
				return reply.response(responsePacket);
			}
		});
		return;
	}

	private DatagramPacket createResponse(DnsMessage message,
			Question question, InetAddress[] targetAddress) {
		message.setQr(false);
		message.setRa(true);
		message.setRcode(0);
		Resource[] answers = new Resource[targetAddress.length];
		for (int i = 0; i < targetAddress.length; i++) {
			Resource answer = new Resource();
			answer.setRclass(question.getQclass());
			answer.setRttl(10 * 60);// 10 minutes
			answer.setRname(question.getQname());
			answer.setRtype(question.getQtype());
			byte[] byteArray = question.getType().toByteArray(targetAddress[i]);
			answer.setRdBuf(byteArray);
			answer.setBufLength(byteArray.length);
			answer.setRdOffset(0);
			answer.setRdlength(byteArray.length);
			answers[i] = answer;
		}
		message.setAnList(answers);
		byte[] data = message.generate();
		return new DatagramPacket(data, data.length);
	}

	private InetAddress[] getAddresses(String qname, Type addressType) {
		if (qname.endsWith(".")) {
			qname = qname.substring(0, qname.length() - 1);
		}
		List<InetAddress> addresses = new ArrayList<InetAddress>();
		// check exact match first
		for (Entry<String, InetAddress> entry : fullMapping.entrySet()) {
			if (entry.getKey().equals(qname)
					&& addressType.getTypeClass().isInstance(entry.getValue())) {
				addresses.add(entry.getValue());
			}
		}
		if (addresses.isEmpty()) {
			// if not exact match, try regex match
			for (Entry<Pattern, InetAddress> entry : mapping.entrySet()) {
				if (entry.getKey().matcher(qname).matches()
						&& addressType.getTypeClass().isInstance(entry.getValue())) {
					addresses.add(entry.getValue());
				}
			}
		}
		return addresses.toArray(new InetAddress[addresses.size()]);
	}

	@Override
	public void close() throws IOException {
		nextProcessor.close();
	}

}
