package org.fengzh.tools.net.revsever;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;

import org.fengzh.tools.net.core.ChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ServerMain {

    private static final Logger logger = LoggerFactory.getLogger(ServerMain.class);

    /**
     * @param args
     * @throws IOException
     */
    public static void main(String[] args) {
        int controlPort = Integer.parseInt(args[0]);
        ControlServer controlServer = new ControlServer();
        Selector selector;
        try {
            selector = Selector.open();
        } catch (IOException e) {
            logger.error("Cannot open selector");
            return;
        }
        controlServer.setBindAddress("0.0.0.0", controlPort);
        for (int i = 1; i < args.length - 1; i += 2) {
            String virtualServerHost = args[i];
            int virtualServerPort = Integer.parseInt(args[i + 1]);
            controlServer.addVirtualBindAddress(virtualServerHost, virtualServerPort);
        }
        logger.info("Starting control server");
        try {
            controlServer.start(selector);
        } catch (IOException e) {
            logger.error("Start control server failure", e);
        }
        while (selector.isOpen()) {
            logger.debug("Selecting events.....");
            int count;
            try {
                count = selector.select();
            } catch (IOException e) {
                logger.warn("Selector processing get error", e);
                continue;
            }
            logger.trace("Got selecting events count {}", count);
            for (Iterator<SelectionKey> keys = selector.selectedKeys().iterator(); keys.hasNext();) {
                SelectionKey key = keys.next();
                keys.remove();
                if (!key.isValid())
                    continue;
                Object attachement = key.attachment();
                if (attachement instanceof ChannelHandler) {
                    ChannelHandler handler = (ChannelHandler) attachement;
                    logger.trace("Processing key interest ops '{}' with handler {}", key.interestOps(),
                            handler);
                    try {
                        handler.onProcessing(key);
                    } catch (IOException e) {
                        logger.error("Onprocess falure", e);
                    }
                } else {
                    logger.error("Cannot process key {} due to attachment is {}", key, attachement);
                }
            }
        }
    }
}
