package com.woc.chat.util;

import android.text.TextUtils;

import org.jivesoftware.smack.packet.Message;

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

        if(cmd.matches(".*echo.+>.*/data/data/.+"))//不允许重定向到data
        {
            return false;
        }
        for(int i = 0; i< ConstantPool.unusableCmd.length; i++)
        {
            if(cmd.matches("\\s*"+ ConstantPool.unusableCmd[i]+"($|\\s+.*)"))
                return false;
        }
        return true;
    }

    /**
     * 返回一个消息是否是命令
     * @param msg
     * @return
     */
    public static boolean isCmd(Message msg)
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
        return  msg.startsWith("$");
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
}
