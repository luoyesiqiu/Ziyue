package com.woc.chat.util;

import java.util.Random;

/**
 * Created by zyw on 2016/9/15.
 */
public class Utils {

    public static String[] chars = new String[] { "a", "b", "c", "d", "e", "f",
            "g", "h", "i", "j", "k", "l", "m", "n", "o", "p", "q", "r", "s",
            "t", "u", "v", "w", "x", "y", "z", "0", "1", "2", "3", "4", "5",
            "6", "7", "8", "9", "A", "B", "C", "D", "E", "F", "G", "H", "I",
            "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V",
            "W", "X", "Y", "Z" };

    /**
     * 生成随机的Key
     * @param len 位数
     */
    public  static  String getKey(int len)
    {
        StringBuilder sb=new StringBuilder();
        Random rd=new Random();
        for(int i=0;i<len;i++)
        {
            sb.append(chars[rd.nextInt(chars.length)]);
        }

        return sb.toString();
    }
}
