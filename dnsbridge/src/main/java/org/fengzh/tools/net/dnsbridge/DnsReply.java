package org.fengzh.tools.net.dnsbridge;

import java.io.IOException;
import java.net.DatagramPacket;

public interface DnsReply {

	public boolean response(DatagramPacket responsePacket) throws IOException;

}
