package org.fengzh.tools.net.core;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ChannelBuffer implements ChannelHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChannelBuffer.class);

    private ByteBuffer outgoing = null;

    private ByteBuffer incoming = ByteBuffer.allocate(8 * 1024);

    private boolean closed = false;

    private SelectionKey readKey;

    private SelectionKey writeKey;

    public ByteBuffer getOutgoing() {
        return outgoing;
    }

    public ByteBuffer getIncoming() {
        return incoming;
    }

    public void setClose() {
        closed = true;
    }

    public void closeAll() {
        readKey.cancel();
        close(readKey.channel());
        writeKey.cancel();
        close(writeKey.channel());
        logger.debug("Closing forward connections...");
    }

    public boolean canCloseNow() {
        return incoming.position() == 0 && closed;
    }

    public static ChannelBuffer create(SelectionKey key) {
        ChannelBuffer bufferChannel = new ChannelBuffer();
        key.attach(bufferChannel);
        bufferChannel.readKey = key;
        return bufferChannel;
    }

    public void connect(ChannelBuffer other) {
        // exchange read/write buffers
        this.outgoing = other.incoming;
        other.outgoing = this.incoming;
        this.writeKey = other.readKey;
        other.writeKey = this.readKey;
    }

    public void onProcessing(SelectionKey key) throws IOException {
        // handle write first (write all)
        if (key.isWritable()) {
            handleWrite(key);
        } else if (key.isReadable()) {
            handleRead(key);
        }
    }

    private void handleRead(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        ChannelBuffer buffer = (ChannelBuffer) key.attachment();
        logger.trace("READ events from {}", channel.socket().getRemoteSocketAddress());
        int count;
        try {
            count = channel.read(buffer.getIncoming());
            logger.trace("... read {}, buffer: {}" + count, buffer.getIncoming());
            if (count < 0) {
                // indicate
                buffer.setClose();
            }
            if (buffer.getIncoming().position() > 0) {
                logger.trace("enable write ...");
                buffer.enableWriteOp(key);
            }
            if (buffer.canCloseNow()) {
                buffer.closeAll();
            }
        } catch (IOException e) {
            logger.debug("read error", e);
            buffer.closeAll();
        }
    }

    private void handleWrite(SelectionKey key) {
        SocketChannel channel = (SocketChannel) key.channel();
        ChannelBuffer buffer = (ChannelBuffer) key.attachment();
        logger.trace("WRITE events from {}", channel.socket().getRemoteSocketAddress());
        ByteBuffer b = buffer.getOutgoing();
        b.flip();
        if (b.hasRemaining()) {
            try {
                int count = channel.write(b);
                b.compact();
                logger.trace("... write {}, write: {}", count, b);
            } catch (IOException e) {
                logger.debug("write error", e);
                buffer.closeAll();
            }
        } else {
            // enable reading
            b.clear();
            // cancel write operation
            logger.trace("disable write ...");
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        }
        if (buffer.canCloseNow()) {
            buffer.closeAll();
        }
    }

    private void enableWriteOp(SelectionKey key) {
        writeKey.interestOps(writeKey.interestOps() | SelectionKey.OP_WRITE);
    }

    private static void close(Closeable closeHandler) {
        try {
            if (closeHandler != null) {
                closeHandler.close();
            }
        } catch (IOException e) {
            logger.debug("close error", e);
        }
    }
}