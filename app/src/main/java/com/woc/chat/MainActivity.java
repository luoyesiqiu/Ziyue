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
import android.preference.PreferenceManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.woc.chat.activity.PreviewMdAct;
import com.woc.chat.adapter.ChatAdapter;
import com.woc.chat.entity.ChatItem;
import com.woc.chat.entity.ChatMsgPool;
import com.woc.chat.entity.User;
import com.woc.chat.activity.SettingActivity;
import com.woc.chat.parser.MsgParser;
import com.woc.chat.parser.UserParser;
import com.woc.chat.util.CustomCmd;
import com.woc.chat.util.ShellUtils;
import com.woc.chat.util.IO;
import com.woc.chat.util.Utils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.bmob.v3.BmobQuery;
import cn.bmob.v3.BmobRealTimeData;
import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.FindListener;
import cn.bmob.v3.listener.SaveListener;
import cn.bmob.v3.listener.UpdateListener;
import cn.bmob.v3.listener.ValueEventListener;

public class MainActivity extends AppCompatActivity {

    private RecyclerView _recyclerView;
    private ChatAdapter _adapter;
    private List<ChatItem> _items;
    private User _curUser;
    private String _friendName;
    private String _friendId;
    private EditText _editMsg;
    private Button _buttonSend;
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
    public enum State {
        MATCH_USER, CHATTING
    }
    private NotificationManager _notificationManager;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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
                _items.add(new ChatItem("文本已复制到剪贴板", ChatAdapter.TYPE_SYSTEM_MSG, true));
                _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                _recyclerView.scrollToPosition(_items.size() - 1);
            }
        });
        _settingPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        _notificationManager =(NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
         _pendingIntent = PendingIntent.getActivity(MainActivity.this, 0,  new Intent(MainActivity.this, MainActivity.class), 0);
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
                        _items.add(new ChatItem("陌生人已离开，正在重新匹配", ChatAdapter.TYPE_SYSTEM_MSG, false));
                        _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                        if(_isBackground) {
                            showNotification("陌生人已离开，正在重新匹配",getString(R.string.app_name),"陌生人已离开，正在重新匹配");
                        }
                        _recyclerView.scrollToPosition(_items.size() - 1);
                        _friendName ="";
                        _buttonSend.setEnabled(false);

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
                if(_state ==State.MATCH_USER)
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
                                _items.add(new ChatItem("对方给你发送了一个命令，但已经被屏蔽", ChatAdapter.TYPE_SYSTEM_MSG, true));
                                _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                _recyclerView.scrollToPosition(_items.size() - 1);
                                if(_isBackground) {
                                    showNotification("对方给你发送了一个命令，但已经被屏蔽",getString(R.string.app_name),"对方给你发送了一个命令，但已经被屏蔽");
                                }
                                return;
                            }
                            String shell = msg.substring(1, msg.length());

                            //不合法的命令去除,要传进去$
                            if(!availableCmd(msg))
                            {
                                _items.add(new ChatItem("对方对您的手机执行了不合法的命令，已被屏蔽", ChatAdapter.TYPE_SYSTEM_MSG, true));
                                _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                _recyclerView.scrollToPosition(_items.size() - 1);
                                if(_isBackground)
                                {
                                    showNotification("对方对您的手机执行了不合法的命令，已被屏蔽",getString(R.string.app_name),"对方对您的手机执行了不合法的命令，已被屏蔽");
                                }
                                return;
                            }

                            _items.add(new ChatItem("对方对您的手机执行了‘" + shell + "'命令", ChatAdapter.TYPE_SYSTEM_MSG, true));
                            _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                            _recyclerView.scrollToPosition(_items.size() - 1);
                            if(_isBackground)
                            {
                                showNotification("对方对您的手机执行了‘" + shell + "'命令",getString(R.string.app_name),"对方对您的手机执行了‘" + shell + "'命令");
                            }
                            if(CustomCmd.isRemoteCustomCmd(shell))//判断是不是本地命令
                            {
                                String result=CustomCmd.runRemoteCmd(MainActivity.this, _curUser,shell);
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
                            //显示对方发来的命令结果
                            _items.add(new ChatItem(msg.substring(2, msg.length()), ChatAdapter.TYPE_STRANGER_MSG, false));
                            //_adapter.notifyDataSetChanged();
                            _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                            _recyclerView.scrollToPosition(_items.size() - 1);

                        } else//纯文本,慢速显示文本
                        {
                            //显示对方发来的文本消息
                            _items.add(new ChatItem(msg, ChatAdapter.TYPE_STRANGER_MSG, true));
                            //_adapter.notifyDataSetChanged();
                            _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                            _recyclerView.scrollToPosition(_items.size() - 1);
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

            Notification notification=new Notification.Builder(MainActivity.this)
                    .setTicker(ticker)
                    .setSmallIcon(R.mipmap.app_icon)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setContentIntent(_pendingIntent)
                    .setAutoCancel(true)
                    .getNotification();
            _notificationManager.notify(0,notification);

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
     * 单击控件事件
     **********************************************************************************************/
    private class ClickEvents implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            //控件判断
            if (v.getId() == R.id.button_send) {
                final String msg = _editMsg.getText().toString();

                //为空判断
                if (!TextUtils.isEmpty(msg) && _canSend) {

                    //是否是命令
                    if (isCmd(msg)) {
                        String shell=msg.substring(1,msg.length());
                        //屏蔽命令
                        if (_settingPreferences.getBoolean("checkbox_receive_msg", false)&&!CustomCmd.isLocalCustomCmd(shell))
                        {
                            _items.add(new ChatItem("由于你屏蔽了命令，所以你也不能发送命令", ChatAdapter.TYPE_SYSTEM_MSG, true));
                            _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                            _recyclerView.scrollToPosition(_items.size() - 1);
                            return;
                        } else
                        //没有屏蔽
                        {
                            //命令不合法直接提示并返回
                            if (!availableCmd(msg)) {
                                _items.add(new ChatItem("该命令不允许发送", ChatAdapter.TYPE_SYSTEM_MSG, true));
                                _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                _recyclerView.scrollToPosition(_items.size() - 1);
                                return;
                            }
                            //判断是否是系统命令
                             if(CustomCmd.isLocalCustomCmd(shell))
                            {
                                System.out.println("-------------->local");
                                String result=CustomCmd.runLocalCmd(MainActivity.this, _curUser,shell);
                                _items.add(new ChatItem(result, ChatAdapter.TYPE_SYSTEM_MSG, true));
                                _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                _recyclerView.scrollToPosition(_items.size() - 1);
                                _editMsg.setText("");
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
                //点击文本框
            } else if (v.getId() == R.id.edit_msg) {
                _recyclerView.scrollToPosition(_items.size() - 1);
            }
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
                    _items.add(new ChatItem(msg, ChatAdapter.TYPE_MYSELF_MSG, true));
                    //_adapter.notifyDataSetChanged();
                    _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                    _recyclerView.scrollToPosition(_items.size() - 1);
                    _editMsg.setText("");
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
                        //返回50条数据，如果不加上这条语句，默认返回10条数据
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
                                                _buttonSend.setEnabled(true);
                                                _items.add(new ChatItem("匹配用户成功，现在可以开始聊天啦", ChatAdapter.TYPE_SYSTEM_MSG, true));
                                                //_adapter.notifyDataSetChanged();
                                                _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                                _recyclerView.scrollToPosition(_items.size() - 1);
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
                                                                _items.add(new ChatItem("对方没有设置签名", ChatAdapter.TYPE_STRANGER_SIGN, true));
                                                                //_adapter.notifyDataSetChanged();
                                                                _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                                                _recyclerView.scrollToPosition(_items.size() - 1);
                                                            } else {
                                                                _items.add(new ChatItem(newFriend.getSign(), ChatAdapter.TYPE_STRANGER_SIGN, true));
                                                                //_adapter.notifyDataSetChanged();
                                                                _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                                                _recyclerView.scrollToPosition(_items.size() - 1);
                                                            }
                                                        }
                                                    }
                                                });

                                            } else {

                                                _items.add(new ChatItem(_friendId + ":修改用户状态失败,原因：" + e.toString(), ChatAdapter.TYPE_SYSTEM_MSG, true));
                                                //_adapter.notifyDataSetChanged();
                                                _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                                _recyclerView.scrollToPosition(_items.size() - 1);
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
                                    _buttonSend.setEnabled(true);


                                    _items.add(new ChatItem("匹配用户成功，现在可以开始聊天啦", ChatAdapter.TYPE_SYSTEM_MSG, true));
                                    //_adapter.notifyDataSetChanged();
                                    _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                    _recyclerView.scrollToPosition(_items.size() - 1);
                                    //设置签名
                                    if(friend.getSign()==null) {
                                        _items.add(new ChatItem("对方没有设置签名", ChatAdapter.TYPE_STRANGER_SIGN, true));
                                        //_adapter.notifyDataSetChanged();
                                        _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                        _recyclerView.scrollToPosition(_items.size() - 1);
                                    }
                                    else
                                    {
                                        _items.add(new ChatItem(friend.getSign(), ChatAdapter.TYPE_STRANGER_SIGN, true));
                                        //_adapter.notifyDataSetChanged();
                                        _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                        _recyclerView.scrollToPosition(_items.size() - 1);
                                    }
                                } else {

                                    _items.add(new ChatItem(_friendId + ":修改用户状态失败,原因：" + e.toString(), ChatAdapter.TYPE_SYSTEM_MSG, true));
                                    //_adapter.notifyDataSetChanged();
                                    _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                                    _recyclerView.scrollToPosition(_items.size() - 1);
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

        _buttonSend.setEnabled(false);
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
                        _items.add(new ChatItem("登陆成功", ChatAdapter.TYPE_SYSTEM_MSG, true));
                        _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                        _recyclerView.scrollToPosition(_items.size() - 1);
                        _items.add(new ChatItem("正在匹配用户...请等待", ChatAdapter.TYPE_SYSTEM_MSG, true));
                        // _adapter.notifyDataSetChanged();
                        _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                        _recyclerView.scrollToPosition(_items.size() - 1);
                    } else {
                        _items.add(new ChatItem("出了点小问题，正在为您处理", ChatAdapter.TYPE_SYSTEM_MSG, true));
                        // _adapter.notifyDataSetChanged();
                        _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                        _recyclerView.scrollToPosition(_items.size() - 1);
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
                    _items.add(new ChatItem("注册成功", ChatAdapter.TYPE_SYSTEM_MSG, true));
                    //_adapter.notifyDataSetChanged();
                    _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                    _recyclerView.scrollToPosition(_items.size() - 1);
                    _items.add(new ChatItem("正在匹配用户...请等待", ChatAdapter.TYPE_SYSTEM_MSG, true));
                    // _adapter.notifyDataSetChanged();
                    _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                    _recyclerView.scrollToPosition(_items.size() - 1);
                    getRandomUser();

                } else {
                    _items.add(new ChatItem(e.toString(), ChatAdapter.TYPE_SYSTEM_MSG, true));
                    //_adapter.notifyDataSetChanged();
                    _adapter.notifyItemRangeChanged(_items.size() - 1, 1);
                    _recyclerView.scrollToPosition(_items.size() - 1);
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
                .setMessage("你想退出聊天吗？")
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
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
        _isBackground =false;
    }
}
