package org.fengzh.tools.net.core;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

public class ChannelExchange implements Closeable {

    private ChannelBuffer localBuf;
    private ChannelBuffer remoteBuf;

    public ChannelExchange(SocketChannel local, SocketChannel remote, Selector selector) throws IOException {
        boolean localBlocking = local.isBlocking();
        boolean remoteBlocking = remote.isBlocking();
        SelectionKey localKey = null;
        SelectionKey remoteKey = null;
        boolean success = false;
        try {
            local.configureBlocking(false);
            localKey = local.register(selector, SelectionKey.OP_READ);
            localBuf = ChannelBuffer.create(localKey);
            remote.configureBlocking(false);
            remoteKey = remote.register(selector, SelectionKey.OP_READ);
            remoteBuf = ChannelBuffer.create(remoteKey);
            localBuf.connect(remoteBuf);
            success = true;
        } finally {
            if (!success) {
                if (localKey != null) {
                    localKey.cancel();
                }
                if (remoteKey != null) {
                    remoteKey.cancel();
                }
                if (local.isOpen()) {
                    local.configureBlocking(localBlocking);
                }
                if (remote.isOpen()) {
                    remote.configureBlocking(remoteBlocking);
                }
            }
        }
    }

    public void close() {
        localBuf.closeAll();
    }

}
