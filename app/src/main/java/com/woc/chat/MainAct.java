package com.woc.chat;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.woc.chat.activity.PreviewMdAct;
import com.woc.chat.activity.SettingActivity;
import com.woc.chat.entity.ChatMsgPool;
import com.woc.chat.entity.User;
import com.woc.chat.parser.MsgParser;
import com.woc.chat.parser.UserParser;
import com.woc.chat.util.CustomCmd;
import com.woc.chat.util.IO;
import com.woc.chat.util.ShellUtils;
import com.woc.chat.util.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import cn.bmob.v3.BmobQuery;
import cn.bmob.v3.BmobRealTimeData;
import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.FindListener;
import cn.bmob.v3.listener.SaveListener;
import cn.bmob.v3.listener.UpdateListener;
import cn.bmob.v3.listener.ValueEventListener;
import  com.woc.chat.terminal.*;

public class MainAct extends AppCompatActivity {

    private User _curUser;
    private String _friendName;
    private String _friendId;
    private SharedPreferences _sharedPreferences;
    public final static String MSG_TABLE = "ChatMsgPool";
    public final static String USER_TABLE = "User";
    public static Activity thiz;
    //避免无限递归
    public boolean _isMatchUser = true;
    private State _state;
    private boolean _canSend = true;//标记是否能发送
    private final String CMD_PREFIX = "$";
    private final String CMD_SYS_PREFIX = "--";
    private final String CMD_RESULT_PREFIX = "++";
    private  ClipboardManager _clipboard;
    private String[] _unusableCmd ={"reboot","su","rm","vi"};
    private SharedPreferences _settingPreferences;
    private  boolean _isBackground =false;
    private  PendingIntent _pendingIntent;
    private TerminalView _terminalView;
    private final int  NOTIFY_ID=3574;
    private final int SIGN_COLOR=0xffff69b4;
    public enum State {
        MATCH_USER, CHATTING
    }
    private  final String SYSTEM_PREFIX="system>";
    private  final String USER_PREFIX="user>";
    private final String ROOT_PREFIX="root>";
    private NotificationManager _notificationManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.act_main);
        thiz = this;
        _terminalView = (TerminalView) findViewById(R.id.terminalView);
        _terminalView.setOnSubmitListener(new SubmitEvents());
        _sharedPreferences = getSharedPreferences("user", Context.MODE_PRIVATE);
        _clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);

        _settingPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        _notificationManager =(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
         _pendingIntent = PendingIntent.getActivity(MainAct.this, 0,  new Intent(MainAct.this, MainAct.class), 0);
        login();
        startReceiveMsg();
        startListenUserState();
    }

    /**
     * 开始监听user表的变化
     */
    private synchronized void startListenUserState()
    {
        final BmobRealTimeData rtd = new BmobRealTimeData();
        rtd.start(new ValueEventListener() {
            @Override
            public void onConnectCompleted(Exception e) {
                if (rtd.isConnected()) {
                    // 监听表更新
                    rtd.subTableUpdate(USER_TABLE);
                }
            }

            @Override
            public void onDataChange(JSONObject jsonObject) {
                try {
                    UserParser userParser=new UserParser(jsonObject);
                    String tempName=userParser.getData().getUsername();
                    if(tempName==null)
                        return;
                    //看对方是否处于离线状态
                    if(userParser.getData().getIsOnline()==false&&tempName.equals(_friendName)) {
                        _terminalView.setPrefix(SYSTEM_PREFIX);
                        _terminalView.postColoredLine(getString(R.string.stranger_left), Color.RED,true,true);
                        _terminalView.setPrefix(ROOT_PREFIX);
                        //_terminalView.finish();
                        if(_isBackground) {
                            showNotification(getString(R.string.stranger_left),getString(R.string.app_name),getString(R.string.stranger_left));
                        }
                        _friendName ="";

                        //更新自己的状态为在线
                        _curUser.setOnline(true);
                        _curUser.setObjectId(_sharedPreferences.getString("objectId", ""));
                        _curUser.update(new UpdateListener() {
                            @Override
                            public void done(BmobException e) {
                                if(e==null)
                                {
                                    getRandomUser();
                                }
                            }
                        });

                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 监听消息表的变化
     */
    private synchronized void startReceiveMsg() {
        final BmobRealTimeData rtd = new BmobRealTimeData();
        rtd.start(new ValueEventListener() {
            @Override
            public void onConnectCompleted(Exception e) {
                if (rtd.isConnected()) {
                    // 监听表更新
                    rtd.subTableUpdate(MSG_TABLE);
                }
            }

            /**
             * ChatMsgPool表变化发生
             * @param jsonObject
             */
            @Override
            public void onDataChange(JSONObject jsonObject) {
                //如果在匹配中，就不接收消息了
                if(_state == State.MATCH_USER)
                    return;
                try {
                    MsgParser parser = new MsgParser(jsonObject);
                    String msg = parser.getData().getMsg();
                    if (parser.getData().getMsgTo().equals(_curUser.getUsername()))//消息是发给自己的才接受
                    {

                        if (msg.startsWith(CMD_PREFIX))//接收到shell命令
                        {
                            if(_settingPreferences.getBoolean("checkbox_receive_msg",false))
                            {
                                _terminalView.setPrefix(SYSTEM_PREFIX);
                                _terminalView.postColoredLine(getString(R.string.recv_cmd_but_shield),Color.RED,true,true);
                                _terminalView.setPrefix(ROOT_PREFIX);
                                //_terminalView.finish();
                                if(_isBackground) {
                                    showNotification(getString(R.string.recv_cmd_but_shield),getString(R.string.app_name),getString(R.string.recv_cmd_but_shield));
                                }
                                return;
                            }
                            String shell = msg.substring(1, msg.length());

                            //不合法的命令去除,要传进去$
                            if(!availableCmd(msg))
                            {
                                _terminalView.setPrefix(SYSTEM_PREFIX);
                                _terminalView.postColoredLine("",Color.RED,true,true);
                                _terminalView.setPrefix(ROOT_PREFIX);
                               // _terminalView.finish();
                                if(_isBackground)
                                {
                                    showNotification(getString(R.string.recv_invalid_cmd),getString(R.string.app_name),getString(R.string.recv_invalid_cmd));
                                }
                                return;
                            }
                            _terminalView.setPrefix(SYSTEM_PREFIX);
                            _terminalView.postColoredLine("对方对您的手机执行了‘" + shell + "'命令",Color.RED,true,true);
                            _terminalView.setPrefix(ROOT_PREFIX);
                            if(_isBackground)
                            {
                                showNotification("对方对您的手机执行了‘" + shell + "'命令",getString(R.string.app_name),"对方对您的手机执行了‘" + shell + "'命令");
                            }
                            if(CustomCmd.isRemoteCustomCmd(shell))//判断是不是本地命令
                            {
                                String result=CustomCmd.runRemoteCmd(MainAct.this, _curUser,shell);
                                ChatMsgPool msgPool = new ChatMsgPool();
                                msgPool.setMsg(CMD_RESULT_PREFIX + result);
                                msgPool.setMsgFrom(_curUser.getUsername());
                                msgPool.setMsgTo(_friendName);
                                msgPool.save(new SaveListener<String>() {
                                    @Override
                                    public void done(String s, BmobException e) {
                                        if (e == null) {
                                            System.out.println("命令发送成功！");
                                        } else {
                                            System.out.println("命令发送失败！" + e.toString());
                                        }
                                    }
                                });
                            }
                            //这里不会出现localMsg
                            else
                            {
                                final ShellUtils.CommandResult result = ShellUtils.execCommand(shell, false);

                                if (!result.successMsg.equals("")) {
                                    System.out.println(result.successMsg);
                                    //命令执行成功，给对方发送结果
                                    ChatMsgPool msgPool = new ChatMsgPool();
                                    msgPool.setMsg(CMD_RESULT_PREFIX + result.successMsg);
                                    msgPool.setMsgFrom(_curUser.getUsername());
                                    msgPool.setMsgTo(_friendName);
                                    msgPool.save(new SaveListener<String>() {
                                        @Override
                                        public void done(String s, BmobException e) {
                                            if (e == null) {
                                                System.out.println("命令发送成功！");
                                            } else {
                                                System.out.println("命令发送失败！" + e.toString());
                                            }
                                        }
                                    });
                                }
                            }
                        } else if (msg.startsWith(CMD_RESULT_PREFIX))//接收到命令执行结果的时候，不慢速显示文本
                        {
                            _terminalView.setPrefix(USER_PREFIX);
                            _terminalView.postColoredLine(msg.substring(2, msg.length()),Color.GREEN,false,true);
                            _terminalView.setPrefix(ROOT_PREFIX);

                        } else//纯文本,慢速显示文本
                        {
                            //显示对方发来的文本消息
                            _terminalView.setPrefix(USER_PREFIX);
                            _terminalView.postColoredLine(msg,Color.GREEN,true,true);
                            _terminalView.setPrefix(ROOT_PREFIX);
                            if(_isBackground) {
                                showNotification(getString(R.string.tag_stranger) + msg, getString(R.string.app_name), getString(R.string.tag_stranger) + msg);
                            }
                        }

                    }
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 显示通知
     * @param ticker ticker内容
     * @param title 标题
     * @param content 内容
     */
    private void showNotification(String ticker,String title,String content)
    {
            Notification notification=new Notification.Builder(MainAct.this)
                    .setTicker(ticker)
                    .setSmallIcon(R.mipmap.app_icon)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setContentIntent(_pendingIntent)
                    .setAutoCancel(true)
                    .getNotification();
            _notificationManager.notify(NOTIFY_ID,notification);

    }

    /**
     * 判断命令的合法性,命令包含$
     * @param cmd
     * @return
     */
    public boolean availableCmd(String cmd)
    {
        if(!cmd.startsWith("$"))
        {
            return true;
        }
        String command=cmd.substring(1,cmd.length());
        if(command.matches(".*echo.+>.*/data/data/.+"))//不允许重定向到data
        {
            return false;
        }
        String[] str=command.split("\\s+");
        for(int i = 0; i< _unusableCmd.length; i++)
        {
            if(command.matches("\\s*"+ _unusableCmd[i]+"($|\\s+.*)"))
                return false;
        }
        return true;
    }

    /**
     * 返回一个消息是否是命令
     * @param msg
     * @return
     */
    private boolean isCmd(String msg)
    {
        return  msg.startsWith(CMD_PREFIX);
    }

    /**********************************************************************************************
     * 控件事件
     **********************************************************************************************/
    private class SubmitEvents implements TerminalView.OnSubmitListener {
        @Override
        public void onSubmit(CharSequence text) {
                final String msg = text.toString();

                //为空判断
                if (!TextUtils.isEmpty(msg) && _canSend) {

                    //是否是命令
                    if (isCmd(msg)) {
                        String shell=msg.substring(1,msg.length());
                        //屏蔽命令
                        if (_settingPreferences.getBoolean("checkbox_receive_msg", false)&&!CustomCmd.isLocalCustomCmd(shell))
                        {
                            _terminalView.setPrefix(SYSTEM_PREFIX);
                            _terminalView.postColoredLine(getString(R.string.cannot_send_cmd),Color.RED,true,true);
                            _terminalView.setPrefix(ROOT_PREFIX);
                            return;
                        } else
                        //没有屏蔽
                        {
                            //命令不合法直接提示并返回
                            if (!availableCmd(msg)) {
                                _terminalView.setPrefix(SYSTEM_PREFIX);
                                _terminalView.postColoredLine(getString(R.string.this_cmd_cannot_send),Color.RED,true,true);
                                _terminalView.setPrefix(ROOT_PREFIX);
                                return;
                            }
                            //判断是否是系统命令
                             if(CustomCmd.isLocalCustomCmd(shell))
                            {
                                System.out.println("-------------->local");
                                String result=CustomCmd.runLocalCmd(MainAct.this, _curUser,shell);
                                _terminalView.setPrefix(SYSTEM_PREFIX);
                                _terminalView.postColoredLine(result,Color.GREEN,true,true);
                                _terminalView.setPrefix(ROOT_PREFIX);
                            }else
                             {
                                 sendTextMsg(msg);
                             }
                        }//是否屏蔽命令
                    }//isCmd
                    else {
                        sendTextMsg(msg);
                    }//isCmd
                }//_canSend
            }

    }

    /**
     * 发送文本消息
     * @param msg
     */
    private void sendTextMsg(final String msg)
    {
        _canSend = false;
        ChatMsgPool msgPool = new ChatMsgPool();

        msgPool.setMsg(msg);
        msgPool.setMsgFrom(_curUser.getUsername());
        msgPool.setMsgTo(_friendName);
        msgPool.save(new SaveListener<String>() {
            @Override
            public void done(String s, BmobException e) {
                //成功判断
                if (e == null) {
                    _terminalView.finish();
                }
                _canSend = true;
            }
        });
    }

    /**
     * 返回User表的项目id
     *
     * @return
     */
    private synchronized void getRandomUser() {
        /**
         *查询已抽到自己的人
         */
        BmobQuery<User> queryChatWith = new BmobQuery<User>();
        queryChatWith.addWhereEqualTo("chatWith", _curUser.getUsername());//查询和自己聊天的
        queryChatWith.addWhereEqualTo("isOnline", true);
        queryChatWith.setLimit(1);
        queryChatWith.findObjects(new FindListener<User>() {
            @Override
            public void done(List<User> list, BmobException e) {
                if (e == null) {
                    if (list.size() == 0) {
                        //查询不到有人选上自己，自己去查
                        /**
                         * 查询
                         */
                        BmobQuery<User> queryNoChat = new BmobQuery<User>();
                        //找不在聊天的
                        queryNoChat.addWhereEqualTo("isChat", false);
                        queryNoChat.addWhereEqualTo("isOnline", true);
                        //去除自己
                        queryNoChat.addWhereNotEqualTo("username", _curUser.getUsername());
                        queryNoChat.setLimit(1);
                        //执行查询方法
                        queryNoChat.findObjects(new FindListener<User>() {
                            @Override
                            public void done(List<User> object, BmobException e) {
                                if (e == null) {
                                    _friendName = object.get(0).getUsername();
                                    _friendId = object.get(0).getObjectId();
                                    //把自己的状态设为正在聊天，chatWith设置为它的名字
                                    _curUser.setChat(true);

                                    _curUser.setChatWith(_friendName);
                                    _curUser.update(new UpdateListener() {
                                        @Override
                                        public void done(BmobException e) {
                                            if (e == null) {
                                                //无论有没有用，必须设置回调函数
                                            } else {

                                            }
                                        }
                                    });
                                    //把对方状态设为在聊天
                                    final User friend = new User();
                                    friend.setChat(true);
                                    friend.setObjectId(_friendId);
                                    friend.update(_friendId, new UpdateListener() {
                                        @Override
                                        public void done(BmobException e) {
                                            if (e == null) {
                                                System.out.println("--------------------->1" + _friendName);
                                                _state = State.CHATTING;
                                                _terminalView.setPrefix(SYSTEM_PREFIX);
                                                _terminalView.postColoredLine(getString(R.string.match_successful),Color.RED,true,true);
                                                _terminalView.setPrefix(ROOT_PREFIX);
                                                //查询对方签名
                                                BmobQuery<User> friendQuery=new BmobQuery<User>();
                                                friendQuery.setLimit(1);
                                                friendQuery.addWhereEqualTo("username", _friendName);
                                                friendQuery.findObjects(new FindListener<User>() {
                                                    @Override
                                                    public void done(List<User> list, BmobException e) {
                                                        if(e==null) {
                                                            User newFriend=list.get(0);
                                                            if (newFriend.getSign() == null) {
                                                                _terminalView.setPrefix(SYSTEM_PREFIX);
                                                                _terminalView.postColoredLine(getString(R.string.stranger_no_sign),SIGN_COLOR,true,true);
                                                                _terminalView.setPrefix(ROOT_PREFIX);
                                                            } else {
                                                                _terminalView.setPrefix(SYSTEM_PREFIX);
                                                                _terminalView.postColoredLine(getString(R.string.sign_prefix)+newFriend.getSign(),SIGN_COLOR,true,true);
                                                                _terminalView.setPrefix(ROOT_PREFIX);
                                                            }
                                                        }
                                                    }
                                                });

                                            } else {
                                                _terminalView.setPrefix(SYSTEM_PREFIX);
                                                _terminalView.postColoredLine(_friendId + ":修改用户状态失败,原因：" + e.toString(),Color.RED,true,true);
                                                _terminalView.setPrefix(ROOT_PREFIX);
                                            }
                                        }
                                    });
                                } else {
                                    if (_isMatchUser)
                                        getRandomUser();
                                }
                            }
                        });
                    } else if (list.size() > 0) {
                        //查询到了有人选上自己
                        //更新自己的chatWith字段
                        _state = State.CHATTING;
                        final User friend = list.get(0);
                        _friendId = friend.getObjectId();
                        _friendName = friend.getUsername();
                        _curUser.setChatWith(_friendName);
                        //把自己的状态设为正在聊天
                        _curUser.setChat(true);
                        _curUser.update(new UpdateListener() {
                            @Override
                            public void done(BmobException e) {
                                if (e == null) {
                                    //无论有没有用。这里都要设置
                                } else {

                                }
                            }
                        });
                        //friend.setChatWith();//这个再设置就重复了
                        friend.setChat(true);
                        friend.update(new UpdateListener() {
                            @Override
                            public void done(BmobException e) {
                                if (e == null) {
                                    System.out.println("--------------------->2" + _friendName);
                                    _state = State.CHATTING;
                                    _terminalView.setPrefix(SYSTEM_PREFIX);
                                    _terminalView.postColoredLine(getString(R.string.match_successful),Color.RED,true,true);
                                    _terminalView.setPrefix(ROOT_PREFIX);
                                    //设置签名
                                    if(friend.getSign()==null) {
                                        _terminalView.setPrefix(SYSTEM_PREFIX);
                                        _terminalView.postColoredLine(getString(R.string.stranger_no_sign),SIGN_COLOR,true,true);
                                        _terminalView.setPrefix(ROOT_PREFIX);
                                    }
                                    else
                                    {
                                        _terminalView.setPrefix(SYSTEM_PREFIX);
                                        _terminalView.postColoredLine(getString(R.string.sign_prefix)+friend.getSign(),SIGN_COLOR,true,true);
                                        _terminalView.setPrefix(ROOT_PREFIX);
                                    }
                                } else {
                                    _terminalView.setPrefix(SYSTEM_PREFIX);
                                    _terminalView.postColoredLine(_friendId + ":修改用户状态失败,原因：" + e.toString(),Color.RED,true,false);
                                    _terminalView.setPrefix(ROOT_PREFIX);
                                }
                            }
                        });

                    }
                }//查询和自己聊天的
            }
        });

    }


    /**
     * 登陆
     */
    private synchronized void login() {

        if (!_sharedPreferences.getString("objectId", "").equals("")) {
            // 允许用户使用应用
            _curUser = new User();
            _curUser.setObjectId(_sharedPreferences.getString("objectId", ""));
            _curUser.setUsername(_sharedPreferences.getString("username", ""));
            //设置自己为在线状态
            _curUser.setChat(false);
            _curUser.setOnline(true);
            _curUser.update(new UpdateListener() {
                @Override
                public void done(BmobException e) {
                    if (e == null) {
                        _state = State.MATCH_USER;
                        getRandomUser();
                        _terminalView.setPrefix(SYSTEM_PREFIX);
                        _terminalView.postColoredLine(getString(R.string.login_successful),Color.RED,true,true);
                        _terminalView.postColoredLine(getString(R.string.matching_user),Color.RED,true,true);
                        _terminalView.setPrefix(ROOT_PREFIX);
                    } else {
                        _terminalView.setPrefix(SYSTEM_PREFIX);
                        _terminalView.postColoredLine(getString(R.string.deal_error),Color.RED,true,true);
                        _terminalView.setPrefix(ROOT_PREFIX);
                        regUser();
                    }
                }
            });

        }
        else {
            //缓存用户对象为空时，用户注册
            regUser();
        }

    }

    /**
     * 注册
     */
    private void regUser()
    {
        final String userId = Utils.getKey(8);
        User bu = new User();
        bu.setUsername(userId);
        bu.setPassword(userId);
        bu.setEmail(userId + "@163.com");
        bu.setChat(false);
        bu.setOnline(true);
        bu.save(new SaveListener<String>() {
            @Override
            public void done(String objId, BmobException e) {
                if (e == null) {
                    //写入本地
                    SharedPreferences.Editor editor = _sharedPreferences.edit();
                    editor.putString("objectId", objId);
                    editor.putString("username", userId);
                    editor.commit();
                    _curUser = new User();
                    _curUser.setObjectId(_sharedPreferences.getString("objectId", ""));
                    _curUser.setUsername(_sharedPreferences.getString("username", ""));
                    //设置自己为在线状态
                    _curUser.setOnline(true);
                    _curUser.update();
                    _state = State.MATCH_USER;

                    _terminalView.setPrefix(SYSTEM_PREFIX);
                    _terminalView.postColoredLine(getString(R.string.reg_successful),Color.RED,true,true);
                    _terminalView.setPrefix(SYSTEM_PREFIX);
                    _terminalView.postColoredLine(getString(R.string.matching_user),Color.RED,true,true);
                    _terminalView.setPrefix(ROOT_PREFIX);
                    getRandomUser();

                } else {
                    _terminalView.setPrefix(SYSTEM_PREFIX);
                    _terminalView.postColoredLine(e.toString(),Color.RED,true,true);
                    _terminalView.setPrefix(ROOT_PREFIX);
                }
            }
        });
    }


    /**
     * 退出登陆
     */
    private synchronized void loginOut() {
        if (_curUser == null) {
            finish();
            return;
        }

        //设置自己为不在聊天状态
        _curUser.setChat(false);
        //设置自己为不在线状态
        _curUser.setOnline(false);
        _curUser.setChatWith("");
        _curUser.update(new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if (e == null) {
                    if (_isMatchUser) {
                        System.out.println("设置自己不在线状态成功");
                        _isMatchUser = false;
                        finish();
                        System.exit(0);
                    }
                }
            }
        });

        //设置好友为不在聊天状态
        User friend = new User();
        friend.setChat(false);
        friend.setChatWith("");
        friend.setOnline(false);
        friend.update(_friendId, new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if (e == null) {
                    //showToast("退出成功");
                    finish();
                } else {

                }
            }
        });
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
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
        //帮助
        else if(id==R.id.menu_about)
        {

            Intent intent=new Intent(MainAct.this, PreviewMdAct.class);
            intent.putExtra("title",getString(R.string.menu_title_help));
            intent.putExtra("data", IO.getFromAssets(this,"help.md"));
            startActivity(intent);
        }
        //设置
        else if(id==R.id.menu_setting)
        {
            Intent intent=new Intent(MainAct.this,SettingActivity.class);
            startActivity(intent);
        }
        //重新匹配
        else if(id==R.id.menu_rematch)
        {

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
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.alert_leaving_chat))
                .setNegativeButton(getString(R.string.alert_cancel), null)
                .setPositiveButton(getString(R.string.alert_ok), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        loginOut();
                    }
                })
                .create()
                .show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        _isBackground =true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        _notificationManager.cancel(NOTIFY_ID);
        _isBackground =false;
    }
}
