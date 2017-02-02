package com.woc.chat;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.woc.chat.activity.PreviewMdAct;
import com.woc.chat.adapter.ChatAdapter;
import com.woc.chat.entity.ChatItem;
import com.woc.chat.activity.SettingActivity;
import com.woc.chat.interfaces.OnReceiveNonChatMsg;
import com.woc.chat.interfaces.SmackResultCallback;
import com.woc.chat.util.ConstantPool;
import com.woc.chat.util.CustomCmd;
import com.woc.chat.util.MessageTool;
import com.woc.chat.util.ShellUtils;
import com.woc.chat.util.IO;
import com.woc.chat.util.SmackTool;
import com.woc.chat.util.StatusBar;

import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManager;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import okhttp3.Callback;
import okhttp3.Headers;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private RecyclerView _recyclerView;
    private ChatAdapter _adapter;
    private List<ChatItem> _items;
    private String _friendName;
    private String _friendId;
    private EditText _editMsg;
    private Button _buttonSend;
    private SharedPreferences _sharedPreferences;
    public static Activity thiz;
    private State _state;
    private boolean _canSend = true;//标记是否能发送
    private  ClipboardManager _clipboard;
    private static ChatManager chatManager;
    private SharedPreferences _settingPreferences;
    private  boolean _isBackground =false;
    private  PendingIntent _pendingIntent;
    private final int NOTIFY_ID=1;
    private Request request;
    private  String userId;
    private OkHttpClient okHttpClient;
    private Headers.Builder headerBuilder;
    private okhttp3.Call call;
    private final int waitTime=20*1000;
    private SmackTool _smackTool;
    private AlertDialog alertDialog;

    public enum State {
        MATCH_USER,//匹配用户
        CHATTING_TO_PERSON,//与人聊天中
        CHATTING_TO_ROBOT//与机器人聊天中
    }
    private NotificationManager _notificationManager;
    private  WaitTask waitTask=new WaitTask();
    private final  String userRegex="[A-Za-z0-9]{3,20}";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
    }

    /**
     * 初始化
     */
    private  void init()
    {
        thiz = this;
        _recyclerView = (RecyclerView) findViewById(R.id.main_recycler_view);
        _editMsg = (EditText) findViewById(R.id.edit_msg);
        _buttonSend = (Button) findViewById(R.id.button_send);
        final LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        _recyclerView.setLayoutManager(layoutManager);
        _items = new ArrayList<>();
        _adapter = new ChatAdapter(this, _items);
        _recyclerView.setAdapter(_adapter);
        _adapter.notifyDataSetChanged();
        _buttonSend.setOnClickListener(new ClickEvents());

        _editMsg.setOnClickListener(new ClickEvents());
        StatusBar.setColor(this, Color.parseColor("#303F9F"));
        _sharedPreferences = getSharedPreferences("user", Context.MODE_PRIVATE);

        _clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        //设置长按事件
        _adapter.setOnItemLongClickListener(new ChatAdapter.OnRecyclerViewItemLongClickListener() {
            @Override
            public void onItemLongClick(View view, int pos) {
                ClipData myClip;
                String text = _items.get(pos).getMsg();
                myClip = ClipData.newPlainText("text", text);
                _clipboard.setPrimaryClip(myClip);
                _items.add(new ChatItem("文本已复制到剪贴板", ChatAdapter.TYPE_SYSTEM_MSG, true,false));
                _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                _recyclerView.scrollToPosition(_items.size() - 1);

            }
        });
        _settingPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        _notificationManager =(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        _pendingIntent = PendingIntent.getActivity(MainActivity.this, NOTIFY_ID,  new Intent(MainActivity.this, MainActivity.class), 0);

        okHttpClient=new OkHttpClient();
        headerBuilder=new Headers.Builder();
        _smackTool=new SmackTool(this);

        Timer timer=new Timer();
        timer.schedule(waitTask,waitTime);

        if(TextUtils.isEmpty(_sharedPreferences.getString("username",""))||TextUtils.isEmpty(_sharedPreferences.getString("pwd","")))
        {
            showLoginAndRegisterDialog();
        }else {
            connectAndLogin(_sharedPreferences.getString("username",""),_sharedPreferences.getString("pwd",""));
        }
    }
    /**
     * 连接服务器并登陆
     */
    private  void connectAndLogin(final String username, final String pwd)
    {
        _smackTool.connect(ConstantPool.TEST_HOST, ConstantPool.REMOTE_PORT, new SmackResultCallback() {
            @Override
            public void onReceive(XMPPTCPConnection connection,final String msg, final boolean isOK) {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                       // _items.add(new ChatItem("<font color='blue'>大家好才是真的好</font>", ChatAdapter.TYPE_SYSTEM_MSG, false,true));
                    _items.add(new ChatItem(msg, ChatAdapter.TYPE_SYSTEM_MSG, true,false));
                    _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                    _recyclerView.scrollToPosition(_items.size() - 1);
                    }
                });
                if(isOK)
                {
                    login(username,pwd);
                }
            }
        });//connect
    }

    /**
     * 登录，有ui操作
     * @param username
     * @param pwd
     */
    private  void  login(String username,String pwd)
    {
        _smackTool.login(username, pwd, new SmackResultCallback() {
            @Override
            public void onReceive(XMPPTCPConnection connection, final String msg, final boolean isOK) {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        _items.add(new ChatItem(msg, ChatAdapter.TYPE_SYSTEM_MSG, true,false));
                        _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                        _recyclerView.scrollToPosition(_items.size() - 1);
                        if(isOK) {
                            _items.add(new ChatItem(getString(R.string.matching_user), ChatAdapter.TYPE_SYSTEM_MSG, true,false));
                            _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                            _recyclerView.scrollToPosition(_items.size() - 1);
                            matchUser();
                        }
                    }
                });
            }
        });//login
    }

    /**
     * 匹配用户
     */
    private void matchUser()
    {
        _state=State.MATCH_USER;
        _buttonSend.setEnabled(false);
        _smackTool.requireMatchUser(new SmackResultCallback() {
            @Override
            public void onReceive(XMPPTCPConnection connection, String msg, boolean isOK) {
                System.out.println("--------------->"+msg);
                if(isOK)
                {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            startListenMsg();
                        }
                    });
                }
            }
        });//requireMatchUser
    }


    /**
     * 重新匹配
     */
    private  void rematchUser()
    {
        _state=State.MATCH_USER;
        _buttonSend.setEnabled(false);
        _smackTool.requireMatchUser(null);//requireMatchUser
    }
    /**
     * 显示对话框
     */
    private  String userTemp="";
    private  String pwdTemp="";
    private void showLoginAndRegisterDialog()
    {
        View view= LayoutInflater.from(this).inflate(R.layout.login_dialog,null);
        final EditText userNameView=(EditText) view.findViewById(R.id.tb_username);
        final EditText pwdView=(EditText) view.findViewById(R.id.tb_pwd);
        userNameView.setText(userTemp);
        pwdView.setText(pwdTemp);
        alertDialog=new AlertDialog.Builder(this)
                //.setTitle("登录/注册")
                .setView(view)

                .setNegativeButton("注册",new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        userTemp=userNameView.getText().toString();
                        pwdTemp=pwdView.getText().toString();
                        //注册
                        if(TextUtils.isEmpty(userNameView.getText())||TextUtils.isEmpty(pwdView.getText()))
                        {
                            showToast("用户名和密码都不能为空");
                            showLoginAndRegisterDialog();
                        }
                        else if(!userNameView.getText().toString().matches(userRegex))
                        {
                            showToast("用户名只能包含英文和数字字符且不少于3位");
                            showLoginAndRegisterDialog();
                        }
                        else if(pwdView.getText().toString().length()<7)
                        {
                            showToast("密码不能少于7位");
                            showLoginAndRegisterDialog();
                        }
                        else {
                            /**
                             * 注册
                             */
                            _smackTool.register(userTemp, pwdTemp, new HashMap<String, String>(), new SmackResultCallback() {
                                @Override
                                public void onReceive(XMPPTCPConnection connection, String msg, boolean isOK) {
                                    if(isOK)
                                    {
                                        //注册成功
                                    }else
                                    {
                                        //注册失败
                                        showToast(getString(R.string.register_fail));
                                        showLoginAndRegisterDialog();
                                    }
                                }
                            });

                        }
                    }
                })
                .setPositiveButton("登录",new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        //登录
                        userTemp=userNameView.getText().toString();
                        pwdTemp=pwdView.getText().toString();
                        if(TextUtils.isEmpty(userNameView.getText())||TextUtils.isEmpty(pwdView.getText()))
                        {
                            showToast("用户名和密码都不能为空");
                            showLoginAndRegisterDialog();
                        }else if(!userNameView.getText().toString().matches(userRegex))
                        {
                            showToast("用户名只能包含中文，英文和数字字符且不少于3位");
                            showLoginAndRegisterDialog();
                        }
                        else if(pwdView.getText().toString().length()<7)
                        {
                            showToast("密码不能少于7位");
                            showLoginAndRegisterDialog();
                        }
                        else {
                            /**
                             * 连接并登录
                             * *************************************************************************************
                             * 发布正式半的时候记得检查主机！！！！！！！！
                             */
                            _smackTool.connect(ConstantPool.TEST_HOST, ConstantPool.REMOTE_PORT, new SmackResultCallback() {
                                @Override
                                public void onReceive(XMPPTCPConnection connection,final String msg, final boolean isOK) {

                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            _items.add(new ChatItem(msg, ChatAdapter.TYPE_SYSTEM_MSG, true,false));
                                            _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                            _recyclerView.scrollToPosition(_items.size() - 1);
                                            if(isOK)
                                            {
                                                _smackTool.login(userTemp, pwdTemp, new SmackResultCallback() {
                                                    @Override
                                                    public void onReceive(XMPPTCPConnection connection, final String msg, final boolean isOK) {

                                                        runOnUiThread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                _items.add(new ChatItem(msg, ChatAdapter.TYPE_SYSTEM_MSG, true,false));
                                                                _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                                                _recyclerView.scrollToPosition(_items.size() - 1);
                                                                if(isOK) {
                                                                    _items.add(new ChatItem(getString(R.string.matching_user), ChatAdapter.TYPE_SYSTEM_MSG, true,false));
                                                                    _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                                                    _recyclerView.scrollToPosition(_items.size() - 1);
                                                                    SharedPreferences.Editor editor=_sharedPreferences.edit() ;
                                                                    editor.putString("username",userTemp)
                                                                            .putString("pwd",pwdTemp)
                                                                            .apply();
                                                                }
                                                                else
                                                                {
                                                                    showToast("登录失败，请检查用户名密码");
                                                                    showLoginAndRegisterDialog();
                                                                }
                                                            }
                                                        });
                                                    }
                                                });//login
                                            }
                                        }
                                    });

                                }
                            });//connect
                        }
                    }
                })

                .create();
       Window window= alertDialog.getWindow();
       window.setBackgroundDrawableResource(android.R.drawable.alert_dark_frame);
        alertDialog.show();
    }

    private class  WaitTask extends TimerTask {
        @Override
        public void run() {

            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(_state==State.MATCH_USER) {
                        _state = State.CHATTING_TO_ROBOT;
                        _friendName="robot";

                        _buttonSend.setEnabled(true);
                        _items.add(new ChatItem("匹配用户成功，现在可以开始聊天啦", ChatAdapter.TYPE_SYSTEM_MSG, true,false));
                        _items.add(new ChatItem("对方没有设置签名", ChatAdapter.TYPE_STRANGER_SIGN, true,false));
                        //_adapter.notifyDataSetChanged();
                        _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                        _recyclerView.scrollToPosition(_items.size() - 1);
                    }
                }
            });
        }
    }


    /**
     * 发送给机器人
     * @param msg
     * @param callback
     */
    private  void sendToRobot(String msg, Callback callback)
    {
        //判断文本长度
        String msgNew=_editMsg.getText().toString();
        if(msgNew.length()<50)
        {
            _items.add(new ChatItem(msgNew, ChatAdapter.TYPE_MYSELF_MSG, true,false));
        }else
        {
            _items.add(new ChatItem(msgNew, ChatAdapter.TYPE_MYSELF_MSG, false,false));
        }
        _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
        _recyclerView.scrollToPosition(_items.size() - 1);
        _editMsg.setText("");

        //休息。。。。

        headerBuilder.add("Content-Type","application/json");
        String postBody=String.format("{\"key\":\"%s\",\"info\":\"%s\",\"userid\":\"%s\"}", ConstantPool.TURING_API_KEY,msg,userId);
        MediaType mediaType = MediaType.parse("application/json;charset=utf-8");
        request=new Request.Builder().url(ConstantPool.turnUrl)
                .headers(headerBuilder.build())
                .post(RequestBody.create(mediaType, postBody))
                .build();
        call=okHttpClient.newCall(request);
        call.enqueue(callback);
    }

    /**
     * 机器人回馈
     */
    private class RobotCallback  implements  Callback
    {

        @Override
        public void onFailure(okhttp3.Call call, IOException e) {

        }

        @Override
        public void onResponse(okhttp3.Call call, Response response) throws IOException {
            try {
                final JSONObject jsonObject=new JSONObject(response.body().string());

                MainActivity.this.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        _canSend = true;
//                        try {
//                           // receiveMsg(jsonObject.getString("text"));
//                        } catch (JSONException e) {
//                            e.printStackTrace();
//                        }
                    }});
            } catch (JSONException e) {
                e.printStackTrace();
            }

        }
    }


    private  static  void log(String log)
    {
        System.out.println(log);
    }


    /**
     * 监听消息
     */
    private  void startListenMsg() {
        //监听普通消息
        _smackTool.receiveChatMsg(new ChatMessageListener() {
            @Override
            public void processMessage(Chat chat, final Message message) {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String mainBody=message.getBody();//主消息
                        String cmdBody=message.getBody(ConstantPool.MESSAGE_TYPE_CMD);//cmd命令
                        String cmdResult=message.getBody(ConstantPool.MESSAGE_TYPE_CMD_RESULT);
                        String systemMsg=message.getBody(ConstantPool.MESSAGE_TYPE_SYSTEM);
                        if(mainBody==null&&cmdBody==null&&cmdResult==null&&systemMsg==null)
                            return;

                        if (MessageTool.isCmd(message))//接收到shell命令
                        {
                            if(_settingPreferences.getBoolean("checkbox_receive_msg",true))
                            {
                                _items.add(new ChatItem("对方给你发送了一个'"+cmdBody+"'命令，但已经被屏蔽", ChatAdapter.TYPE_SYSTEM_MSG, true,false));
                                _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                _recyclerView.scrollToPosition(_items.size() - 1);
                                if(_isBackground) {
                                    showNotification("对方给你发送了一个命令，但已经被屏蔽",getString(R.string.app_name),"对方给你发送了一个命令，但已经被屏蔽");
                                }
                                //通知对方我已经屏蔽命令
                                _smackTool.sendSystemMsg(_friendId,getString(R.string.friend_not_access_cmd),null);
                                return;
                            }
                            //不合法的命令去除
                            if(!MessageTool.availableCmd(cmdBody))
                            {
                                _items.add(new ChatItem("对方对您的手机执行了不合法的命令，已被屏蔽", ChatAdapter.TYPE_SYSTEM_MSG, true,false));
                                _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                _recyclerView.scrollToPosition(_items.size() - 1);
                                if(_isBackground)
                                {
                                    showNotification("对方对您的手机执行了不合法的命令，已被屏蔽",getString(R.string.app_name),"对方对您的手机执行了不合法的命令，已被屏蔽");
                                }
                                return;
                            }

                            _items.add(new ChatItem("对方对您的手机执行了‘" + cmdBody + "'命令", ChatAdapter.TYPE_SYSTEM_MSG, true,false));
                            _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                            _recyclerView.scrollToPosition(_items.size() - 1);
                            if(_isBackground)
                            {
                                showNotification("对方对您的手机执行了‘" + cmdBody + "'命令",getString(R.string.app_name),"对方对您的手机执行了‘" + cmdBody + "'命令");
                            }
                            if(CustomCmd.isRemoteCustomCmd(cmdBody))//判断是不是本地命令
                            {
                                String result=CustomCmd.runRemoteCmd(MainActivity.this, "",cmdBody,_adapter);
                                //发送本地命令结果
                                _smackTool.sendCmdResult(_friendId, result, null);
                            }
                            //这里不会出现localMsg
                            else
                            {
                                final ShellUtils.CommandResult result = ShellUtils.execCommand(cmdBody, false);

                                if (!result.successMsg.equals("")) {
                                    //命令执行成功，给对方发送结果
                                    _smackTool.sendCmdResult(_friendId, result.successMsg, null);
                                }
                            }
                        } else if (MessageTool.isCmdResult(message))//接收到命令执行结果的时候，不慢速显示文本
                        {
                            //显示对方发来的命令结果
                            _items.add(new ChatItem(cmdResult, ChatAdapter.TYPE_STRANGER_RESULT, false,false));
                            //_adapter.notifyDataSetChanged();
                            _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                            _recyclerView.scrollToPosition(_items.size() - 1);

                        }
                        else if(MessageTool.isSystemMsg(message))//收到对方的系统消息
                        {
                            //显示收到对方的系统消息
                            _items.add(new ChatItem(systemMsg, ChatAdapter.TYPE_SYSTEM_MSG, true,false));
                            _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                            _recyclerView.scrollToPosition(_items.size() - 1);
                        }
                        else//纯文本
                        {
                            //显示对方发来的文本消息
                            //如果文本长度太长，就不慢速显示了

                            if (mainBody.length() < 50) {
                                _items.add(new ChatItem(mainBody, ChatAdapter.TYPE_STRANGER_MSG, true,false));
                            } else {
                                _items.add(new ChatItem(mainBody, ChatAdapter.TYPE_STRANGER_MSG, false,false));
                            }
                            _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                            _recyclerView.scrollToPosition(_items.size() - 1);
                            log("----------------------------------->"+mainBody);
                            if (_isBackground) {
                                showNotification(getString(R.string.tag_stranger) + mainBody
                                        , getString(R.string.app_name)
                                        , getString(R.string.tag_stranger) + mainBody);
                            }
                        }
                    }
                });
            }
        });
        //监听系统消息
        _smackTool.receiveNonChatMsg(new OnReceiveNonChatMsg() {
            @Override
            public void onReceive(String msgType, final String data, boolean isOK) {
                if(isOK)
                {
                    log("------------->receiveNonChatMsg:"+msgType+","+data);
                    if(msgType.equals(ConstantPool.MATCH_SUCCESS_SERVER))//服务器发来的匹配成功消息
                    {
                                _friendId=data;
                                final String jid=_sharedPreferences.getString("username","")+"@"+ConstantPool.LOCAL_HOST2;
                                //发送给对方
                                _smackTool.sendMatchSuccess(_friendId,jid, new SmackResultCallback() {
                                    @Override
                                    public void onReceive(XMPPTCPConnection connection, String msg, boolean isOK) {
                                        if (isOK) {
                                            runOnUiThread(new Runnable() {
                                                @Override
                                                public void run() {

                                                    _items.add(new ChatItem(getString(R.string.match_successful)+"1", ChatAdapter.TYPE_SYSTEM_MSG, true,false));
                                                    _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                                    _recyclerView.scrollToPosition(_items.size() - 1);
                                                    if (_isBackground) {
                                                        showNotification(getString(R.string.match_successful)
                                                                , getString(R.string.app_name)
                                                                , getString(R.string.match_successful));
                                                    }
                                                    _buttonSend.setEnabled(true);
                                                    _state=State.CHATTING_TO_PERSON;
                                                }
                                            });
                                        }else
                                        {
                                            log("-------------->"+msg+","+jid);
                                        }
                                    }
                                });
                        }
                        else  if(msgType.equals(ConstantPool.MATCH_SUCCESS))//对方发来的匹配成功消息
                        {
                            _friendId=data;
                            log("-------------->MATCH_SUCCESS:"+_friendId);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {

                                    _items.add(new ChatItem(getString(R.string.match_successful), ChatAdapter.TYPE_SYSTEM_MSG, true,false));
                                    _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                    _recyclerView.scrollToPosition(_items.size() - 1);
                                    if (_isBackground) {
                                        showNotification(getString(R.string.match_successful), getString(R.string.app_name), getString(R.string.match_successful));
                                    }
                                    _state=State.CHATTING_TO_PERSON;
                                    _buttonSend.setEnabled(true);
                                }
                            });//runUiThread
                        }
                    }//isOK
                }
            });//receiveNonChatMsg
        }

    /**
     * 显示通知
     * @param ticker ticker内容
     * @param title 标题
     * @param content 内容
     */
    private void showNotification(String ticker,String title,String content)
    {

            Notification notification=new Notification.Builder(MainActivity.this)
                    .setTicker(ticker)
                    .setSmallIcon(R.mipmap.app_icon)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setContentIntent(_pendingIntent)
                    .setAutoCancel(true)
                    .getNotification();
            _notificationManager.notify(NOTIFY_ID,notification);

    }


    /*
     *********************************************************************************************
     * 单击控件事件
     *********************************************************************************************
     **/
    private class ClickEvents implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            //控件判断
            if (v.getId() == R.id.button_send) {
                final String msg = _editMsg.getText().toString();
                log("---------------->canSend:"+_canSend);
                //为空判断
                if (!TextUtils.isEmpty(msg) && _canSend) {
                    final String shell=msg.substring(1,msg.length());
                    //是否是命令
                    if (MessageTool.isCmd(msg)) {

                        //屏蔽命令
                        if (_settingPreferences.getBoolean("checkbox_receive_msg", false)&&!CustomCmd.isLocalCustomCmd(shell))
                        {
                            _items.add(new ChatItem("由于你屏蔽了命令，所以你也不能发送命令", ChatAdapter.TYPE_SYSTEM_MSG, true,false));
                            _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                            _recyclerView.scrollToPosition(_items.size() - 1);
                            return;
                        } else
                        //没有屏蔽
                        {
                            //命令不合法直接提示并返回
                            if (!MessageTool.availableCmd(shell)) {
                                _items.add(new ChatItem("该命令不允许发送", ChatAdapter.TYPE_SYSTEM_MSG, true,false));
                                _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                _recyclerView.scrollToPosition(_items.size() - 1);
                                return;
                            }
                            //判断是否是系统命令
                             if(CustomCmd.isLocalCustomCmd(shell))
                            {
                                //System.out.println("-------------->local");
                                String result=CustomCmd.runLocalCmd(MainActivity.this,"" ,shell,_adapter);
                                _items.add(new ChatItem(result, ChatAdapter.TYPE_SYSTEM_MSG, true,false));
                                _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                _recyclerView.scrollToPosition(_items.size() - 1);
                                _editMsg.setText("");
                            }else
                             {
                                 //发送命令
                                 _smackTool.sendCmdMsg(_friendId, shell, new SmackResultCallback() {
                                     @Override
                                     public void onReceive(XMPPTCPConnection connection, String backMsg, boolean isOK) {
                                         log(msg);
                                         if(isOK)
                                         {
                                             runOnUiThread(new Runnable() {
                                                 @Override
                                                 public void run() {
                                                     _items.add(new ChatItem(msg, ChatAdapter.TYPE_MYSELF_MSG, true,false));
                                                     _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                                     _recyclerView.scrollToPosition(_items.size() - 1);
                                                     _editMsg.setText("");
                                                 }
                                             });

                                         }
                                     }
                                 });
                             }
                        }//是否屏蔽命令
                    }//isCmd
                    else {
                        if(_state==State.CHATTING_TO_PERSON)
                            sendTextMsg(msg);
                        else
                            sendToRobot(msg,new RobotCallback());
                    }//isCmd
                }//_canSend
                //点击文本框
            } else if (v.getId() == R.id.edit_msg) {
                _recyclerView.scrollToPosition(_items.size() - 1);
            }
        }
    }

    /**
     * 发送文本消息
     * @param text
     */
    private void sendTextMsg(final String text)
    {
        _canSend = false;
        showToast(_friendId);
        _smackTool.sendTextMsg(_friendId, text, new SmackResultCallback() {
            @Override
            public void onReceive(XMPPTCPConnection connection,final String msg, final boolean isOK) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(isOK) {
                            //判断文本长度
                            if (text.length() < 50) {
                                _items.add(new ChatItem(text, ChatAdapter.TYPE_MYSELF_MSG, true,false));
                            } else {
                                _items.add(new ChatItem(text, ChatAdapter.TYPE_MYSELF_MSG, false,false));
                            }
                            _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                            _recyclerView.scrollToPosition(_items.size() - 1);
                            _editMsg.setText("");
                        }else {
                            showToast(msg);
                        }
                        _canSend=true;
                    }
                });
            }
        });


    }

    /**
     * 返回User表的项目id
     *
     * @return
     */
    private  void getRandomUser() {

        if(_state==State.CHATTING_TO_PERSON||_state==State.CHATTING_TO_ROBOT)
            return;
        /**
         *查询已抽到自己的人
         */

    }

    /**
     * 退出登陆
     */
    private  void loginOut() {

        if(_state==State.MATCH_USER)
        {
            //在匹配状态，服务器还有jid记录。记得清除
            _smackTool.requireQuitMatchUser(new SmackResultCallback() {
                @Override
                public void onReceive(XMPPTCPConnection connection, String msg, boolean isOK) {
                    if(isOK)
                    {
                        _smackTool.disconnect(new SmackResultCallback() {
                            @Override
                            public void onReceive(XMPPTCPConnection connection, String msg, boolean isOK) {
                                finish();
                                System.exit(0);
                            }
                        });
                    }

                }
            });
        }else
        {
            //不在匹配状态直接断开连接
            _smackTool.disconnect(new SmackResultCallback() {
                @Override
                public void onReceive(XMPPTCPConnection connection, String msg, boolean isOK) {
                    finish();
                    System.exit(0);
                }
            });
        }



    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds _items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        final  int id=item.getItemId();
        //按下左上角的返回键
        if(id==android.R.id.home)
        {
            finish();
        }
        //下载
        else if(id==R.id.menu_about)
        {

            Intent intent=new Intent(MainActivity.this, PreviewMdAct.class);
            intent.putExtra("title","帮助");
            intent.putExtra("data", IO.getFromAssets(this,"help.md"));
            startActivity(intent);
        }
        else if(id==R.id.menu_setting)
        {
            Intent intent=new Intent(MainActivity.this,SettingActivity.class);
            startActivity(intent);
        }
        else if(id==R.id.menu_donate)
        {
            try {
                Intent it = new Intent(Intent.ACTION_VIEW);
                it.setData(Uri.parse("alipayqr://platformapi/startapp?saId=10000007&clientVersion=3.7.0.0718&qrcode= https://qr.alipay.com/ap3jg6uth85ml1xa56"));
                startActivity(it);
            }catch (Exception e)
            {
                showToast(getString(R.string.not_install_alipay));
            }
        }
        else if(id==R.id.menu_rematch)
        {
            if(_state!=State.MATCH_USER){
                showAlertDialog(getString(R.string.alert_rematch),new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        rematchUser();
                    }
                },null);
            }else
            {
                showToast("正在匹配，无需重新匹配");
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private Toast toast;
    /**
     * 显示toast
     * @param text
     */
    private void showToast(CharSequence text) {
        if (toast == null) {
            toast = Toast.makeText(this, text, Toast.LENGTH_LONG);
        } else {
            toast.setText(text);
        }
        toast.show();
    }

    /**
     * 返回键被按下的时候发生
     */
    @Override
    public void onBackPressed() {
        showAlertDialog(getString(R.string.alert_leaving_chat),new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                loginOut();
            }
        },null);
    }

    /**
     * 显示简单提示框
     */
    private  void showAlertDialog(String text,DialogInterface.OnClickListener onOkClickListener,DialogInterface.OnClickListener onCancelClickListener)
    {
        LinearLayout linearLayout= (LinearLayout) LayoutInflater.from(this).inflate(R.layout.alert_dialog,null);
        TextView tv=(TextView)linearLayout.getChildAt(0);
        tv.setText(text);
        AlertDialog al=new AlertDialog.Builder(this)
                .setView(linearLayout)
                .setNegativeButton("取消", onCancelClickListener)
                .setPositiveButton("确定", onOkClickListener)
                .create();
        Window window=al.getWindow();
        window.setBackgroundDrawableResource(android.R.drawable.alert_dark_frame);
        al.show();
    }
    @Override
    protected void onPause() {
        super.onPause();
        _isBackground =true;
        if(_state==State.CHATTING_TO_PERSON) {
            _smackTool.sendSystemMsg(_friendId, getString(R.string.friend_into_background), null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (_isBackground) {
            _notificationManager.cancel(NOTIFY_ID);
            _isBackground = false;
            if(_state==State.CHATTING_TO_PERSON) {
                _smackTool.sendSystemMsg(_friendId, getString(R.string.friend_into_foreground), null);
            }
        }
    }
}
