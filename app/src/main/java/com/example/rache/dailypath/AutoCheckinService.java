package com.example.rache.dailypath;

import android.app.PendingIntent;
import android.app.Service;
import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.util.Log;
import java.util.Date;


public class AutoCheckinService extends Service {

    private Thread mThread;
    boolean destoryFlag = false;
    private AlarmManager alarmManager;
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mThread= new Thread(new Runnable() {
            @Override
            public void run() {
                Intent intent = new Intent();
                intent.setAction("CHECK");
                intent.putExtra("KEY", 1);
                sendBroadcast(intent); }
        });
        mThread.start();
        alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        int fivemin = 5 * 60 * 1000;
        //int fivemin = 30 * 1000;
        long mMinute = SystemClock.elapsedRealtime() + fivemin;
        Intent i = new Intent(this, AlarmReceiver.class);
        PendingIntent pi = PendingIntent.getBroadcast(this, 0, i, 0);
        alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, mMinute, pi);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        destoryFlag=true;
    }

}
