package com.woc.chat.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Environment;
import android.os.Vibrator;

import com.woc.chat.R;
import com.woc.chat.adapter.ChatAdapter;
import com.woc.chat.entity.ChatItem;

import java.io.File;
import java.util.List;
import java.util.Set;

//import jackpal.term.Term;

/**
 * Created by zyw on 2016/9/21.
 * 自定义命令
 */
public class CustomCmd {
    private static Vibrator vibrator;

    private static  final  String VIBRATE="vibrate";
    private static  final  String PLAY="play";
    private static  final String SIGN="sign";
    private static  final String VERSION="version";
    private static  final String CLEAR="clear";
    private static  final  String TERM="term";
    private static final String EXPORT="export";
    private static String[] remoteCmdTable ={VIBRATE,PLAY,VERSION};
    private static String[] localCmdTable ={SIGN,CLEAR,TERM,EXPORT};
    private static String signMsg="更改签名成功";
    private static String SD_PATH= Environment.getExternalStorageDirectory().getAbsolutePath();
    /**
     * 让本地运行命令
     * @param context
     * @param user
     * @param cmd
     * @return
     */
    public static  String runLocalCmd(Context context,String user,String cmd,ChatAdapter chatAdapter)
    {
        String[] cmds=cmd.split("\\s+");
        String msg=null;
        switch (cmds[0])
        {
            //设置签名
            case SIGN:
                if(cmds.length<2)
                {
                    msg="参数个数不对";
                    break;
                }
                msg=setSign(user,cmds[1]);
                break;

            case CLEAR:
                msg=clearList(context,chatAdapter);
                break;

            case TERM:
                msg=runTerm(context);
                break;
            case EXPORT:
                msg=exportMsg(context,SD_PATH+File.separator+cmds[1],chatAdapter);
        }
        return msg;
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
        sb.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n");
        sb.append("<style type=\"text/css\">\n");
        sb.append("body{background-color:black}");
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
        sb.append("</body>");
        sb.append("</html>");
        String msg=IO.writeFile(new File(path),sb.toString());
        if(msg==null)
            return  "聊天记录导出成功,目录："+path;
        return msg;
    }

    /**
     * 启动终端模拟器
     * @param context
     * @return
     */
    public  static  String runTerm(Context context)
    {

//        Intent intent=new Intent(context,Term.class);
//        context.startActivity(intent);

        return  "启动终端成功";
    }
    /**
     * 让远程运行命令
     * @param context
     * @param cmd
     * @return
     */
    public static  String runRemoteCmd(Context context,String user,String cmd,ChatAdapter chatAdapter)
    {
        String[] cmds=cmd.split("\\s+");
        String msg=null;
        switch (cmds[0])
        {
            //震动
            case VIBRATE:
                if(cmds.length<2)
                {
                    msg="参数个数不对";
                    break;
                }
                try {
                    int time = Integer.parseInt(cmds[1]);
                    if(time<=100000)
                    {
                        msg = vibrate(context, time);
                    }else {
                        msg="时间设置得过长";
                    }
                }
                catch(NumberFormatException e)
                {
                    msg="参数不正确";
                }
                break;
            //播放音乐
            case PLAY:
                if(cmds.length<2)
                {
                    msg="参数个数不对";
                    break;
                }
                msg=playSound(cmds[1]);
                break;
            //获取版本
            case VERSION:
                msg=getAppVersion(context);
                break;

        }
        return  msg;
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
    private static String setSign(String user, final String signString)
    {
        if(signString.length()>100)
        {
            return "签名长度不能大于100个字符";
        }

        return signMsg;
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
}
