package com.woc.chat;

import android.app.Application;

import org.jivesoftware.smack.ReconnectionManager;
import org.jivesoftware.smack.android.AndroidSmackInitializer;
import org.jivesoftware.smackx.ping.android.ServerPingWithAlarmManager;

/**
 * Created by zyw on 2016/9/1.
 */
public class ThisApplication  extends Application{


    @Override
    public void onCreate() {
        super.onCreate();
        AndroidSmackInitializer androidSmackInitializer=new AndroidSmackInitializer();
        androidSmackInitializer.initialize();

    }

}
