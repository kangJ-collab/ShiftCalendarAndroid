package com.kangj.shiftcalendar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.kangj.shiftcalendar.widget.WidgetUpdater;

public class SystemEventReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AlarmSettingsStore.rescheduleAll(context);
        WidgetUpdater.updateAll(context);
    }
}
