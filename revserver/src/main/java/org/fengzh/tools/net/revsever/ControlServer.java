package org.fengzh.tools.net.revsever;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import org.fengzh.tools.net.core.ChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ControlServer implements ChannelHandler {

    private static final Logger logger = LoggerFactory.getLogger(ControlServer.class);

    private InetSocketAddress controlAddress;
    private ServerSocketChannel serverChannel;
    private VirtualServer virtualServer;
    private Queue<SocketChannel> remoteChannels = new LinkedBlockingQueue<SocketChannel>(16);

    public void setBindAddress(String localHost, int port) {
        this.controlAddress = new InetSocketAddress(localHost, port);
        this.virtualServer = new VirtualServer(this);
    }

    public void addVirtualBindAddress(String localHost, int port) {
        this.virtualServer.addLocalAddress(localHost, port);
    }

    public void start(Selector selector) throws IOException {
        if (serverChannel != null && virtualServer != null)
            return;
        try {
            serverChannel = ServerSocketChannel.open();
            serverChannel.socket().bind(controlAddress);
            logger.info("Control server binds to {} successful", controlAddress);
            serverChannel.configureBlocking(false);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT, this);
        } catch (IOException e) {
            stop();
            throw e;
        }
    }

    public void onProcessing(SelectionKey key) throws IOException {
        if (!key.isAcceptable()) {
            logger.warn("Ignore non-acceptable key from channel {}", key.channel());
            return;
        }
        ServerSocketChannel channel = (ServerSocketChannel) key.channel();
        SocketChannel remote = channel.accept();
        logger.info("Receive incoming remote connection {}", remote.socket());
        if (!remoteChannels.offer(remote)) {
            validRemoteConnections();
            if (!remoteChannels.offer(remote)) {
                remote.close();
            }
        }
        logger.trace("==> (Ready) Offer remote channel: {}", remote.socket());
        if (!virtualServer.isRunning()) {
            logger.info("Starting virtal server");
            virtualServer.start(key.selector());
        } else {
            remote = remoteChannels.poll();
            logger.trace("==> (Link) Poll remote channel: {}", remote.socket());
            if (!virtualServer.link(remote, key)) {
                logger.trace("==> (Link) Offer remote channel: {}", channel.socket());
                remoteChannels.offer(remote);
            }
        }
    }

    private void validRemoteConnections() {
        for (int i = remoteChannels.size() - 1; i >= 0; i--) {
            SocketChannel channel = remoteChannels.poll();
            logger.trace("==> (Checking) Poll remote channel: {}", channel.socket());
            if (channel.isOpen()) {
                if (channel.isConnected()) {
                    logger.trace("==> (Checking) Offer remote channel: {}", channel.socket());
                    remoteChannels.offer(channel);
                } else {
                    try {
                        channel.close();
                    } catch (IOException e) {
                        logger.debug("Close closed connection");
                    }
                }
            }
        }
    }

    public void stop() {

    }

    public synchronized SocketChannel receiveRemoteChannel() {
        logger.trace("==> remote channels: {}", remoteChannels.size());
        logger.trace("==> (Using) Poll remote channel: {}", remoteChannels.peek());
        return remoteChannels.poll();
    }
}
