package com.kangj.shiftcalendar;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class AlarmSettingsStore {
    private static final String PREFS = "shiftcalendar_native";
    private static final String KEY = "multi_alarm_settings_v2";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    public static final List<String> DEFAULT_SHIFTS = Arrays.asList(
        "주간", "야간", "주OT", "야OT", "주+반OT", "야+반OT", "주8OT", "야8OT",
        "일근08~17", "일근07~17", "일근+1 OT", "일근휴일 OT", "호출", "교육", "출장"
    );

    private AlarmSettingsStore() {}

    public static Map<String, List<String>> load(Context context) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean firstUse = !prefs.contains(KEY);
        String raw = prefs.getString(KEY, "{}");
        try {
            JSONObject object = new JSONObject(raw);
            for (String shift : DEFAULT_SHIFTS) result.put(shift, readTimes(object.optJSONArray(shift)));
            for (String shift : ScheduleStore.discoveredShiftTypes(context)) {
                if (!result.containsKey(shift)) result.put(shift, readTimes(object.optJSONArray(shift)));
            }
        } catch (Exception ignored) {
            for (String shift : DEFAULT_SHIFTS) result.put(shift, new ArrayList<>());
        }
        if (firstUse && result.get("주간").isEmpty()) result.get("주간").add("05:30");
        if (firstUse && result.get("야간").isEmpty()) result.get("야간").add("15:30");
        return result;
    }

    private static List<String> readTimes(JSONArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) return values;
        for (int i = 0; i < array.length(); i++) {
            String time = array.optString(i, "");
            if (time.matches("\\d{2}:\\d{2}") && !values.contains(time)) values.add(time);
        }
        values.sort(String::compareTo);
        return values;
    }

    public static void save(Context context, Map<String, List<String>> settings) {
        JSONObject object = new JSONObject();
        try {
            for (Map.Entry<String, List<String>> entry : settings.entrySet()) {
                JSONArray array = new JSONArray();
                List<String> times = new ArrayList<>(entry.getValue());
                times.sort(String::compareTo);
                for (String time : times) if (time.matches("\\d{2}:\\d{2}")) array.put(time);
                object.put(entry.getKey(), array);
            }
        } catch (Exception ignored) {}
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, object.toString()).apply();
        rescheduleAll(context);
    }

    public static void rescheduleAll(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
            AlarmManager manager = context.getSystemService(AlarmManager.class);
            if (Build.VERSION.SDK_INT >= 31 && (manager == null || !manager.canScheduleExactAlarms())) return;

            Map<String, List<String>> settings = load(context);
            Map<String, String> schedule = ScheduleStore.asMap(context);
            JSONArray alarms = new JSONArray();
            long now = System.currentTimeMillis();

            for (Map.Entry<String, String> day : schedule.entrySet()) {
                List<String> times = settings.get(day.getValue());
                if (times == null) continue;
                for (int index = 0; index < times.size(); index++) {
                    String time = times.get(index);
                    LocalDate date = LocalDate.parse(day.getKey(), DATE);
                    String[] parts = time.split(":");
                    LocalDateTime local = date.atTime(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                    long trigger = local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                    if (trigger <= now + 1500) continue;
                    JSONObject alarm = new JSONObject();
                    alarm.put("id", stableId(day.getKey() + "|" + day.getValue() + "|" + time + "|" + index));
                    alarm.put("triggerAt", trigger);
                    alarm.put("title", "교대달력 · " + day.getValue());
                    alarm.put("body", day.getKey() + " " + time + " 기상 알람입니다.");
                    alarm.put("shiftType", day.getValue());
                    alarm.put("workDate", day.getKey());
                    alarms.put(alarm);
                }
            }
            AlarmScheduler.replaceAlarms(context, alarms.toString());
        } catch (Exception ignored) {}
    }

    private static int stableId(String value) {
        int hash = value.hashCode() & 0x7fffffff;
        return 1_000_000 + (hash % 1_900_000_000);
    }
}
