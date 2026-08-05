package com.kangj.shiftcalendar.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;

public final class WidgetUpdater {
    private WidgetUpdater() {}

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        TodayWidgetProvider.update(context, manager,
            manager.getAppWidgetIds(new ComponentName(context, TodayWidgetProvider.class)));
        NextWidgetProvider.update(context, manager,
            manager.getAppWidgetIds(new ComponentName(context, NextWidgetProvider.class)));
        CalendarWidgetProvider.update(context, manager,
            manager.getAppWidgetIds(new ComponentName(context, CalendarWidgetProvider.class)));
    }
}
