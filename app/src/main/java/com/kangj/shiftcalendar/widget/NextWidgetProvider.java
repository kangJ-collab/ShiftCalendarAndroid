package com.kangj.shiftcalendar.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.widget.RemoteViews;

import com.kangj.shiftcalendar.R;
import com.kangj.shiftcalendar.ScheduleStore;

import java.time.LocalDate;
import java.util.Map;

public class NextWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { update(c,m,ids); }
    public static void update(Context context, AppWidgetManager manager, int[] ids) {
        Map<String,String> schedule = ScheduleStore.asMap(context);
        LocalDate today = LocalDate.now();
        String now = schedule.getOrDefault(today.toString(), "동기화 필요");
        String next = schedule.getOrDefault(today.plusDays(1).toString(), "동기화 필요");
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_next);
            views.setTextViewText(R.id.widgetNextToday, "오늘  " + now);
            views.setTextViewText(R.id.widgetNextTomorrow, "내일  " + next);
            views.setOnClickPendingIntent(R.id.widgetNextRoot, TodayWidgetProvider.openApp(context));
            manager.updateAppWidget(id, views);
        }
    }
}
