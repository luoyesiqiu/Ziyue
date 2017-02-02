package com.woc.chat.util;

/**
 * Created by zyw on 2017/1/30.
 */
public class ConstantPool {

    //客户端发送的匹配消息
    public static final String MATCH_USER="match_user";//请求匹配用户
    public static final String MATCH_SUCCESS="match_success";//匹配用户成功,由一个用户告诉另外一个用户
    //服务端发送的匹配消息
    public static final String MATCH_SUCCESS_SERVER="match_success_server";//匹配用户成功

    public static final String QUIT_CHAT="quit_chat";
    public static final String MATCH_USER_PLUGIN_NAME="MatchUserPlugin";
    public static final String TAG_INFO="info";
    public static  final String MESSAGE_TYPE_CMD="cmd";
    public static  final String MESSAGE_TYPE_CMD_RESULT="cmd_result";
    public static  final String MESSAGE_TYPE_SYSTEM="system_msg";
    public static  final  String INFO_NAMESPACE="match:iq:info";
    public static  final  String DATA_TYPE_NIL="nil";
    public static  final  String LOCAL_HOST1="127.0.0.1";
    public static  final  String LOCAL_HOST2="localhost";
    public static  final  String REMOTE_HOST="58";
    public static  final  String TEST_HOST="172.24.219.1";
    public static  final  String TEST_User1="zyw@localhost";
    public static  final  String TEST_User2="zyw8@localhost";
    public static  final  int REMOTE_PORT=5222;
    public static final String TURING_API_KEY = "16e274e0cb6749f0b61e6a742d101737";
    public static final  String turnUrl="http://www.tuling123.com/openapi/api";
    public static  String[] unusableCmd ={"reboot","su","rm","vi","busybox","mv","eval","am", "chmod", "cp", "curl", "dd", "grep", "kill", "mount", "pm", "run-as", "sh", "umount", "wget"};

}
