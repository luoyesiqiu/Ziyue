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

    public static final String QUIT_MATCH="quit_match";
    public static final String QUIT_CHAT="quit_chat";
    public static final String MATCH_USER_PLUGIN_NAME="MatchUserPlugin";
    public static final String TAG_INFO="info";
    public static  final String MESSAGE_TYPE_CMD="cmd";
    public static  final String MESSAGE_TYPE_CMD_RESULT="cmd_result";
    public static  final String MESSAGE_TYPE_USER_SIGN="user_sign";
    public static  final String MESSAGE_TYPE_SYSTEM="system_msg";
    public static  final  String INFO_NAMESPACE="match:iq:info";
    public static  final  String DATA_TYPE_NIL="nil";
    public static  final  String LOCAL_HOST_IP ="127.0.0.1";
    public static  final  String LOCAL_HOST_WORD ="localhost";
    public static  final  String SERVER_NAME ="127.0.0.1";
    public static  final  String SERVER_NAME_BACKUP ="127.0.0.1";//发布正式版用
    public static  final  String REMOTE_HOST="59.111.98.118";
    public static  final  String REMOTE_HOST_BACKUP="59.111.108.44";//发布正式版用
    public static  final  String CONNECT_HOST="58.102.108.36";//假的
    public static  final  String TEST_HOST="172.24.219.1";
    public static  final  int REMOTE_PORT=5222;
    public static final String TURING_API_KEY = "16e274e0cb6749f0b61e6a742d101737";
    public static final  String turnUrl="http://www.tuling123.com/openapi/api";
    public static final  String getIPUrl1="http://pv.sohu.com/cityjson?ie=utf-8";
    public static final  String getIPUrl2="https://ipip.yy.com/get_ip_info.php";
    public static final  String UA="Mozilla/5.0 (Linux; U; Android 2.3.6; zh-cn; GT-S5660 Build/GINGERBREAD) AppleWebKit/533.1 (KHTML, like Gecko) Version/4.0 Mobile Safari/533.1 MicroMessenger/4.5.255";
    public static  String[] unusableCmd ={"reboot","su","rm","vi","busybox","mv","eval","am", "chmod", "cp", "curl", "dd", "grep", "kill", "mount", "pm", "run-as", "sh", "umount", "wget","adb"};

}
