package org.fengzh.tools.net.revsever.remote;

import java.io.IOException;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public interface RemoteAgentHandler {
    public void onConnected(SocketChannel remoteChannel, Selector selector, int id) throws IOException;
}
