package org.fengzh.tools.net.dnsbridge;

import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

public class DnsMessage {

	private byte[] buf;

	private int bufLen;

	private int id;

	private boolean qr;

	private int opcode;

	private boolean aa;

	private boolean tc;

	private boolean rd;

	private boolean ra;

	private int rcode;

	private Question[] qdList;

	private Resource[] anList;

	private Resource[] nsList;

	private Resource[] arList;

	// question
	public static class Question {

		private String qname;

		private int qtype;

		private int qclass;

		public String getQname() {
			return qname;
		}

		public int getQtype() {
			return qtype;
		}

		public int getQclass() {
			return qclass;
		}

		public Type getType() {
			return Type.valueOf(qtype);
		}

		public String toString() {
			return String.format("Question: name=%s, type=%s, class=%s", qname,
					qtype, qclass);
		}

	}

	// resource
	public static class Resource {
		private String rname;

		private int rtype;

		private int rclass;

		private long rttl;

		private int rdlength;

		private int rdOffset;

		private byte[] rdBuf;

		private int bufLength;

		public String getRname() {
			return rname;
		}

		public int getRtype() {
			return rtype;
		}

		public int getRclass() {
			return rclass;
		}

		public long getRttl() {
			return rttl;
		}

		public int getRdlength() {
			return rdlength;
		}

		public byte[] toRdData() {
			return Arrays.copyOfRange(rdBuf, rdOffset, rdlength);
		}

		public Type getType() {
			if (rclass == 1) {
				return Type.valueOf(rtype);
			} else {
				return null;
			}
		}

		public Object getRdata() {
			Type type = getType();
			if (type != null) {
				return type.parseValue(rdBuf, rdOffset, rdlength, bufLength);
			} else {
				return toRdData();
			}
		}

		public byte[] normalizeRdata() {
			Type type = getType();
			if (type != null) {
				Object value = type.parseValue(rdBuf, rdOffset, rdlength,
						bufLength);
				return type.toByteArray(value);
			} else {
				return toRdData();
			}
		}

		public void setRname(String rname) {
			this.rname = rname;
		}

		public void setRtype(int rtype) {
			this.rtype = rtype;
		}

		public void setRclass(int rclass) {
			this.rclass = rclass;
		}

		public void setRttl(long rttl) {
			this.rttl = rttl;
		}

		public void setRdlength(int rdlength) {
			this.rdlength = rdlength;
		}

		public void setRdOffset(int rdOffset) {
			this.rdOffset = rdOffset;
		}

		public void setRdBuf(byte[] rdBuf) {
			this.rdBuf = rdBuf;
		}

		public void setBufLength(int bufLength) {
			this.bufLength = bufLength;
		}

		@Override
		public String toString() {
			StringBuffer buf = new StringBuffer();
			buf.append(String
					.format("Resource: name=%s, type=%s, class=%s, ttl=%s, length=%s%n",
							rname, rtype, rclass, rttl, rdlength));
			if (getType() != null) {
				buf.append("\t[" + getType() + "] ").append(getRdata());
			} else {
				try {
					toHexValues(buf, rdBuf, rdOffset, rdlength);
				} catch (RuntimeException e) {
					System.err.println("Error in " + buf);
					System.err.println("buf len: " + rdBuf.length
							+ ", rd offset: " + rdOffset + ", rdlength: "
							+ rdlength);
					throw e;
				}
			}
			return buf.toString();
		}
	}

	public static class SOAData {

		private String mname;

		private String rname;

		private long serial;

		private long refresh;

		private long retry;

		private long expire;

		private long minimum;

		public String getMname() {
			return mname;
		}

		public String getRname() {
			return rname;
		}

		public long getSerial() {
			return serial;
		}

		public long getRefresh() {
			return refresh;
		}

		public long getRetry() {
			return retry;
		}

		public long getExpire() {
			return expire;
		}

		public long getMinimum() {
			return minimum;
		}

		public void setMname(String mname) {
			this.mname = mname;
		}

		public void setRname(String rname) {
			this.rname = rname;
		}

		public void setSerial(long serial) {
			this.serial = serial;
		}

		public void setRefresh(long refresh) {
			this.refresh = refresh;
		}

		public void setRetry(long retry) {
			this.retry = retry;
		}

		public void setExpire(long expire) {
			this.expire = expire;
		}

		public void setMinimum(long minimum) {
			this.minimum = minimum;
		}

		@Override
		public String toString() {
			return String
					.format("mname=%s, rname=%s, serial=%s, refresh=%s, retry=%s, expire=%s, minimum=%s",
							mname, rname, serial, refresh, retry, expire,
							minimum);
		}

	}

	public static enum Type {
		A(1, Inet4Address.class) {
			@Override
			public Inet4Address parseValue(byte[] buf, int offset, int length,
					int bufLength) {
				if (length != 4) {
					throw new IllegalArgumentException("invalid A resource");
				}
				byte[] addr = new byte[4];
				System.arraycopy(buf, offset, addr, 0, length);
				try {
					return (Inet4Address) InetAddress.getByAddress(addr);
				} catch (UnknownHostException e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public byte[] toByteArray(Object obj) {
				Inet4Address address = (Inet4Address) obj;
				return address.getAddress();
			}
		},

		AAAA(28, Inet6Address.class) {
			@Override
			public Inet6Address parseValue(byte[] buf, int offset, int length,
					int bufLength) {
				if (length != 16) {
					throw new IllegalArgumentException("invalid AAAA resource");
				}
				byte[] addr = new byte[16];
				System.arraycopy(buf, offset, addr, 0, length);
				try {
					return (Inet6Address) InetAddress.getByAddress(addr);
				} catch (UnknownHostException e) {
					throw new RuntimeException(e);
				}
			}

			@Override
			public byte[] toByteArray(Object obj) {
				Inet6Address address = (Inet6Address) obj;
				return address.getAddress();
			}
		},

		CNAME(5, String.class) {

			@Override
			public String parseValue(byte[] buf, int offset, int length,
					int bufLength) {
				StringBuffer str = new StringBuffer();
				domainValue(buf, offset, bufLength, str);
				return str.toString();
			}

			@Override
			public byte[] toByteArray(Object obj) {
				String domainName = (String) obj;
				return domainValue(domainName);
			}
		},

		SOA(6, SOAData.class) {

			@Override
			public SOAData parseValue(byte[] buf, int offset, int length,
					int bufLength) {
				SOAData data = new SOAData();
				StringBuffer name = new StringBuffer();
				offset += domainValue(buf, offset, bufLength, name);
				data.mname = name.toString();
				name.delete(0, name.length());
				offset += domainValue(buf, offset, bufLength, name);
				data.rname = name.toString();
				data.serial = longValue(buf, offset, offset + length);
				offset += 4;
				data.refresh = longValue(buf, offset, offset + length);
				offset += 4;
				data.retry = longValue(buf, offset, offset + length);
				offset += 4;
				data.expire = longValue(buf, offset, offset + length);
				offset += 4;
				data.minimum = longValue(buf, offset, offset + length);
				offset += 4;
				return data;
			}

			@Override
			public byte[] toByteArray(Object obj) {
				SOAData soaData = (SOAData) obj;
				byte[] domainName = domainValue(soaData.mname);
				byte[] rName = domainValue(soaData.rname);
				byte[] serial = bytesValue(soaData.serial);
				byte[] refresh = bytesValue(soaData.refresh);
				byte[] retry = bytesValue(soaData.retry);
				byte[] expire = bytesValue(soaData.expire);
				byte[] minimum = bytesValue(soaData.minimum);
				byte[] bytes = new byte[domainName.length + rName.length
						+ serial.length * 5];
				int pos = 0;
				System.arraycopy(domainName, 0, bytes, pos, domainName.length);
				pos += domainName.length;
				System.arraycopy(rName, 0, bytes, pos, rName.length);
				pos += rName.length;
				System.arraycopy(serial, 0, bytes, pos, serial.length);
				pos += serial.length;
				System.arraycopy(refresh, 0, bytes, pos, refresh.length);
				pos += refresh.length;
				System.arraycopy(retry, 0, bytes, pos, retry.length);
				pos += retry.length;
				System.arraycopy(expire, 0, bytes, pos, expire.length);
				pos += expire.length;
				System.arraycopy(minimum, 0, bytes, pos, minimum.length);
				pos += minimum.length;
				return bytes;
			}

		};

		private int type;

		private Class<?> clasz;

		private static Type[] types = null;

		private Type(int type, Class<?> clasz) {
			this.type = type;
			this.clasz = clasz;
		}

		public static Type valueOf(int rtype) {
			if (types == null) {
				Type[] aTypes = new Type[256];
				for (Type type : values()) {
					if (aTypes[type.type] != null) {
						throw new IllegalArgumentException(
								"duplicate type code: " + type.type);
					}
					aTypes[type.type] = type;
				}
				types = aTypes;
			}
			return types[rtype];
		}

		public int getTypeValue() {
			return type;
		}

		public Class<?> getTypeClass() {
			return clasz;
		}

		public abstract Object parseValue(byte[] buf, int offset, int length,
				int bufLength);

		public abstract byte[] toByteArray(Object obj);
	}

	public DnsMessage(byte[] buf, int len) {
		this.buf = buf;
		this.bufLen = len;
		parse();
	}

	public static void toHexValues(StringBuffer buf, byte[] rdBuf,
			int rdOffset, int rdlength) {
		int index = 0;
		StringBuffer displayText = new StringBuffer(16);
		while (index < rdlength) {
			int value = 0xFF & rdBuf[index + rdOffset];
			if (index % 16 == 0) {
				String seq = "00000000" + Integer.toHexString(index);
				buf.append(seq.substring(seq.length() - 8));
				buf.append(": ");
				displayText.delete(0, displayText.length());
			}
			buf.append(Integer.toHexString(value | 0x100).substring(1, 3));
			buf.append(' ');
			if (value >= 0x20 && value < 0x7F) {
				displayText.append((char) value);
			} else {
				displayText.append('.');
			}
			if (index % 16 == 15) {
				buf.append("  ");
				buf.append(displayText);
				buf.append("\n");
			}
			index++;
		}
		if (index % 16 != 0) {
			for (int i = 16; i > index % 16; i--) {
				buf.append("   ");
			}
			buf.append("  ");
			buf.append(displayText);
			buf.append("\n");
		}
	}

	public void parse() {
		try {
			if (bufLen < 12) {
				throw new IndexOutOfBoundsException(
						"header size should not less than 12 bytes: " + bufLen);
			}
			id = intValue(buf, 0, bufLen);
			int flag = intValue(buf, 2, bufLen);
			qr = intMask(flag, 16, 1) == 0;
			opcode = intMask(flag, 15, 4);
			aa = intMask(flag, 11, 1) != 0;
			tc = intMask(flag, 10, 1) != 0;
			rd = intMask(flag, 9, 1) != 0;
			ra = intMask(flag, 8, 1) != 0;
			if (intMask(flag, 7, 3) != 0) {
				throw new IllegalArgumentException("reseved value is not 0");
			}
			rcode = intMask(flag, 4, 4);
			int qdcount = intValue(buf, 4, bufLen);
			int ancount = intValue(buf, 6, bufLen);
			int nscount = intValue(buf, 8, bufLen);
			int arcount = intValue(buf, 10, bufLen);
			int byteOffset = 12;
			qdList = new Question[qdcount];
			for (int i = 0; i < qdcount; i++) {
				qdList[i] = new Question();
				byteOffset += questionValue(byteOffset, qdList[i]);
			}
			anList = new Resource[ancount];
			for (int i = 0; i < ancount; i++) {
				anList[i] = new Resource();
				byteOffset += resourceValue(byteOffset, anList[i]);
			}
			nsList = new Resource[nscount];
			for (int i = 0; i < nscount; i++) {
				nsList[i] = new Resource();
				byteOffset += resourceValue(byteOffset, nsList[i]);
			}
			arList = new Resource[arcount];
			for (int i = 0; i < arcount; i++) {
				arList[i] = new Resource();
				byteOffset += resourceValue(byteOffset, arList[i]);
			}
		} catch (RuntimeException e) {
			StringBuffer stringBuffer = new StringBuffer();
			stringBuffer.append("Raw Data:\n");
			toHexValues(stringBuffer, buf, 0, bufLen);
			System.err.println(stringBuffer);
			throw e;
		}
	}

	private int questionValue(int byteOffset, Question question) {
		int offset = 0;
		StringBuffer buf = new StringBuffer();
		offset += domainValue(this.buf, byteOffset, this.bufLen, buf);
		question.qname = buf.toString();
		question.qtype = intValue(this.buf, byteOffset + offset, bufLen);
		offset += 2;
		question.qclass = intValue(this.buf, byteOffset + offset, bufLen);
		offset += 2;
		return offset;
	}

	private void write(DataOutput output, Question question) throws IOException {
		output.write(domainValue(question.qname));
		output.writeShort(question.qtype);
		output.writeShort(question.qclass);
	}

	static byte[] domainValue(String name) {
		if (!name.endsWith(".")) {
			name += ".";
		}
		byte[] bytes = new byte[name.length() * 3];
		int index = 0;
		int len = 0;
		for (char c : name.toCharArray()) {
			if (c == '.') {
				bytes[index - len] = (byte) len;
				len = 0;
				index++;
				continue;
			}
			index++;
			bytes[index] = (byte) c;
			len++;
		}
		if (name.length() > 1) {
			// add zero length
			bytes[index++] = 0;
		}
		return Arrays.copyOf(bytes, index);
	}

	static int domainValue(byte[] buf, int byteOffset, int bufLen,
			StringBuffer stringBuffer) {
		int offset = 0;
		int readOffset = 0;
		boolean done = false;

		while (!done) {
			if (byteOffset >= bufLen) {
				throw new IndexOutOfBoundsException("invalid offset: "
						+ byteOffset + ">=" + bufLen);
			}
			int len = 0xFF & buf[byteOffset++];
			offset++;
			switch (len & 0xC0) {
			case 0:
				// normal
				if (len == 0) {
					// end domain
					done = true;
				} else {
					for (int i = 0; i < len; i++) {
						if (byteOffset >= bufLen) {
							throw new IndexOutOfBoundsException(
									"invalid offset: " + byteOffset + ">="
											+ bufLen);
						}
						stringBuffer.append((char) (0xFF & buf[byteOffset++]));
						offset++;
					}
					stringBuffer.append('.');
				}
				break;
			case 0xC0:
				if (byteOffset >= bufLen) {
					throw new IndexOutOfBoundsException("invalid offset: "
							+ byteOffset + ">=" + bufLen);
				}
				// compressed
				int pos = (0xFF & buf[byteOffset++]) | ((len & ~0xC0) << 8);
				offset++;
				if (pos >= bufLen - 2)
					throw new IllegalArgumentException("invalid compression");
				byteOffset = pos;
				if (readOffset == 0) {
					readOffset = offset;
				}
				break;
			default:
				throw new IllegalArgumentException("invalid label type: 0x"
						+ Integer.toHexString(len) + ", parsed value: '"
						+ stringBuffer + "', offset: 0x"
						+ Integer.toHexString(byteOffset - 1));
			}
		}
		return readOffset == 0 ? offset : readOffset;
	}

	private int resourceValue(int byteOffset, Resource resource) {
		int offset = 0;
		StringBuffer buf = new StringBuffer();
		offset += domainValue(this.buf, byteOffset + offset, this.bufLen, buf);
		resource.rname = buf.toString();
		resource.rtype = intValue(this.buf, byteOffset + offset, bufLen);
		offset += 2;
		resource.rclass = intValue(this.buf, byteOffset + offset, bufLen);
		offset += 2;
		resource.rttl = longValue(this.buf, byteOffset + offset, bufLen);
		offset += 4;
		resource.rdlength = intValue(this.buf, byteOffset + offset, bufLen);
		offset += 2;
		resource.rdOffset = byteOffset + offset;
		offset += resource.rdlength;
		resource.rdBuf = this.buf;
		resource.bufLength = this.bufLen;
		return offset;
	}

	private void write(DataOutputStream output, Resource resource)
			throws IOException {
		output.write(domainValue(resource.rname));
		output.writeShort(resource.rtype);
		output.writeShort(resource.rclass);
		output.writeInt((int) resource.rttl);
		byte[] rdata = resource.normalizeRdata();
		output.writeShort(rdata.length);
		output.write(rdata, 0, rdata.length);
	}

	static long longValue(byte[] buf, int offset, int length) {
		if (offset + 3 < length) {
			return ((0xFF & buf[offset]) << 24)
					| ((0xFF & buf[offset + 1]) << 16)
					| ((0xFF & buf[offset + 2]) << 8)
					| (0xFF & buf[offset + 3]);
		} else {
			throw new IndexOutOfBoundsException("invalid offset: " + offset
					+ " + 3 >= " + length);
		}
	}

	static byte[] bytesValue(long v) {
		byte[] bytes = new byte[4];
		bytes[0] = (byte) ((v >>> 24) & 0xFF);
		bytes[1] = (byte) ((v >>> 16) & 0xFF);
		bytes[2] = (byte) ((v >>> 8) & 0xFF);
		bytes[3] = (byte) ((v >>> 0) & 0xFF);
		return bytes;
	}

	private int intMask(int value, int bitOffset, int bitLength) {
		return (value << (32 - bitOffset)) >>> (32 - bitLength);
	}

	private int maskInt(int bitOffset, int bitLength, int value) {
		return (((~0) >>> (32 - bitLength)) & value) << (bitOffset - bitLength);
	}

	static int intValue(byte[] buf, int offset, int length) {
		if (offset + 1 < length) {
			return ((0xFF & buf[offset]) << 8) | (0xFF & buf[offset + 1]);
		} else {
			throw new IndexOutOfBoundsException("invalid offset: " + offset
					+ " + 1 >= " + length);
		}
	}

	public byte[] getBuf() {
		return buf;
	}

	public int getBufLen() {
		return bufLen;
	}

	public int getId() {
		return id;
	}

	public boolean isQr() {
		return qr;
	}

	public int getOpcode() {
		return opcode;
	}

	public boolean isAa() {
		return aa;
	}

	public boolean isTc() {
		return tc;
	}

	public boolean isRd() {
		return rd;
	}

	public boolean isRa() {
		return ra;
	}

	public int getRcode() {
		return rcode;
	}

	@Override
	public String toString() {
		try {
			StringBuffer stringBuffer = new StringBuffer();
			stringBuffer
					.append(String
							.format("DnsMessage [id=%s, qr=%s, opcode=%s, aa=%s, tc=%s, rd=%s, ra=%s, rcode=%s]",
									id, qr, opcode, aa, tc, rd, ra, rcode));
			for (Question data : qdList) {
				stringBuffer.append("\n ").append(data);
			}
			for (Resource data : anList) {
				stringBuffer.append("\n Answer-").append(data);
			}
			for (Resource data : nsList) {
				stringBuffer.append("\n Authority-").append(data);
			}
			for (Resource data : arList) {
				stringBuffer.append("\n Additional-").append(data);
			}
			stringBuffer.append("\n");
			return stringBuffer.toString();
		} catch (RuntimeException e) {
			StringBuffer stringBuffer = new StringBuffer();
			stringBuffer.append("Raw Data:\n");
			toHexValues(stringBuffer, buf, 0, bufLen);
			System.err.println(stringBuffer);
			throw e;
		}
	}

	public Question[] getQdList() {
		return qdList;
	}

	public void setQdList(Question[] qdList) {
		this.qdList = qdList;
	}

	public Resource[] getAnList() {
		return anList;
	}

	public void setAnList(Resource[] anList) {
		this.anList = anList;
	}

	public Resource[] getNsList() {
		return nsList;
	}

	public void setNsList(Resource[] nsList) {
		this.nsList = nsList;
	}

	public Resource[] getArList() {
		return arList;
	}

	public void setArList(Resource[] arList) {
		this.arList = arList;
	}

	public void setId(int id) {
		this.id = id;
	}

	public void setQr(boolean qr) {
		this.qr = qr;
	}

	public void setOpcode(int opcode) {
		this.opcode = opcode;
	}

	public void setAa(boolean aa) {
		this.aa = aa;
	}

	public void setTc(boolean tc) {
		this.tc = tc;
	}

	public void setRd(boolean rd) {
		this.rd = rd;
	}

	public void setRa(boolean ra) {
		this.ra = ra;
	}

	public void setRcode(int rcode) {
		this.rcode = rcode;
	}

	public static void main(String[] args) {
		String s = "";
		String[] data = s.split(" ");
		byte[] bytes = new byte[data.length];
		int i = 0;
		for (String d : data) {
			bytes[i++] = (byte) Integer.parseInt(d, 16);
		}
		System.out.println(new DnsMessage(bytes, bytes.length));
	}

	public byte[] generate() {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		DataOutputStream output = new DataOutputStream(bytes);
		try {
			output.writeShort(id);
			int flag = 0;
			flag |= maskInt(16, 1, qr ? 0 : 1);
			flag |= maskInt(15, 4, opcode);
			flag |= maskInt(11, 1, aa ? 1 : 0);
			flag |= maskInt(10, 1, tc ? 1 : 0);
			flag |= maskInt(9, 1, rd ? 1 : 0);
			flag |= maskInt(8, 1, ra ? 1 : 0);
			flag |= maskInt(7, 3, 0);
			flag |= maskInt(4, 4, rcode);
			output.writeShort(flag);
			output.writeShort(qdList.length);
			output.writeShort(anList.length);
			output.writeShort(nsList.length);
			output.writeShort(arList.length);
			for (int i = 0; i < qdList.length; i++) {
				write(output, qdList[i]);
			}
			for (int i = 0; i < anList.length; i++) {
				write(output, anList[i]);
			}
			for (int i = 0; i < nsList.length; i++) {
				write(output, nsList[i]);
			}
			for (int i = 0; i < arList.length; i++) {
				write(output, arList[i]);
			}
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
		return bytes.toByteArray();
	}

}
