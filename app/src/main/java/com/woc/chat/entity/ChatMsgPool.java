package com.woc.chat.entity;

import cn.bmob.v3.BmobObject;

/**
 * Created by zyw on 2016/9/15.
 */
public class ChatMsgPool extends BmobObject {
    public String getMsgFrom() {
        return msgFrom;
    }

    public void setMsgFrom(String msgFrom) {
        this.msgFrom = msgFrom;
    }

    public String getMsgTo() {
        return msgTo;
    }

    public void setMsgTo(String msgTo) {
        this.msgTo = msgTo;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    private  String msgFrom;
    private  String msgTo;
    private String msg;
}
