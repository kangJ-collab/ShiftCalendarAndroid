package com.kangj.shiftcalendar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmActionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent stopIntent = new Intent(context, AlarmRingingService.class);
        context.stopService(stopIntent);
    }
}
