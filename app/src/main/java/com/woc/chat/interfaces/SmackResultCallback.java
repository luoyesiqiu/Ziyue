package com.woc.chat.interfaces;

import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;

/**
 * Created by zyw on 2017/1/29.
 */
public interface SmackResultCallback  {
    public void onReceive(XMPPTCPConnection connection, String msg, boolean isOK);
}
