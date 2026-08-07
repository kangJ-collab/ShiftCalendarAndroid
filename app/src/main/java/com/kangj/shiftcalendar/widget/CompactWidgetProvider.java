package com.kangj.shiftcalendar.widget;

import android.appwidget.AppWidgetManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

import com.kangj.shiftcalendar.R;
import com.kangj.shiftcalendar.ScheduleStore;

import java.time.LocalDate;

public class CompactWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        update(context, manager, ids);
    }

    public static void update(Context context, AppWidgetManager manager, int[] ids) {
        String today = LocalDate.now().toString();
        ScheduleStore.ScheduleEntry entry = ScheduleStore.getEntry(context, today);
        String label = WidgetStyle.label(entry, "동기화 필요");

        for (int id : ids) {
            RemoteViews views = new RemoteViews(
                context.getPackageName(), R.layout.widget_compact);
            views.setTextViewText(R.id.widgetCompactDate, today.substring(5));
            views.setTextViewText(R.id.widgetCompactShift, label);
            if (!entry.shift.isEmpty()) {
                views.setInt(R.id.widgetCompactRoot, "setBackgroundColor",
                    WidgetStyle.background(entry, 0xffe8eff2));
                views.setTextColor(R.id.widgetCompactShift,
                    WidgetStyle.text(entry, 0xff202428));
            }
            views.setOnClickPendingIntent(
                R.id.widgetCompactRoot, TodayWidgetProvider.openApp(context));
            manager.updateAppWidget(id, views);
        }
    }
}
