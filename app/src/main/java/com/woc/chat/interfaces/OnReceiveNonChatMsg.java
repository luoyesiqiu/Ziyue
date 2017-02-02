package com.woc.chat.interfaces;

/**
 * Created by Administrator on 2017/1/30.
 */
public interface OnReceiveNonChatMsg {
    public void onReceive(String msgType,String data,boolean isOK);
}
