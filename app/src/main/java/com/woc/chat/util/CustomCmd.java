package com.woc.chat.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Vibrator;

import com.woc.chat.entity.User;

import cn.bmob.v3.exception.BmobException;
import cn.bmob.v3.listener.UpdateListener;

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
    private static String[] remoteCmdTable ={VIBRATE,PLAY,VERSION};
    private static String[] localCmdTable ={SIGN};
    private static String signMsg="更改签名成功";

    /**
     * 让本地运行命令
     * @param context
     * @param user
     * @param cmd
     * @return
     */
    public static  String runLocalCmd(Context context,User user,String cmd)
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
        }
        return msg;
    }


    /**
     * 让远程运行命令
     * @param context
     * @param cmd
     * @return
     */
    public static  String runRemoteCmd(Context context,User user,String cmd)
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
     * 设置签名
     * @param user
     * @param signString
     * @return
     */
    private static String setSign(User user, final String signString)
    {
        user.setSign(signString);
        user.update(new UpdateListener() {
            @Override
            public void done(BmobException e) {
                if(e==null)
                {
                    signMsg="更改签名成功";
                }else
                {
                    signMsg="更改签名失败:"+e.toString();
                }
            }
        });
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
