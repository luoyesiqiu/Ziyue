package com.woc.chat.util;

import android.app.Notification;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.Vibrator;

import com.woc.chat.R;
import com.woc.chat.adapter.ChatAdapter;
import com.woc.chat.entity.ChatItem;

import org.jivesoftware.smackx.vcardtemp.VCardManager;
import org.jivesoftware.smackx.vcardtemp.packet.VCard;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by zyw on 2016/9/21.
 * 自定义命令
 */
public class CustomCmd {
    private static Vibrator vibrator;
   private static String friendJID;
    private static  final  String VIBRATE="vibrate";
    private static  final  String PLAY="play";
    private static  final String SIGN="sign";
    private static  final String VERSION="version";
    private static  final String CLEAR="clear";
    private static final String EXPORT="export";
    private static final String HTML="html";
    private  static  final  String C_LOGIN ="clogin";
    private  static  final  String GET_IP="getip";
    private static String[] remoteCmdTable ={VIBRATE,PLAY,VERSION,GET_IP};
    private static String[] remoteNoReturnCmdTable ={HTML};
    private static String[] localCmdTable ={SIGN,CLEAR,EXPORT, C_LOGIN};
    private static String[] unCheckCmdTable ={HTML};
    private static String signMsg="更改签名成功";
    private static String SD_PATH= Environment.getExternalStorageDirectory().getAbsolutePath();
    private static SmackTool smackTool;
    /**
     * 让本地运行命令
     * @param context
     * @param friendJID
     * @param cmd
     * @return
     */
    public static  String runLocalCmd(Context context,String friendJID,String cmd,ChatAdapter chatAdapter)
    {
        String[] params=cmd.split("\\s+");
        String msg=null;
        switch (params[0])
        {
            //设置签名
            case SIGN:
                if(params.length<2)
                {
                    msg="参数个数不对";
                    break;
                }
                msg=setSign(context,"",cmd.substring(cmd.indexOf(' ')));
                break;

            case CLEAR:
                msg=clearList(context,chatAdapter);
                break;

            case EXPORT:
                if(params.length<2)
                {
                    msg="参数个数不对";
                    break;
                }
                msg=exportMsg(context,SD_PATH+File.separator+params[1],chatAdapter);
                break;
            case C_LOGIN:
                msg=clearLogin(context);
                break;

        }
        return msg;
    }


    public  static String clearLogin(Context context)
    {
        SharedPreferences sharedPreferences=context.getSharedPreferences("data",Context.MODE_PRIVATE);
        sharedPreferences.edit().clear().apply();
       // System.exit(0);
        return "清除登录信息成功";
    }

    public static  String exportMsg(Context context,String path,ChatAdapter chatAdapter)
    {
        StringBuilder sb=new StringBuilder();
        List<ChatItem> chatMsgList=chatAdapter.getChatMsgList();
        List<String> prefixList=chatAdapter.getPrefixList();
        List<Integer> typeList=chatAdapter.getTypeList();
        if(chatMsgList.size()!=prefixList.size()||chatMsgList.size()!=typeList.size())
            return  "聊天记录导出失败";
        sb.append("<html>\n<head>\n");
        sb.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\"  name=\"viewport\"content=\"width=device-width, initial-scale=1.0, minimum-scale=0.5, maximum-scale=2.0, user-scalable=yes\">\n");
        sb.append("<style type=\"text/css\">\n");
        sb.append("body{background-color:black;}");
        sb.append("</style>\n");
        sb.append("</head>\n");
        sb.append("<title>");
        sb.append(context.getString(R.string.app_name));
        sb.append("</title>\n");
        sb.append("<body>\n");
        for(int i=0;i<chatMsgList.size();i++) {
            int type=typeList.get(i);
            String prefix=prefixList.get(i);
            switch (type)
            {
                case ChatAdapter.TYPE_MYSELF_MSG:
                    sb.append( "<font color='white'>"+prefix+ chatMsgList.get(i).getMsg() + "</font></br>\n");
                    break;
                case ChatAdapter.TYPE_STRANGER_SIGN:
                    sb.append( "<font color='#ff69b4'>"+prefix+ chatMsgList.get(i).getMsg() + "</font></br>\n");
                    break;
                case ChatAdapter.TYPE_STRANGER_MSG:
                    sb.append( "<font color='green'>"+prefix+ chatMsgList.get(i).getMsg() + "</font></br>\n");
                    break;
                case ChatAdapter.TYPE_SYSTEM_MSG:
                    sb.append( "<font color='red'>"+prefix+ chatMsgList.get(i).getMsg() + "</font></br>\n");
                    break;
                case ChatAdapter.TYPE_STRANGER_RESULT:
                    sb.append( "<font color='#ffd500'>"+prefix+ chatMsgList.get(i).getMsg() + "</font></br>\n");
                    break;
                default:
                    sb.append( prefix+ chatMsgList.get(i).getMsg() + "\n");

                    break;
            }

        }
        sb.append("</body>\n");
        sb.append("</html>");
        String msg=IO.writeFile(new File(path),sb.toString());
        if(msg==null)
            return  "聊天记录导出成功,目录："+path;
        return msg;
    }
    /**
     * 让远程运行命令
     * @param context
     * @param cmd
     * @return
     */
    public static  String runRemoteNoReturnCmd(Context context,String user,String cmd,ChatAdapter chatAdapter)
    {
        String[] params=cmd.split("\\s+");
        String msg=null;
        switch (params[0])
        {
            //震动
            case HTML:
                if(params.length<2)
                {
                    msg="参数个数不对";
                    break;
                }
                msg=handleHtmlCmd(cmd.substring(cmd.indexOf(' ')));
            break;
        }
        return  msg;
    }
    /**
     * 让远程运行命令
     * @param context
     * @param cmd
     * @return
     */
    public static  String runRemoteCmd(Context context,String user,String cmd,ChatAdapter chatAdapter)
    {
        String[] params=cmd.split("\\s+");
        final String[] msg = {null};
        switch (params[0])
        {
            //震动
            case VIBRATE:
                if(params.length<2)
                {
                    msg[0] ="参数个数不对";
                    break;
                }
                try {
                    int time = Integer.parseInt(params[1]);
                    if(time<=100000)
                    {
                        msg[0] = vibrate(context, time);
                    }else {
                        msg[0] ="时间设置得过长";
                    }
                }
                catch(NumberFormatException e)
                {
                    msg[0] ="参数不正确";
                }
                break;
            //播放音乐
            case PLAY:
                if(params.length<2)
                {
                    msg[0] ="参数个数不对";
                    break;
                }
                msg[0] =playSound(params[1]);
                break;
            //获取版本
            case VERSION:
                msg[0] =getAppVersion(context);
                break;
            case GET_IP:
                Thread thread=new Thread(new Runnable() {
                    @Override
                    public void run() {
                        msg[0] =getIp();
                    }
                });

                thread.start();
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                break;
        }
        return msg[0];
    }


    /**
     * 获取ip
     */
    private  static String  getIp()
    {
        final String[] arr=new String[1];
        final boolean[] ok=new boolean[1];
         StringBuilder result=new StringBuilder();
        OkHttpClient okHttpClient=new OkHttpClient();

        Request request = new Request.Builder().url(ConstantPool.getIPUrl1)
                .addHeader("User-Agent",ConstantPool.UA)
                .get()
                .build();
        Call call = okHttpClient.newCall(request);

        try {
            Response response=call.execute();
            arr[0]=response.body().string();
            //System.out.println("-------------->2"+arr[0]);
            ok[0]=true;

        } catch (IOException e) {
            e.printStackTrace();
            arr[0]=e.toString();
            ok[0]=false;
        }
        if(!ok[0])
        {
            return  arr[0];
        }
        String json=arr[0].substring(arr[0].indexOf('{'),arr[0].indexOf(';'));
        //System.out.println("-------------->3"+json);
        try {
            JSONObject jsonObject=new JSONObject(json);
            result.append("IP:"+jsonObject.getString("cip")+"\n");
            result.append("运营商："+jsonObject.getString("cname"));
;
        } catch (JSONException e) {
            e.printStackTrace();
            return  e.toString();
        }
        return result.toString();
    }
    /**
     * 处理HTML
     * @param text
     * @return
     */
    private static  String handleHtmlCmd(String text)
    {
        return text;
    }


    /**
     * 获取应用版本
     * @param context
     * @return
     */
    private static String getAppVersion(Context context)
    {
        try {
            PackageManager manager = context.getPackageManager();
            PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
            String version = info.versionName;
            return version;
        } catch (Exception e) {
            e.printStackTrace();
            return "Unknown";
        }
    }
    /**
     *
     * @param context
     * @return
     */
    private static String clearList(Context context, ChatAdapter chatAdapter)
    {
        List<ChatItem> list=chatAdapter.getChatMsgList();
        Set<Integer> set=chatAdapter.getSet();
        list.clear();
        set.clear();
        chatAdapter.notifyDataSetChanged();
        return "消息已清除";
    }
    /**
     * 设置签名
     * @param user
     * @param signString
     * @return
     */
    private static String setSign(Context context,String user, final String signString){
        if(signString.length()>200)
        {
            return "签名长度不能大于200个字符";
        }

        VCard vCard=new VCard();
        vCard.setField("sign",signString);
        ;VCardManager vCardManager=VCardManager.getInstanceFor(SmackTool.getConnection());
        try {
            vCardManager.saveVCard(vCard);
            return  "更改签名成功";
        } catch (Exception e) {
            e.printStackTrace();
        }

        return  "更改签名失败";
    }


    /**
     * 震动
     * @param context
     * @param time
     * @return
     */
    public static String vibrate(Context context,int time)
    {
        vibrator = (Vibrator)context.getSystemService(Context.VIBRATOR_SERVICE);
        //long [] pattern = {100,400,100,400};   // 停止 开启 停止 开启
        vibrator.vibrate(time);           //重复两次上面的pattern 如果只想震动一次，index设为-1
        return "成功震"+time+"毫秒";
    }

    /**
     * 播放声音
     * @param path
     * @return
     */
    public static  String playSound(String path)
    {
        String msg=null;
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setDataSource(path);
            mp.prepare();
            mp.start();
            msg="播放成功";
        } catch (Exception e) {
            msg="播放失败："+e.toString();
            e.printStackTrace();
        }
        return  msg;
    }

    /**
     * 判断是不是远程命令
     * @param cmd
     * @return
     */
    public static boolean isRemoteCustomCmd(String cmd)
    {
        String[] str=cmd.split("\\s+");
        for(int i = 0; i< remoteCmdTable.length; i++)
        {
            if(str[0].contains(remoteCmdTable[i]))
                return true;
        }
        return false;
    }
    /**
     * 判断是不是本地命令
     * @param cmd
     * @return
     */
    public static boolean isLocalCustomCmd(String cmd)
    {
        String[] str=cmd.split("\\s+");
        for(int i = 0; i< localCmdTable.length; i++)
        {
            if(str[0].contains(localCmdTable[i]))
                return true;
        }
        return false;
    }

    /**
     * 判断是不是远程无返回命令
     * @param cmd
     * @return
     */
    public static boolean isRemoteNoReturnCustomCmd(String cmd)
    {
        String[] str=cmd.split("\\s+");
        for(int i = 0; i< remoteNoReturnCmdTable.length; i++)
        {
            if(str[0].contains(remoteNoReturnCmdTable[i]))
                return true;
        }
        return false;
    }

    /**
     * 判断是不是不检查命令
     * @param cmd
     * @return
     */
    public static boolean isUnCheckCmd(String cmd)
    {
        String[] str=cmd.split("\\s+");
        for(int i = 0; i< unCheckCmdTable.length; i++)
        {
            if(str[0].contains(unCheckCmdTable[i]))
                return true;
        }
        return false;
    }
}
