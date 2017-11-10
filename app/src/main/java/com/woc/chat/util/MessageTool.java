package com.woc.chat.util;

import android.text.TextUtils;

import org.jivesoftware.smack.packet.Message;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by zyw on 2017/1/30.
 */
public class MessageTool {

    /**
     * 判断命令的合法性
     * @param cmd
     * @return
     */
    public static boolean availableCmd(String cmd)
    {
        //不包含html,且含有=的不给发送
        if((!cmd.startsWith("html")&&cmd.contains("="))
                &&(!cmd.startsWith("sign")&&cmd.contains("=")))
        {
            return  false;
        }

        if(cmd.matches(".*echo.+>.*/data/data/.+"))//不允许重定向到data
        {
            return false;
        }
        for(int i = 0; i< ConstantPool.unusableCmd.length; i++)
        {;
            Pattern pattern= Pattern.compile("\\b"+ ConstantPool.unusableCmd[i]+"\\b");
            Matcher matcher=pattern.matcher(cmd);
            if(matcher.find())
                return false;
        }
        return true;
    }

    /**
     * 返回一个消息是否是命令
     * @param msg
     * @return
     */
    public static boolean isCmdMsg(Message msg)
    {
        String mainBody=msg.getBody();
        String cmdBody=msg.getBody(ConstantPool.MESSAGE_TYPE_CMD);
        return  TextUtils.isEmpty(mainBody)&&!TextUtils.isEmpty(cmdBody);
    }

    /**
     * 返回一个消息是否是命令
     * @param msg
     * @return
     */
    public static boolean isCmd(String msg)
    {
        return  msg.startsWith("$")&&msg.length()>1;
    }
    /**
     * 返回一个消息是否是命令结果
     * @param msg
     * @return
     */
    public static boolean isCmdResult(Message msg)
    {
        String mainBody=msg.getBody();
        String cmdResult=msg.getBody(ConstantPool.MESSAGE_TYPE_CMD_RESULT);
        return  TextUtils.isEmpty(mainBody)&&!TextUtils.isEmpty(cmdResult);
    }
    /**
     * 返回一个消息是否是命令结果
     * @param msg
     * @return
     */
    public static boolean isSystemMsg(Message msg)
    {
        String mainBody=msg.getBody();
        String systemMsg=msg.getBody(ConstantPool.MESSAGE_TYPE_SYSTEM);
        return  TextUtils.isEmpty(mainBody)&&!TextUtils.isEmpty(systemMsg);
    }

    /**
     * 返回一个消息是否是签名
     * @param msg
     * @return
     */
    public static boolean isSignMsg(Message msg)
    {
        String mainBody=msg.getBody();
        String signMsg=msg.getBody(ConstantPool.MESSAGE_TYPE_USER_SIGN);
        return  TextUtils.isEmpty(mainBody)&&!TextUtils.isEmpty(signMsg);
    }
}
