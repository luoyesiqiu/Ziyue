package com.woc.chat.entity;

import android.content.Context;

import org.json.JSONObject;

import cn.bmob.v3.BmobObject;
import cn.bmob.v3.BmobUser;

/**
 * Created by zyw on 2016/9/15.
 */
public class User extends BmobObject {
    public Boolean getChat() {
        return isChat;
    }

    public void setChat(Boolean chat) {
        isChat = chat;
    }

    public Boolean getOnline() {
        return isOnline;
    }

    public void setOnline(Boolean online) {
        isOnline = online;
    }


    public String getChatWith() {
        return chatWith;
    }

    public void setChatWith(String chatWith) {
        this.chatWith = chatWith;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }


    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }


    public String getSign() {
        return sign;
    }

    public void setSign(String sign) {
        this.sign = sign;
    }

    //签名
    private String sign;
    //e-mail
    private  String email;
    //密码
    private String password;
    //用户名
    private String username;
    //是否在线
    private Boolean isOnline;
    //是否正在聊天
    private Boolean isChat;
    //与谁聊天中。。。
    private String chatWith;
}
