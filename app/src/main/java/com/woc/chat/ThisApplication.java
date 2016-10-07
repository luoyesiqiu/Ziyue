package com.woc.chat;

import android.app.Application;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.Spanned;
import android.util.Log;


import com.woc.chat.terminal.TerminalView;

import cn.bmob.v3.Bmob;

/**
 * Created by zyw on 2016/9/1.
 */
public class ThisApplication  extends Application{
    @Override
    public void onCreate() {
        super.onCreate();

        Bmob.initialize(this,"5e8fc01faa879eaaffddc0f88ba838d3");
    }

}
