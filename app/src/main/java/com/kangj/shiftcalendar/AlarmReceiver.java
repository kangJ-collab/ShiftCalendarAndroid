package com.kangj.shiftcalendar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        int alarmId = intent.getIntExtra("alarmId", 0);
        if (alarmId != 0) AlarmScheduler.removeStoredAlarm(context, alarmId);

        Intent serviceIntent = new Intent(context, AlarmRingingService.class);
        serviceIntent.setAction(AlarmRingingService.ACTION_START);
        serviceIntent.putExtras(intent);

        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(serviceIntent);
        } else {
            context.startService(serviceIntent);
        }
    }
}
