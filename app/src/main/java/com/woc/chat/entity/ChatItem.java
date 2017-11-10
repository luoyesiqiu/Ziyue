package com.woc.chat.entity;

/**
 * Created by zyw on 2016/9/13.
 */
public class ChatItem {

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }


    public int getItemType() {
        return itemType;
    }

    public void setItemType(int itemType) {
        this.itemType = itemType;
    }


    /**
     *
     * @param msg
     * @param itemType
     * @param slow
     */
    public ChatItem(String msg, int itemType,boolean slow,boolean isHtml) {
        this.msg = msg;
        this.itemType = itemType;
        this.slow=slow;
        this.isHtml=isHtml;
    }

    private  String msg;
    private  int itemType;

    public boolean isSlow() {
        return slow;
    }

    public void setSlow(boolean slow) {
        this.slow = slow;
    }

    public boolean isHtml() {
        return isHtml;
    }

    public void setHtml(boolean html) {
        isHtml = html;
    }

    private   boolean isHtml;
    private  boolean slow;//是否慢点打字
}
