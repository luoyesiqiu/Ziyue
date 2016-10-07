package com.woc.chat.parser;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by zyw on 2016/9/20.
 * 用户转换类
 */
public class UserParser {
    private JSONObject json;
    private  String appKey;
    private String tableName;
    private  String objectId;
    private  Data data;

    public UserParser(JSONObject json) throws JSONException {
        this.json=json;
        data=new Data(json.getJSONObject("data"));

    }
    public String getAppKey() {
        return appKey;
    }


    public String getTableName() {
        return tableName;
    }

    public Data getData() {
        return data;
    }


    public String getObjectId() {
        return objectId;
    }


    public static class Data{


        private  String createAt;
        private String username;
        private  Boolean isOnline;
        private  Boolean isChat;
        private  String objectId;
        private  String updateAt;
        private JSONObject json;
        public Data(JSONObject json) throws JSONException
        {
            this.json=json;
            username =json.getString("username");
            isOnline =json.getBoolean("isOnline");
            isChat =json.getBoolean("isChat");
        }
        public String getCreateAt() {
            return createAt;
        }


        public String getUsername() {
            return username;
        }


        public boolean getIsOnline() {
            return isOnline;
        }


        public boolean getIsChat() {
            return isChat;
        }


        public String getObjectId() {
            return objectId;
        }

        public String getUpdateAt() {
            return updateAt;
        }



    }
}
