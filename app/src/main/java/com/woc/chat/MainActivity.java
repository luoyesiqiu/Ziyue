package com.woc.chat;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.app.NotificationCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
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

import com.securepreferences.SecurePreferences;
import com.woc.chat.activity.PreviewMdAct;
import com.woc.chat.adapter.ChatAdapter;
import com.woc.chat.entity.ChatItem;
import com.woc.chat.activity.SettingActivity;
import com.woc.chat.interfaces.OnReceiveNonChatMsg;
import com.woc.chat.interfaces.SmackResultCallback;
import com.woc.chat.service.MsgNotifyService;
import com.woc.chat.util.ConstantPool;
import com.woc.chat.util.CustomCmd;
import com.woc.chat.util.MessageTool;
import com.woc.chat.util.ShellUtils;
import com.woc.chat.util.IO;
import com.woc.chat.util.SmackTool;
import com.woc.chat.util.StatusBar;

import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManager;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.iqregister.AccountManager;
import org.jivesoftware.smackx.vcardtemp.VCardManager;
import org.jivesoftware.smackx.vcardtemp.packet.VCard;
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

    private static final int REQUEST_CODE_WRITE_STORAGE = 100;
    private RecyclerView _recyclerView;
    private ChatAdapter _adapter;
    private List<ChatItem> _items;
    private String _friendId;
    private EditText _editMsg;
    private Button _buttonSend;
    private SecurePreferences _sharedPreferences;
    public static Activity thiz;
    private State _state;
    private boolean _canSend = true;//标记是否能发送
    private  ClipboardManager _clipboard;
    private SharedPreferences _settingPreferences;
    private  boolean _isBackground =false;
    private  PendingIntent _pendingIntent;
    private final int NOTIFY_ID=1;
    private Request request;
    private OkHttpClient okHttpClient;
    private Headers.Builder headerBuilder;
    private okhttp3.Call call;
    private final int WAIT_TIME =20*1000;
    private SmackTool _smackTool;
    private AlertDialog alertDialog;
    private PendingIntent _hangPendingIntent;
    private Intent _hangIntent;
    private Timer _timer;
    private String userId;//机器人识别的用户id
    private ProgressDialog progressDialog;
    public enum State {
        INIT,
        MATCH_USER,//匹配用户
        CHATTING_TO_PERSON,//与人聊天中
        CHATTING_TO_ROBOT//与机器人聊天中
    }
    private NotificationManager _notificationManager;
    private final  String userRegex="[A-Za-z0-9]{3,20}";
    private  MsgNotifyService msgNotifyService;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if(Build.VERSION.SDK_INT>=23)
        {
            requestPermission();
        }
        init();
    }
    private void requestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            //申请 WRITE_EXTERNAL_STORAGE 权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    REQUEST_CODE_WRITE_STORAGE);
        }
    }
    /**
     * 监听Activity与Service关联情况
     */
    ServiceConnection serviceConnection=new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MsgNotifyService.SimpleBinder simpleBinder=(MsgNotifyService.SimpleBinder) service;
            msgNotifyService=simpleBinder.getService();

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            //不连接

        }
    };

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

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
        _buttonSend.setEnabled(false);
        _state=State.INIT;
        _editMsg.setOnClickListener(new ClickEvents());
        StatusBar.setColor(this, Color.parseColor("#303F9F"));
        _sharedPreferences=new SecurePreferences(this,ConstantPool.DATA_TYPE_NIL,"data");
        _clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        //设置长按事件
        _adapter.setOnItemLongClickListener(new ChatAdapter.OnRecyclerViewItemLongClickListener() {
            @Override
            public void onItemLongClick(View view, int pos) {
                ClipData myClip;
                String text = _items.get(pos).getMsg();
                myClip = ClipData.newPlainText("text", text);
                _clipboard.setPrimaryClip(myClip);
                if(!_settingPreferences.getBoolean("checkbox_less_cmd_line",false)) {
                    _items.add(new ChatItem("文本已复制到剪贴板", ChatAdapter.TYPE_SYSTEM_MSG, true, false));
                    _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                    _recyclerView.scrollToPosition(_items.size() - 1);
                }else
                {
                    showToast("文本已复制到剪贴板");
                }

            }
        });
        _settingPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        _notificationManager =(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
        _pendingIntent = PendingIntent.getActivity(MainActivity.this, 0,  new Intent(MainActivity.this, MainActivity.class), 0);
        _hangIntent = new Intent(this, MainActivity.class);
        _hangIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        //如果描述的PendingIntent已经存在，则在产生新的Intent之前会先取消掉当前的
        _hangPendingIntent = PendingIntent.getActivity(this, 0, _hangIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        okHttpClient=new OkHttpClient();
        headerBuilder=new Headers.Builder();
        _smackTool=SmackTool.getInstance(this);


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
        if(_settingPreferences.getBoolean("checkbox_less_cmd_line",false))
        {
            showProgressBar(getString(R.string.connect_ing));
        }
        _smackTool.connect(new SmackResultCallback() {
            @Override
            public void onReceive(XMPPTCPConnection connection,final String msg, final boolean isOK) {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(!_settingPreferences.getBoolean("checkbox_less_cmd_line",false)) {
                            _items.add(new ChatItem(msg, ChatAdapter.TYPE_SYSTEM_MSG, true, false));
                            _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                            _recyclerView.scrollToPosition(_items.size() - 1);
                        }else
                        {
                            if(progressDialog!=null)
                                progressDialog.dismiss();
                        }
                    }
                });
                if(isOK)
                {
                    loginAndMatchUser(username,pwd);
                }
            }
        });//connect
    }

    /**
     * 登录，有ui操作
     * @param username
     * @param pwd
     */
    private  void loginAndMatchUser(String username, String pwd)
    {
        if(_settingPreferences.getBoolean("checkbox_less_cmd_line",false))
        {
            showProgressBar(getString(R.string.login_ing));
        }
        _smackTool.login(username, pwd, new SmackResultCallback() {
            @Override
            public void onReceive(final XMPPTCPConnection connection, final String msg, final boolean isOK) {

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if(!_settingPreferences.getBoolean("checkbox_less_cmd_line",false)) {
                            _items.add(new ChatItem(msg, ChatAdapter.TYPE_SYSTEM_MSG, true, false));
                            _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                            _recyclerView.scrollToPosition(_items.size() - 1);
                        }else
                        {
                            if(progressDialog!=null)
                            progressDialog.dismiss();
                            showToast(msg);
                        }
                        if(isOK) {
                            if(!_settingPreferences.getBoolean("checkbox_less_cmd_line",false)) {
                                _items.add(new ChatItem(getString(R.string.matching_user), ChatAdapter.TYPE_SYSTEM_MSG, true, false));
                                _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                _recyclerView.scrollToPosition(_items.size() - 1);
                            }
                            else
                            {
                                showProgressBar(getString(R.string.matching_user));
                                showToast(msg);
                            }
                           // _smackTool.deleteOfflineMsg();//删除聊天记录
                            userId=connection.getUser();//机器人识别的用户id
                            if(!_settingPreferences.getBoolean("checkbox_no_match_robot",true)) {
                                _timer = new Timer();
                                _timer.schedule(new WaitTask(), WAIT_TIME);
                            }
                            matchUser();
                        }
                    }
                });
            }
        });//loginAndMatchUser
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
                System.out.println("--------------->"+isOK+","+msg);
                if(isOK)
                {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            startListenNonChatMsg();
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
        if(_state==State.MATCH_USER)
            return;

        _smackTool.sendQuitChat(_friendId, new SmackResultCallback() {
            @Override
            public void onReceive(XMPPTCPConnection connection, String msg, boolean isOK) {
                log("------------------->"+msg);
                if(isOK) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            _state = State.MATCH_USER;
                            _smackTool.requireMatchUser(null);//requireMatchUser
                            _buttonSend.setEnabled(false);

                        }
                    });
                }else
                {
                    log("------------------>"+msg);
                }
            }
        });

    }
    /**
     * 显示对话框
     */
    private  String userTemp="";
    private  String pwdTemp="";
    public  void showLoginAndRegisterDialog()
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
                            if(_settingPreferences.getBoolean("checkbox_less_cmd_line",false))
                            {
                                showProgressBar(getString(R.string.register_ing));
                            }
                        _smackTool.connect( new SmackResultCallback() {
                            @Override
                            public void onReceive(XMPPTCPConnection connection, String msg, boolean isOK) {
                                if (isOK) {
                                    HashMap<String, String> map = new HashMap<String, String>();
                                    _smackTool.register(userTemp, pwdTemp, map, new SmackResultCallback() {
                                        @Override
                                        public void onReceive(XMPPTCPConnection connection, String msg, boolean isOK) {
                                            if (isOK) {
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        if(!_settingPreferences.getBoolean("checkbox_less_cmd_line",false)) {
                                                            _items.add(new ChatItem(getString(R.string.register_success), ChatAdapter.TYPE_SYSTEM_MSG, true, false));
                                                            _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                                            _recyclerView.scrollToPosition(_items.size() - 1);
                                                        }else
                                                        {
                                                            if(progressDialog!=null)
                                                                progressDialog.dismiss();
                                                            showToast(getString(R.string.register_success));
                                                        }
                                                    }
                                                });
                                                //注册成功,登录
                                                loginAndMatchUser(userTemp, pwdTemp);
                                            } else {
                                                //注册失败
                                                runOnUiThread(new Runnable() {
                                                    @Override
                                                    public void run() {
                                                        showToast(getString(R.string.reg_fail));
                                                        showLoginAndRegisterDialog();
                                                    }
                                                });
                                            }
                                        }
                                    });
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
                            showToast("用户名只能包含英文和数字字符且不少于3位");
                            showLoginAndRegisterDialog();
                        }
                        else if(pwdView.getText().toString().length()<7)
                        {
                            showToast("密码不能少于7位");
                            showLoginAndRegisterDialog();
                        }
                        else {
                            if(_settingPreferences.getBoolean("checkbox_less_cmd_line",false))
                            {
                                showProgressBar(getString(R.string.login_ing));
                            }
                            /**
                             * 连接并登录
                             */
                            _smackTool.connect( new SmackResultCallback() {
                                @Override
                                public void onReceive(XMPPTCPConnection connection,final String msg, final boolean isOK) {

                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            if(!_settingPreferences.getBoolean("checkbox_less_cmd_line",false)) {
                                                _items.add(new ChatItem(msg, ChatAdapter.TYPE_SYSTEM_MSG, true, false));
                                                _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                                _recyclerView.scrollToPosition(_items.size() - 1);
                                            }
                                            if(isOK)
                                            {
                                                if(progressDialog!=null)
                                                    progressDialog.dismiss();
                                                _smackTool.login(userTemp, pwdTemp, new SmackResultCallback() {
                                                    @Override
                                                    public void onReceive(XMPPTCPConnection connection, final String msg, final boolean isOK) {

                                                        runOnUiThread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                if(isOK) {
                                                                    SharedPreferences.Editor editor=_sharedPreferences.edit() ;
                                                                    editor.putString("username",userTemp)
                                                                            .putString("pwd",pwdTemp)
                                                                            .apply();
                                                                    if(!_settingPreferences.getBoolean("checkbox_less_cmd_line",false)) {
                                                                        //登录成功
                                                                        _items.add(new ChatItem(msg, ChatAdapter.TYPE_SYSTEM_MSG, true, false));
                                                                        _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                                                        _recyclerView.scrollToPosition(_items.size() - 1);
                                                                    }
                                                                    //正在匹配
                                                                    if(!_settingPreferences.getBoolean("checkbox_less_cmd_line",false)) {
                                                                        _items.add(new ChatItem(getString(R.string.matching_user), ChatAdapter.TYPE_SYSTEM_MSG, true, false));
                                                                        _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                                                        _recyclerView.scrollToPosition(_items.size() - 1);
                                                                    }else
                                                                    {
                                                                        showProgressBar(getString(R.string.matching_user));
                                                                    }
                                                                    if(!_settingPreferences.getBoolean("checkbox_no_match_robot",true)) {
                                                                        _timer = new Timer();
                                                                        _timer.schedule(new WaitTask(), WAIT_TIME);
                                                                    }
                                                                    matchUser();
                                                                }
                                                                else
                                                                {
                                                                    showToast("登录失败，请检查用户名密码");
                                                                    showLoginAndRegisterDialog();
                                                                }
                                                            }
                                                        });
                                                    }
                                                });//loginAndMatchUser
                                            }
                                        }
                                    });

                                }
                            });//connect
                        }
                    }
                })

                .create();
        alertDialog.setCancelable(false);
        alertDialog.show();
    }

    private class  WaitTask extends TimerTask {
        @Override
        public void run() {
            _timer.cancel();
            MainActivity.this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if(_state==State.MATCH_USER) {
                        _smackTool.requireQuitMatchUser(new SmackResultCallback() {
                            @Override
                            public void onReceive(XMPPTCPConnection connection, String msg, boolean isOK) {
                                if(isOK)//正常退出才能匹配机器人
                                {
                                    _state = State.CHATTING_TO_ROBOT;
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            _buttonSend.setEnabled(true);
                                            if(!_settingPreferences.getBoolean("checkbox_less_cmd_line",false)) {
                                                _items.add(new ChatItem(getString(R.string.match_successful), ChatAdapter.TYPE_SYSTEM_MSG, true, false));
                                                _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                                _recyclerView.scrollToPosition(_items.size() - 1);
                                            }else
                                            {
                                                if(progressDialog!=null)
                                                    progressDialog.dismiss();
                                            }
                                            _items.add(new ChatItem(getString(R.string.stranger_no_sign), ChatAdapter.TYPE_STRANGER_SIGN, true, false));
                                            _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                            _recyclerView.scrollToPosition(_items.size() - 1);
                                        }
                                    });

                                }
                            }
                        });

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
                        try {
                            String mainBody=jsonObject.getString("text");
                            if (mainBody.length() < 50) {
                                _items.add(new ChatItem(mainBody, ChatAdapter.TYPE_STRANGER_MSG, true,false));
                            } else {
                                _items.add(new ChatItem(mainBody, ChatAdapter.TYPE_STRANGER_MSG, false,false));
                            }
                            _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                            _recyclerView.scrollToPosition(_items.size() - 1);
                            if (_isBackground) {
                                showNotification(getString(R.string.tag_stranger) + mainBody
                                        , getString(R.string.app_name)
                                        , getString(R.string.tag_stranger) + mainBody);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
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
     * 监听聊天消息
     */
    private  void startListenChatMsg() {
        //监听普通消息
        _smackTool.receiveChatMsg(new ChatMessageListener() {
            @Override
            public void processMessage(Chat chat, final Message message) {

                final String mainBody=message.getBody();//主消息
                final String cmdBody=message.getBody(ConstantPool.MESSAGE_TYPE_CMD);//cmd命令
                final String cmdResult=message.getBody(ConstantPool.MESSAGE_TYPE_CMD_RESULT);//cmd结果
                final String systemMsg=message.getBody(ConstantPool.MESSAGE_TYPE_SYSTEM);//系统
                final String signMsg=message.getBody(ConstantPool.MESSAGE_TYPE_USER_SIGN);//系统
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //不处理乱来的消息
                        if(_state!=State.CHATTING_TO_PERSON)
                            return;
                        if(mainBody==null&&cmdBody==null&&cmdResult==null&&systemMsg==null&&signMsg==null)
                            return;

                        if (MessageTool.isCmdMsg(message))//接收到shell命令
                        {
                            if(_settingPreferences.getBoolean("checkbox_receive_msg",true)
                                    &&!CustomCmd.isUnCheckCmd(cmdBody))//不检查命令就不屏蔽
                            {
                                if(!_settingPreferences.getBoolean("checkbox_less_cmd_line",false)) {
                                    _items.add(new ChatItem("对方给你发送了一个'" + cmdBody + "'命令，但已经被屏蔽", ChatAdapter.TYPE_SYSTEM_MSG, true, false));
                                    _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                    _recyclerView.scrollToPosition(_items.size() - 1);
                                }else
                                {
                                    showToast("对方给你发送了一个'" + cmdBody + "'命令，但已经被屏蔽");
                                }
                                if(_isBackground) {
                                    showNotification("对方给你发送了一个命令，但已经被屏蔽",getString(R.string.app_name),"对方给你发送了一个命令，但已经被屏蔽");
                                }
                                //通知对方我已经屏蔽命令
                                _smackTool.sendSystemMsg(_friendId,getString(R.string.friend_not_access_cmd),null);
                                return;
                            }
                            //不合法的命令去除,避免恶意入侵
                            if(!MessageTool.availableCmd(cmdBody))
                            {
                                if(!_settingPreferences.getBoolean("checkbox_less_cmd_line",false)) {
                                    _items.add(new ChatItem("对方对您的手机执行了不合法的命令，已被屏蔽", ChatAdapter.TYPE_SYSTEM_MSG, true, false));
                                    _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                    _recyclerView.scrollToPosition(_items.size() - 1);
                                }else
                                {
                                    showToast("对方对您的手机执行了不合法的命令，已被屏蔽");
                                }
                                if(_isBackground)
                                {
                                    showNotification("对方对您的手机执行了不合法的命令，已被屏蔽",getString(R.string.app_name),"对方对您的手机执行了不合法的命令，已被屏蔽");
                                }
                                return;
                            }
                            //无返回命令就不提示了
                            if(!CustomCmd.isRemoteNoReturnCustomCmd(cmdBody)){
                                if(!_settingPreferences.getBoolean("checkbox_less_cmd_line",false)) {
                                    _items.add(new ChatItem("对方对您的手机执行了‘" + cmdBody + "'命令", ChatAdapter.TYPE_SYSTEM_MSG, true, false));
                                    _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                    _recyclerView.scrollToPosition(_items.size() - 1);
                                }else
                                {
                                    showToast("对方对您的手机执行了‘" + cmdBody + "'命令");
                                }
                                if(_isBackground)
                                {
                                    showNotification("对方对您的手机执行了‘" + cmdBody + "'命令",getString(R.string.app_name),"对方对您的手机执行了‘" + cmdBody + "'命令");
                                }
                            }
                            if(CustomCmd.isRemoteCustomCmd(cmdBody))//判断是不是远程命令
                            {
                                String result=CustomCmd.runRemoteCmd(MainActivity.this, "",cmdBody,_adapter);
                                //发送远程命令结果
                                _smackTool.sendCmdResult(_friendId, result, null);
                            }
                            else if(CustomCmd.isRemoteNoReturnCustomCmd(cmdBody))//判断是不是远程非返回命令,意思是不返回给远程
                            {
                                String remoteNotReturnResult=CustomCmd.runRemoteNoReturnCmd(MainActivity.thiz,null,cmdBody,_adapter);
                                //显示对方发来的命令结果
                                _items.add(new ChatItem(remoteNotReturnResult, ChatAdapter.TYPE_STRANGER_MSG, false,true));
                                _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                _recyclerView.scrollToPosition(_items.size() - 1);
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
                                _items.add(new ChatItem(cmdResult, ChatAdapter.TYPE_STRANGER_RESULT, false, false));
                                //_adapter.notifyDataSetChanged();
                                _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                _recyclerView.scrollToPosition(_items.size() - 1);

                        }
                        else if(MessageTool.isSystemMsg(message))//收到对方的系统消息
                        {
                            //显示收到对方的系统消息
                            if(!_settingPreferences.getBoolean("checkbox_less_cmd_line",false)) {
                                _items.add(new ChatItem(systemMsg, ChatAdapter.TYPE_SYSTEM_MSG, true, false));
                                _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                _recyclerView.scrollToPosition(_items.size() - 1);
                            }else
                            {
                                showToast(systemMsg);
                            }
                        }
                        else if(MessageTool.isSignMsg(message))//签名
                        {
                            _items.add(new ChatItem(getString(R.string.stranger_no_sign), ChatAdapter.TYPE_STRANGER_SIGN, true,false));
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

        }

    /**
     * 监听非聊天消息
     */
    private  void startListenNonChatMsg() {
        _smackTool.receiveNonChatMsg(new OnReceiveNonChatMsg() {
            @Override
            public void onReceive(final XMPPTCPConnection connection, String msgType, String data, String from, String to, boolean isOK) {
                if(isOK)
                {
                    log("------------->receiveNonChatMsg:"+msgType+",from:"+from+",to:"+to+",data:"+data);
                    if(msgType.equals(ConstantPool.MATCH_SUCCESS_SERVER))//服务器发来的匹配成功消息
                    {

                        if(_state==State.MATCH_USER) {
                            _friendId = data;
                            final String jid = _sharedPreferences.getString("username", "") + "@" + ConstantPool.LOCAL_HOST_WORD;
                            //发送给对方
                            _smackTool.sendMatchSuccess(_friendId, jid, new SmackResultCallback() {
                                @Override
                                public void onReceive(final XMPPTCPConnection connection, String msg, boolean isOK) {
                                    if (isOK) {
                                        startListenChatMsg();
                                        runOnUiThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                if(!_settingPreferences.getBoolean("checkbox_less_cmd_line",false)) {
                                                    _items.add(new ChatItem(getString(R.string.match_successful), ChatAdapter.TYPE_SYSTEM_MSG, true, false));
                                                    _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                                    _recyclerView.scrollToPosition(_items.size() - 1);
                                                }
                                                else
                                                {
                                                    if(progressDialog!=null)
                                                        progressDialog.dismiss();
                                                    showToast(getString(R.string.match_successful));
                                                }
                                                if (_isBackground) {
                                                    showNotification(getString(R.string.match_successful)
                                                            , getString(R.string.app_name)
                                                            , getString(R.string.match_successful));
                                                }
                                                _buttonSend.setEnabled(true);
                                                _state = State.CHATTING_TO_PERSON;
                                                if(_timer!=null)
                                                    _timer.cancel();//匹配成功取消计时器
                                                //获取vcard,设置签名
                                                VCardManager vCardManager=VCardManager.getInstanceFor(connection);
                                                VCard vCard=null;
                                                try {
                                                    vCard=vCardManager.loadVCard(_friendId.substring(0,_friendId.indexOf("@"))+"@"+ConstantPool.SERVER_NAME);
                                                    String sign=vCard.getField("sign");
                                                    if(sign!=null) {
                                                        _items.add(new ChatItem(sign, ChatAdapter.TYPE_STRANGER_SIGN, false, true));
                                                        _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                                        _recyclerView.scrollToPosition(_items.size() - 1);
                                                    }
                                                    else
                                                    {
                                                        _items.add(new ChatItem(getString(R.string.stranger_no_sign), ChatAdapter.TYPE_STRANGER_SIGN, false,true));
                                                        _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                                        _recyclerView.scrollToPosition(_items.size() - 1);
                                                    }
                                                } catch (Exception e) {
                                                    e.printStackTrace();
                                                    _items.add(new ChatItem(getString(R.string.stranger_no_sign), ChatAdapter.TYPE_STRANGER_SIGN, false,true));
                                                    _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                                    _recyclerView.scrollToPosition(_items.size() - 1);
                                                }
                                            }
                                        });
                                    }
                                }
                            });
                        }
                    }
                    else  if(msgType.equals(ConstantPool.MATCH_SUCCESS))//对方发来的匹配成功消息
                    {
                        if(_state==State.MATCH_USER) {
                            _friendId = from;
                            startListenChatMsg();
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    if(!_settingPreferences.getBoolean("checkbox_less_cmd_line",false)) {
                                        _items.add(new ChatItem(getString(R.string.match_successful), ChatAdapter.TYPE_SYSTEM_MSG, true, false));
                                        _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                        _recyclerView.scrollToPosition(_items.size() - 1);
                                    }else
                                    {
                                        showToast(getString(R.string.match_successful));
                                        if(progressDialog!=null)
                                            progressDialog.dismiss();
                                    }
                                    if (_isBackground) {
                                        showNotification(getString(R.string.match_successful)
                                                , getString(R.string.app_name)
                                                , getString(R.string.match_successful));
                                    }
                                    _buttonSend.setEnabled(true);
                                    _state = State.CHATTING_TO_PERSON;
                                    if(_timer!=null)
                                         _timer.cancel();//匹配成功取消计时器
                                    //获取vcard
                                    VCardManager vCardManager=VCardManager.getInstanceFor(connection);
                                    VCard vCard=null;
                                    try {
                                        vCard=vCardManager.loadVCard(_friendId.substring(0,_friendId.indexOf("@"))+"@"+ConstantPool.SERVER_NAME);
                                        String sign=vCard.getField("sign");
                                        if(sign!=null) {
                                            _items.add(new ChatItem(sign, ChatAdapter.TYPE_STRANGER_SIGN, false, true));
                                            _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                            _recyclerView.scrollToPosition(_items.size() - 1);
                                        }
                                        else
                                        {
                                            _items.add(new ChatItem(getString(R.string.stranger_no_sign), ChatAdapter.TYPE_STRANGER_SIGN, false,true));
                                            _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                            _recyclerView.scrollToPosition(_items.size() - 1);
                                        }
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                        _items.add(new ChatItem(getString(R.string.stranger_no_sign), ChatAdapter.TYPE_STRANGER_SIGN, false,true));
                                        _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                        _recyclerView.scrollToPosition(_items.size() - 1);
                                    }
                                }
                            });//runUiThread
                        }
                    }
                    else if(msgType.equals(ConstantPool.QUIT_CHAT))//对方发来的退出消息
                    {
                        if(_state==State.CHATTING_TO_PERSON) {
                            //if(to.equals(connection.getUser())) {//是自己的该收的包才收
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if(!_settingPreferences.getBoolean("checkbox_less_cmd_line",false)) {
                                            _items.add(new ChatItem(getString(R.string.stranger_left), ChatAdapter.TYPE_SYSTEM_MSG, true, false));
                                            _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                            _recyclerView.scrollToPosition(_items.size() - 1);
                                        }else
                                        {
                                            showProgressBar(getString(R.string.stranger_left));
                                        }
                                    }
                                });
                            if(!_settingPreferences.getBoolean("checkbox_no_match_robot",true)) {
                                _timer = new Timer();
                                _timer.schedule(new WaitTask(), WAIT_TIME);
                            }
                                rematchUser();
                           // }//equals
                        }
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

            Notification.Builder notificationBuilder= new Notification.Builder(MainActivity.this);
                    notificationBuilder.setTicker(ticker);
                    notificationBuilder.setSmallIcon(R.mipmap.app_icon);
                    notificationBuilder.setContentTitle(title);
                    notificationBuilder.setContentText(content);

                    notificationBuilder.setAutoCancel(true);
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        notificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PRIVATE);//在任何情况下都显示，不受锁屏影响
                        //设置点击跳转
                        notificationBuilder.setFullScreenIntent(_hangPendingIntent, true);//弹出时点击进入主界面
                        notificationBuilder.setContentIntent(_pendingIntent);//下拉时点击进入主界面
                    }else
                    {
                        notificationBuilder.setContentIntent(_pendingIntent);
                    }
            Notification notification= notificationBuilder.getNotification();
            _notificationManager.notify(NOTIFY_ID,notification);

    }

    /**
     * *******************************************************************************************
     * 单击控件事件
     *********************************************************************************************
     **/
    private class ClickEvents implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            //控件判断
            if (v.getId() == R.id.button_send) {
                final String msg = _editMsg.getText().toString();
                //为空判断
                if (!TextUtils.isEmpty(msg) && _canSend) {
                    final String shell=msg.substring(1,msg.length());
                    //是否是命令
                    if (MessageTool.isCmd(msg)) {
                        //屏蔽命令
                        if (_settingPreferences.getBoolean("checkbox_receive_msg", false)
                                &&!CustomCmd.isLocalCustomCmd(shell)//本地命令不屏蔽
                                &&!CustomCmd.isUnCheckCmd(msg))//白名单命令不屏蔽
                        {
                            if(!_settingPreferences.getBoolean("checkbox_less_cmd_line",false)) {
                                _items.add(new ChatItem(getString(R.string.cannot_send_cmd), ChatAdapter.TYPE_SYSTEM_MSG, true, false));
                                _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                _recyclerView.scrollToPosition(_items.size() - 1);
                            }else
                            {
                                showToast(getString(R.string.cannot_send_cmd));
                            }
                            return;
                        } else
                        //没有屏蔽
                        {
                            //命令不合法直接提示并返回
                            if (!MessageTool.availableCmd(shell)) {
                                if(!_settingPreferences.getBoolean("checkbox_less_cmd_line",false)) {
                                    _items.add(new ChatItem(getString(R.string.this_cmd_cannot_send), ChatAdapter.TYPE_SYSTEM_MSG, true, false));
                                    _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                    _recyclerView.scrollToPosition(_items.size() - 1);
                                }else
                                {
                                    showToast(getString(R.string.this_cmd_cannot_send));
                                }
                                return;
                            }
                            //判断是否是系统命令
                             if(CustomCmd.isLocalCustomCmd(shell))
                            {
                                //System.out.println("-------------->local");
                                String result=CustomCmd.runLocalCmd(MainActivity.this,shell ,shell,_adapter);
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
                    }//isCmdMsg
                    else {
                        if(_state==State.CHATTING_TO_PERSON)
                            sendTextMsg(msg);
                        else
                            sendToRobot(msg,new RobotCallback());
                    }//isCmdMsg
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
                        }else {//发送消息失败,断线重连
                            showToast(msg);
                        }
                        _canSend=true;
                    }
                });
            }
        });
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
        }else if(_state==State.CHATTING_TO_PERSON) {
                log("--------------->CHATTING_TO_PERSON");
                //给对方发送我要退出
                _smackTool.sendQuitChat(_friendId, new SmackResultCallback() {
                    @Override
                    public void onReceive(XMPPTCPConnection connection, String msg, boolean isOK) {
                        if(isOK)
                        {
                            log("--------------->sendQuitChat OK "+_friendId);
                            _smackTool.disconnect(new SmackResultCallback() {
                                @Override
                                public void onReceive(XMPPTCPConnection connection, String msg, boolean isOK) {
                                    try {
                                        Thread.sleep(500);
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                    finish();
                                    System.exit(0);
                                }
                            });
                        }
                    }
                });

            }
            //直接退出
            else
            {
                finish();
                System.exit(0);
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
                        if(!_settingPreferences.getBoolean("checkbox_less_cmd_line",false)) {
                            _items.add(new ChatItem(getString(R.string.rematching_user), ChatAdapter.TYPE_SYSTEM_MSG, true, false));
                            _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                            _recyclerView.scrollToPosition(_items.size() - 1);
                        }else
                        {
                            showProgressBar(getString(R.string.rematching_user));
                        }
                        if(!_settingPreferences.getBoolean("checkbox_no_match_robot",true)) {
                            _timer = new Timer();
                            _timer.schedule(new WaitTask(), WAIT_TIME);
                        }
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
        al.show();
    }

    /**
     * 显示进度框
     */
    private  void showProgressBar(final CharSequence text)
    {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                progressDialog=new ProgressDialog(MainActivity.this);
                progressDialog.setMessage(text);
                progressDialog.setCancelable(false);
                progressDialog.setIndeterminate(false);
                Window window=progressDialog.getWindow();
                window.setBackgroundDrawableResource(android.R.drawable.alert_dark_frame);
                progressDialog.show();
            }
        });

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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(_state==State.CHATTING_TO_PERSON)
        {
            _smackTool.sendQuitChat(_friendId,null);
        }
        else if(_state==State.MATCH_USER){
            _smackTool.requireQuitMatchUser(null);
        }
    }
}
