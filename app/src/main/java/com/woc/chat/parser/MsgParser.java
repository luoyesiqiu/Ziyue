package com.woc.chat.parser;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by zyw on 2016/9/15.
 * 消息转换者
 */
public class MsgParser {

    private  JSONObject json;
    private  String appKey;
    private String tableName;
    private  String objectId;
    private  Data data;

    public MsgParser(JSONObject json) throws JSONException {
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
        private String msg;
        private  String msgFrom;
        private  String msgTo;
        private  String objectId;
        private  String updateAt;
        private JSONObject json;
        public Data(JSONObject json) throws JSONException
        {
                this.json=json;
                msg=json.getString("msg");
                msgFrom=json.getString("msgFrom");
                msgTo=json.getString("msgTo");
        }
        public String getCreateAt() {
            return createAt;
        }


        public String getMsg() {
            return msg;
        }


        public String getMsgFrom() {
            return msgFrom;
        }


        public String getMsgTo() {
            return msgTo;
        }


        public String getObjectId() {
            return objectId;
        }

        public String getUpdateAt() {
            return updateAt;
        }



    }

}
