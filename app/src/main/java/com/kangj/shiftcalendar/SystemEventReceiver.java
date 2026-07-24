package com.kangj.shiftcalendar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class SystemEventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AlarmScheduler.rescheduleStored(context);
    }
}
