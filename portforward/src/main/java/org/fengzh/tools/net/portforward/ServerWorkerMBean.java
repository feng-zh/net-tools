package org.fengzh.tools.net.portforward;

public interface ServerWorkerMBean {
	public void setCurrentRemoteAddressPos(int currentAddrPos);

	public int getCurrentRemoteAddressPos();

	
	public String getRemoteAddress();
}
