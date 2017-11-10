package com.woc.chat.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;

import java.text.SimpleDateFormat;

/**
 * Created by zyw on 2017/8/29.
 */
public class MsgNotifyService extends Service {

    private SimpleBinder simpleBinder;
    @Override
    public void onCreate() {
        super.onCreate();
        simpleBinder=new SimpleBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return simpleBinder;
    }

    public   class SimpleBinder extends Binder {
        /**
         * 获取 Service 实例
         * @return
         */
        public MsgNotifyService getService(){
            return MsgNotifyService.this;
        }
    }
}
