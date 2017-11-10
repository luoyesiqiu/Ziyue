package com.woc.chat.util;

import android.content.Context;
import android.util.Log;

import org.jivesoftware.smack.ConnectionListener;
import org.jivesoftware.smack.PacketCollector;
import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.chat.Chat;
import org.jivesoftware.smack.chat.ChatManager;
import org.jivesoftware.smack.chat.ChatManagerListener;
import org.jivesoftware.smack.chat.ChatMessageListener;
import org.jivesoftware.smack.filter.AndFilter;
import org.jivesoftware.smack.filter.StanzaTypeFilter;
import org.jivesoftware.smack.filter.ToFilter;
import org.jivesoftware.smack.packet.IQ;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.packet.Stanza;
import org.jivesoftware.smack.packet.StreamError;
import  org.jivesoftware.smackx.iqregister.AccountManager;
import com.woc.chat.R;
import com.woc.chat.interfaces.OnReceiveNonChatMsg;
import com.woc.chat.interfaces.SmackResultCallback;
import com.woc.chat.iq.NonChatMsgIQ;
import com.woc.chat.provider.NonChatMsgIQProvider;

import org.jivesoftware.smack.ConnectionConfiguration;
import org.jivesoftware.smack.SmackException;
import org.jivesoftware.smack.StanzaListener;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.filter.StanzaFilter;
import org.jivesoftware.smack.provider.ProviderManager;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jivesoftware.smackx.offline.OfflineMessageManager;

import java.io.IOException;
import java.util.Map;

/**
 * Created by zyw on 2017/1/29.
 */
public class SmackTool {

    private static XMPPTCPConnection connection;
    private  Context context;
    private  Chat chat;
    private  ChatManager chatManager;
    private  String curHost;
    private ChatManagerListener chatManagerListener;
    private  OnReceiveNonChatMsg onReceiveNonChatMsg;
    private static  SmackTool smackTool;

    /**
     * 给对方发送个人签名
     * @param friendJid 消息接收方
     * @param smackResultCallback
     */
    public void   sendSign(final String friendJid, final String sign, final SmackResultCallback smackResultCallback){

        if(connection==null)
            return;
        if(chat==null) {
            try {
                chat = chatManager.createChat(friendJid);
            }catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        Thread thread=   new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Message msg=new Message();
                    msg.addBody(ConstantPool.MESSAGE_TYPE_USER_SIGN,sign);
                    chat.sendMessage(msg);
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.send_sign_msg_success),true);
                }
                catch (Exception e) {
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.send_sign_msg_fail),false);
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    /**
     * 给对方发送离开消息
     * @param friendJid 消息接收方
     * @param smackResultCallback
     */
    public void   sendQuitChat(final String friendJid,final SmackResultCallback smackResultCallback){

        final NonChatMsgIQ msg=new NonChatMsgIQ(ConstantPool.TAG_INFO);
        msg.setFrom(connection.getUser());
        msg.setTo(friendJid);
        msg.setData(ConstantPool.DATA_TYPE_NIL);
        msg.setMsgType(ConstantPool.QUIT_CHAT);
        Thread thread=  new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    connection.sendStanza(msg);
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.send_quit_chat_msg_success),true);
                } catch (SmackException.NotConnectedException e) {
                    // TODO Auto-generated catch block
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.send_quit_chat_msg_fail),false);
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }


    /**
     * 给对方发送匹配成功消息
     * @param user 消息接收方
     * @param myJID 自己的jid
     * @param smackResultCallback
     */
    public void   sendMatchSuccess(final String user,final  String myJID,final SmackResultCallback smackResultCallback){

        final NonChatMsgIQ msg=new NonChatMsgIQ(ConstantPool.TAG_INFO);
        msg.setFrom(connection.getUser());
        msg.setTo(user);
        msg.setData(myJID);
        msg.setMsgType(ConstantPool.MATCH_SUCCESS);
        Thread thread=  new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    connection.sendStanza(msg);
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.send_match_success_msg_success),true);
                } catch (SmackException.NotConnectedException e) {
                    // TODO Auto-generated catch block
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.send_match_success_msg_fail),false);
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }

    /**
     * 发送系统消息
     * @param userId
     * @param systemMsg
     * @param smackResultCallback
     */
    public void   sendSystemMsg(String userId, final String systemMsg, final SmackResultCallback smackResultCallback){

        if(connection==null)
            return;
        if(chat==null) {
            try {
                chat = chatManager.createChat(userId);
            }catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        Thread thread=   new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Message msg=new Message();
                    msg.addBody(ConstantPool.MESSAGE_TYPE_SYSTEM,systemMsg);
                    chat.sendMessage(msg);
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.send_system_msg_success),true);
                }
                catch (Exception e) {
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.send_system_msg_fail),false);
                    e.printStackTrace();
                }
            }
        });
        thread.start();

    }
    /**
     * 发送命令结果
     * @param userId
     * @param cmdResult
     * @param smackResultCallback
     */
    public void   sendCmdResult(String userId, final String cmdResult, final SmackResultCallback smackResultCallback){

        if(connection==null)
            return;
        if(chat==null) {
            chat = chatManager.createChat(userId);
        }

        Thread thread=  new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Message msg=new Message();
                    msg.addBody(ConstantPool.MESSAGE_TYPE_CMD_RESULT,cmdResult);
                    chat.sendMessage(msg);
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.send_cmd_result_success),true);
                }
                catch (Exception e) {
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.send_cmd_result_fail),false);
                    e.printStackTrace();
                }
            }
        });
        thread.start();

    }
    /**
     * 发送命令消息
     * @param userId
     * @param cmd
     * @param smackResultCallback
     */
    public void   sendCmdMsg(String userId, final String cmd, final SmackResultCallback smackResultCallback){

        if(connection==null)
            return;
        if(chat==null) {
            chat = chatManager.createChat(userId);
        }

        Thread thread=  new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Message msg=new Message();
                    msg.addBody(ConstantPool.MESSAGE_TYPE_CMD,cmd);
                    chat.sendMessage(msg);
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.send_cmd_success),true);
                }
                catch (Exception e) {
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.send_cmd_fail),false);
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }
    /**
     * 接收聊天消息
     * @param chatMessageListener
     */
    public void   receiveChatMsg(final ChatMessageListener chatMessageListener){
        //添加消息接收器

        if(this.chatManagerListener==null) {
            chatManager.addChatListener(this.chatManagerListener = new ChatManagerListener() {
                /**
                 * @param chat
                 * @param b    消息是否来自本地用户
                 */
                @Override
                public void chatCreated(Chat chat, boolean b) {
                    if (!b) {
                        chat.addMessageListener(chatMessageListener);
                    }
                }
            });
        }
    }
    /**
     * 接收非聊天消息
     * @param onReceiveNonChatMsg
     */
    public void   receiveNonChatMsg(final OnReceiveNonChatMsg onReceiveNonChatMsg){

        this.onReceiveNonChatMsg=onReceiveNonChatMsg;
        //只要发给自己的 NonChatMsgIQ包
        StanzaFilter filter =new AndFilter( new StanzaTypeFilter(NonChatMsgIQ.class),new ToFilter(connection.getUser()));
        connection.addSyncStanzaListener(stanzaListener,filter);
        connection.addSyncStanzaListener(errorStanzaListener,errorFilter);
    }

    /**
     * 错误过滤器
     */
    StanzaFilter errorFilter = new StanzaFilter() {
        @Override
        public boolean accept(Stanza stanza) {
            if(stanza instanceof  IQ)
            {
                if (((IQ) stanza).getType().equals("error"))
                return true;
            }
            return  false;
        }
    };
    /**
     * 包监听器
     */
    private   StanzaListener stanzaListener=new StanzaListener() {
        @Override
        public void processPacket(Stanza packet) {
            // TODO Auto-generated method stub
            NonChatMsgIQ iq=(NonChatMsgIQ)packet;
            if(onReceiveNonChatMsg!=null)
                onReceiveNonChatMsg.onReceive(connection,iq.getMsgType(),iq.getData(),iq.getFrom(),iq.getTo(),true);
        }
    };
    /**
     * 错误
     */
    private   StanzaListener errorStanzaListener=new StanzaListener() {

        @Override
        public void processPacket(Stanza packet) {
            // TODO Auto-generated method stub
            NonChatMsgIQ iq=(NonChatMsgIQ)packet;
            if(onReceiveNonChatMsg!=null)
                onReceiveNonChatMsg.onReceive(connection,iq.getMsgType(),iq.getData(),iq.getFrom(),iq.getTo(),false);
        }
    };

    /**
     * 请求退出匹配聊天
     */
    public void   requireQuitMatchUser(final SmackResultCallback smackResultCallback){

        final NonChatMsgIQ msg=new NonChatMsgIQ(ConstantPool.TAG_INFO);
        msg.setFrom(connection.getUser());
        msg.setTo(ConstantPool.SERVER_NAME +"/"+ConstantPool.MATCH_USER_PLUGIN_NAME);
        msg.setData(ConstantPool.DATA_TYPE_NIL);
        msg.setMsgType(ConstantPool.QUIT_MATCH);
       Thread thread= new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    connection.sendStanza(msg);
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.send_require_quit_match_msg_success),true);
                } catch (SmackException.NotConnectedException e1) {
                    // TODO Auto-generated catch block
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.send_require_quit_match_msg_fail),false);
                    e1.printStackTrace();

                }
            }
        });
        thread.start();

    }
    /**
     * 请求匹配用户
     * @param smackResultCallback 回调可以为空
     */
    public void   requireMatchUser(final SmackResultCallback smackResultCallback){
        final NonChatMsgIQ msg=new NonChatMsgIQ(ConstantPool.TAG_INFO);
        msg.setFrom(connection.getUser());
        msg.setTo(ConstantPool.SERVER_NAME +"/"+ConstantPool.MATCH_USER_PLUGIN_NAME);
        msg.setData(ConstantPool.DATA_TYPE_NIL);
        msg.setMsgType(ConstantPool.MATCH_USER);
        Thread thread= new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    connection.sendStanza(msg);
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.send_require_match_msg_success),true);
                } catch (SmackException.NotConnectedException e1) {
                    // TODO Auto-generated catch block
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.send_require_match_msg_fail),false);
                    e1.printStackTrace();
                }
            }
        });
        thread.start();
    }

    /**
     * 发送文本消息
     * @param userId
     * @param msg
     * @param smackResultCallback
     */
    public void   sendTextMsg(String userId, final String msg, final SmackResultCallback smackResultCallback){
        if(chat==null) {
            chat = chatManager.createChat(userId);
        }
        Thread thread= new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    chat.sendMessage(msg);
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.send_msg_success),true);
                }
                catch (Exception e) {
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.send_msg_fail),false);
                    e.printStackTrace();
                }
            }
        });
        thread.start();

    }

    /**
     * 注册
     * @param username
     * @param password
     * @param smackResultCallback
     * @param attributes
     * @return
     */
    public void   register(final String username, final String password, final Map<String, String> attributes , final SmackResultCallback smackResultCallback){

        Thread thread= new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    AccountManager.sensitiveOperationOverInsecureConnectionDefault(true);
                    AccountManager.getInstance(connection).createAccount(username, password, attributes);
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.register_success),true);
                } catch (Exception e) {
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.register_fail),false);
                    e.printStackTrace();
                }
            }
        });
        thread.start();

    }

    /**
     * 登录
     * @param username
     * @param password
     * @return
     */
    public void  login(final String username, final String password, final SmackResultCallback smackResultCallback)
    {
        Thread thread=  new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    connection.login(username,password);
                    chatManager = ChatManager.getInstanceFor(connection);
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.login_success),true);
                } catch (XMPPException|SmackException|IOException e) {
                    if(smackResultCallback!=null)
                        smackResultCallback.onReceive(connection,context.getString(R.string.login_fail),false);
                    e.printStackTrace();
                }
            }
        });
        thread.start();
    }
    /**
     * 连接到服务器
     */
    public void connect(final SmackResultCallback smackResultCallback)
    {
        XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder()
                .setServiceName(ConstantPool.SERVER_NAME)
                .setHost(ConstantPool.REMOTE_HOST)
                .setPort(ConstantPool.REMOTE_PORT)
                .setConnectTimeout(20000)
                .setCompressionEnabled(false)
                .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled)
                .build();
        this.curHost=ConstantPool.REMOTE_HOST;
        connection = new XMPPTCPConnection(config);

        Thread thread= new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        connection= (XMPPTCPConnection) connection.connect();
                        ProviderManager.addIQProvider(ConstantPool.TAG_INFO, ConstantPool.INFO_NAMESPACE, new NonChatMsgIQProvider());
                        ReconnectionManager reconnectionManager=ReconnectionManager.getInstanceFor(connection);
                        reconnectionManager.enableAutomaticReconnection();
                        if(connection.isConnected())
                        {
                            connection.addConnectionListener(mConnectionListener);
                        }
                        if(smackResultCallback!=null)
                            smackResultCallback.onReceive(connection,context.getString(R.string.connect_server_success),true);
                    } catch (Exception e) {
                        if(smackResultCallback!=null)
                            smackResultCallback.onReceive(connection,context.getString(R.string.connect_server_fail),false);
                        e.printStackTrace();
                    }
                }
            });
        thread.start();
    }

   private static ConnectionListener mConnectionListener=new ConnectionListener() {
        @Override
        public void connected(XMPPConnection connection) {
            Log.v("smackTool","connected");
        }

        @Override
        public void authenticated(XMPPConnection connection, boolean resumed) {
            Log.v("smackTool","authenticated");
        }

        @Override
        public void connectionClosed() {
            Log.v("smackTool","connectionClosed");
        }

        @Override
        public void connectionClosedOnError(Exception e) {
            Log.v("smackTool","connectionClosedOnError");
        }

        @Override
        public void reconnectionSuccessful() {
            Log.v("smackTool","reconnectionSuccessful");
        }

        @Override
        public void reconnectingIn(int seconds) {
            Log.v("smackTool","reconnectingIn");
        }

        @Override
        public void reconnectionFailed(Exception e) {
            Log.v("smackTool","reconnectionFailed");
        }
    };

    /**
     * 删除离线消息
     */
    public void deleteOfflineMsg()
    {
        if(connection!=null) {
            OfflineMessageManager offlineManager = new OfflineMessageManager(connection);
            try {
                offlineManager.deleteMessages();
            } catch (SmackException.NoResponseException e) {
                e.printStackTrace();
            } catch (XMPPException.XMPPErrorException e) {
                e.printStackTrace();
            } catch (SmackException.NotConnectedException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 断开连接
     * @param smackResultCallback
     */
    public  void disconnect(SmackResultCallback smackResultCallback)
    {
        if(connection!=null)
        {
            connection.disconnect();
            if(smackResultCallback!=null)
                smackResultCallback.onReceive(connection,context.getString(R.string.disconnect_success),true);
        }
        else
        {
            if(smackResultCallback!=null)
                smackResultCallback.onReceive(connection,context.getString(R.string.disconnect_fail),false);
        }
    }

    /**
     * 获取连接
     * @return
     */
    public static XMPPTCPConnection getConnection()
    {
        return  connection;
    }
    private  SmackTool(Context context)
    {
        this.context=context;
    }
    public static SmackTool getInstance(Context context)
    {
        if(smackTool==null)
        {
            return new SmackTool(context);
        }
        return smackTool;
    }

}
