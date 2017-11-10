package com.woc.chat.interfaces;

import org.jivesoftware.smack.tcp.XMPPTCPConnection;

/**
 * Created by Administrator on 2017/1/30.
 */
public interface OnReceiveNonChatMsg {
    public void onReceive(XMPPTCPConnection connection,String msgType, String data, String from, String to, boolean isOK);
}
