package org.fengzh.tools.net.portforward;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class.getName());

    /**
     * java [App Name] <remote host> <remote port> [<local port>]
     * or
     * java [App Name] @host.txt <remote port> [<local port>]
     * 
     * @param args
     */
    public static void main(String[] args) {
        if (args.length <= 1) {
            verbose();
            return;
        }
        String[] remoteHosts = loadRemoteHosts(args[0]);
        if (remoteHosts.length == 0) {
        	 System.err.println("Error: No available remote hosts.");
             return;
        }
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

    private static String[] loadRemoteHosts(String argument) {
    	if (argument.startsWith("@")) {
    		argument=argument.substring(1);
    		Set<String> hosts = new LinkedHashSet<String>();
    		BufferedReader reader = null;
			try {
				reader = new BufferedReader(new FileReader(argument));
				String line;
				while ((line = reader.readLine()) != null) {
					line = line.trim();
					if (!line.startsWith("#") && line.length() > 0) {
						hosts.add(line);
					}
				}
			} catch (IOException e) {
				System.err.println("Error: Fail to load remote host file: " + argument + ", caused by: "
						+ e.getMessage());
			} finally {
				if (reader != null) {
					try {
						reader.close();
					} catch (IOException ignored) {
					}
				}
			}
			return hosts.toArray(new String[0]);
    	} else {
    		return argument.split("/");
    	}
	}

	public static void verbose() {
        System.err
                .println("java [App Name] <remote host>[/remote host2/...] <remote port>[/remote port2/...]");
    }

}
