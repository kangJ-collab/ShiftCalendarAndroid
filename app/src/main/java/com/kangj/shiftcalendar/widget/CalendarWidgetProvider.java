package com.kangj.shiftcalendar.widget;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.widget.RemoteViews;

import com.kangj.shiftcalendar.R;
import com.kangj.shiftcalendar.ScheduleStore;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

public class CalendarWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context c, AppWidgetManager m, int[] ids) { update(c,m,ids); }

    public static void update(Context context, AppWidgetManager manager, int[] ids) {
        Map<String, ScheduleStore.ScheduleEntry> schedule = new HashMap<>();
        for (int day = 1; day <= YearMonth.now().lengthOfMonth(); day++) {
            LocalDate date = YearMonth.now().atDay(day);
            schedule.put(date.toString(), ScheduleStore.getEntry(context, date.toString()));
        }
        for (int id : ids) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_calendar);
            views.setImageViewBitmap(R.id.widgetCalendarImage, drawCalendar(context, schedule));
            views.setOnClickPendingIntent(R.id.widgetCalendarRoot, TodayWidgetProvider.openApp(context));
            manager.updateAppWidget(id, views);
        }
    }

    private static Bitmap drawCalendar(
        Context context, Map<String, ScheduleStore.ScheduleEntry> schedule) {
        float density = context.getResources().getDisplayMetrics().density;
        int width = Math.max(760, (int)(360*density));
        int height = Math.max(760, (int)(360*density));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        YearMonth month = YearMonth.now();
        LocalDate today = LocalDate.now();

        paint.setColor(Color.rgb(32,36,40));
        paint.setTextSize(width * 0.055f);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        canvas.drawText(month.getYear() + "년 " + month.getMonthValue() + "월", width*0.05f, height*0.09f, paint);

        String[] weekdays = {"일","월","화","수","목","금","토"};
        float top = height*0.16f;
        float cellW = width/7f;
        float cellH = height*0.125f;
        paint.setTextSize(width*0.032f);
        for (int i=0;i<7;i++) {
            paint.setColor(i==0?Color.rgb(229,53,43):(i==6?Color.rgb(34,85,204):Color.rgb(88,99,107)));
            canvas.drawText(weekdays[i], i*cellW+cellW*0.43f, top, paint);
        }

        LocalDate first = month.atDay(1);
        int start = first.getDayOfWeek()==DayOfWeek.SUNDAY?0:first.getDayOfWeek().getValue();
        for (int day=1; day<=month.lengthOfMonth(); day++) {
            int index = start + day - 1;
            int row = index/7;
            int col = index%7;
            float x = col*cellW;
            float y = top + cellH*(row+1);
            LocalDate date = month.atDay(day);

            if (date.equals(today)) {
                paint.setColor(Color.rgb(232,239,242));
                canvas.drawRoundRect(x+4, y-cellH*0.72f, x+cellW-4, y+cellH*0.18f, 12,12,paint);
            }

            paint.setTypeface(Typeface.DEFAULT_BOLD);
            paint.setTextSize(width*0.031f);
            paint.setColor(col==0?Color.rgb(229,53,43):(col==6?Color.rgb(34,85,204):Color.rgb(32,36,40)));
            canvas.drawText(String.valueOf(day), x+cellW*0.12f, y-cellH*0.38f, paint);

            ScheduleStore.ScheduleEntry entry =
                schedule.getOrDefault(date.toString(),
                    new ScheduleStore.ScheduleEntry(date.toString(), "", "", "", ""));
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(width*0.024f);
            String shortShift = shorten(entry.label);
            if (!entry.shift.isEmpty()) {
                int bg = WidgetStyle.background(entry, 0xff53778c);
                canvas.drawRoundRect(
                    x + cellW * 0.06f, y - cellH * 0.25f,
                    x + cellW * 0.94f, y + cellH * 0.12f,
                    6, 6, paintFor(bg));
                paint.setColor(WidgetStyle.text(entry, 0xffffffff));
            } else {
                paint.setColor(0xff53778c);
            }
            canvas.drawText(shortShift, x+cellW*0.10f, y, paint);
        }
        return bitmap;
    }

    private static String shorten(String shift) {
        if (shift == null) return "";
        if (shift.length() <= 4) return shift;
        if (shift.startsWith("일근")) return "일근";
        if (shift.contains("OT")) return shift.substring(0, Math.min(4, shift.length()));
        return shift.substring(0, Math.min(3, shift.length()));
    }

    private static Paint paintFor(int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        return paint;
    }
}
