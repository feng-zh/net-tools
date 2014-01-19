package org.fengzh.tools.net.dnsbridge;

import java.io.Closeable;
import java.io.IOException;
import java.net.DatagramPacket;
import java.util.Properties;

public interface DnsProcessor extends Closeable {

	public void init(Properties prop);

	public void process(DatagramPacket requestPacket, DnsReply reply)
			throws IOException;

	public void close() throws IOException;

}
