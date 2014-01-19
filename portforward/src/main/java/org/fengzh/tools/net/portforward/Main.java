package org.fengzh.tools.net.portforward;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class.getName());

    /**
     * java [App Name] <remote host> <remote port> [<local port>]
     * 
     * @param args
     */
    public static void main(String[] args) {
        if (args.length <= 1) {
            verbose();
            return;
        }
        String[] remoteHosts = args[0].split("/");
        int[] remotePorts;
        try {
            int i = 0;
            String[] strings = args[1].split("/");
            remotePorts = new int[strings.length];
            for (String port : strings) {
                remotePorts[i++] = Integer.parseInt(port);
            }
        } catch (NumberFormatException e) {
            System.err.println("Error: Require Remote Port Number (1 ~ 65535)");
            logger.error("Error Remote Port Number.", e);
            verbose();
            return;
        }
        int[] localPorts = remotePorts;
        final PortForwardServer[] servers = new PortForwardServer[remotePorts.length];
        try {
            for (int i = 0; i < remotePorts.length; i++) {
                servers[i] = new PortForwardServer(remoteHosts, remotePorts[i], localPorts[i]);
                servers[i].start();
            }
        } catch (IOException e) {
            System.err.println("Error: Fail to start Forward Server: " + e.getMessage());
            logger.error("Cannot start forward server due to exception", e);
        } finally {
            Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {

                public void run() {
                    for (PortForwardServer server : servers) {
                        if (server == null)
                            continue;
                        try {
                            server.close();
                        } catch (IOException e) {
                            logger.warn("Stop server error", e);
                        }
                    }
                }
            }));
        }
    }

    public static void verbose() {
        System.err
                .println("java [App Name] <remote host>[/remote host2/...] <remote port>[/remote port2/...]");
    }

}
