package com.kangj.shiftcalendar.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

import com.kangj.shiftcalendar.R;
import com.kangj.shiftcalendar.ScheduleStore;

import java.time.LocalDate;
public class NextWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { update(c,m,ids); }
    public static void update(Context context, AppWidgetManager manager, int[] ids) {
        LocalDate today = LocalDate.now();
        ScheduleStore.ScheduleEntry todayEntry =
            ScheduleStore.getEntry(context, today.toString());
        ScheduleStore.ScheduleEntry tomorrowEntry =
            ScheduleStore.getEntry(context, today.plusDays(1).toString());
        String now = WidgetStyle.label(todayEntry, "동기화 필요");
        String next = WidgetStyle.label(tomorrowEntry, "동기화 필요");
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_next);
            views.setTextViewText(R.id.widgetNextToday, "오늘  " + now);
            views.setTextViewText(R.id.widgetNextTomorrow, "내일  " + next);
            if (!todayEntry.shift.isEmpty()) {
                views.setInt(R.id.widgetNextToday, "setBackgroundColor",
                    WidgetStyle.background(todayEntry, 0xffe8eff2));
                views.setTextColor(R.id.widgetNextToday,
                    WidgetStyle.text(todayEntry, 0xff202428));
            }
            if (!tomorrowEntry.shift.isEmpty()) {
                views.setInt(R.id.widgetNextTomorrow, "setBackgroundColor",
                    WidgetStyle.background(tomorrowEntry, 0xffeef2f4));
                views.setTextColor(R.id.widgetNextTomorrow,
                    WidgetStyle.text(tomorrowEntry, 0xff202428));
            }
            views.setOnClickPendingIntent(R.id.widgetNextRoot, TodayWidgetProvider.openApp(context));
            manager.updateAppWidget(id, views);
        }
    }
}
