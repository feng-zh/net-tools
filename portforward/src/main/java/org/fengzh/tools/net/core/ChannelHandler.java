package org.fengzh.tools.net.core;

import java.io.IOException;
import java.nio.channels.SelectionKey;

public interface ChannelHandler {

    public void onProcessing(SelectionKey key) throws IOException;

}
