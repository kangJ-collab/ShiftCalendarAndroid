package com.kangj.shiftcalendar.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.kangj.shiftcalendar.MainActivity;
import com.kangj.shiftcalendar.R;
import com.kangj.shiftcalendar.ScheduleStore;

import java.time.LocalDate;
import java.util.Map;

public class TodayWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { update(c,m,ids); }
    public static void update(Context context, AppWidgetManager manager, int[] ids) {
        Map<String,String> schedule = ScheduleStore.asMap(context);
        String today = LocalDate.now().toString();
        String shift = schedule.getOrDefault(today, "앱을 열어 동기화");
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_today);
            views.setTextViewText(R.id.widgetTodayDate, today);
            views.setTextViewText(R.id.widgetTodayShift, shift);
            views.setOnClickPendingIntent(R.id.widgetTodayRoot, openApp(context));
            manager.updateAppWidget(id, views);
        }
    }
    static PendingIntent openApp(Context context) {
        Intent intent = new Intent(context, MainActivity.class);
        return PendingIntent.getActivity(context, 9901, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
