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
    private static final String KEY_MODE = "alarm_mode_v1";
    private static final String KEY_SOUND_URI = "alarm_sound_uri_v1";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    public static final String MODE_SOUND_VIBRATE = "sound_vibrate";
    public static final String MODE_SOUND = "sound";
    public static final String MODE_VIBRATE = "vibrate";

    public static final List<String> DEFAULT_SHIFTS = Arrays.asList(
        "주간", "야간", "주OT", "야OT", "주+반OT", "야+반OT", "주8OT", "야8OT",
        "일근08~17", "일근07~17", "일근+1 OT", "일근휴일 OT", "호출", "교육", "출장"
    );

    private AlarmSettingsStore() {}

    public static String loadMode(Context context) {
        String mode = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, MODE_SOUND_VIBRATE);
        if (MODE_SOUND.equals(mode) || MODE_VIBRATE.equals(mode)) return mode;
        return MODE_SOUND_VIBRATE;
    }

    public static void saveMode(Context context, String mode) {
        String safeMode = MODE_SOUND.equals(mode) || MODE_VIBRATE.equals(mode)
            ? mode
            : MODE_SOUND_VIBRATE;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, safeMode)
            .apply();
    }

    public static String loadSoundUri(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SOUND_URI, "");
    }

    public static void saveSoundUri(Context context, String uri) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SOUND_URI, uri == null ? "" : uri)
            .apply();
    }

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
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, object.toString())
            .apply();
        rescheduleAll(context);
    }

    public static String getPermissionBlockReason(Context context) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return "알림 권한 필요";
        }

        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (Build.VERSION.SDK_INT >= 31 &&
            (manager == null || !manager.canScheduleExactAlarms())) {
            return "정확한 알람 권한 필요";
        }
        return "";
    }

    public static String rescheduleAll(Context context) {
        try {
            String permissionBlockReason = getPermissionBlockReason(context);
            if (!permissionBlockReason.isEmpty()) {
                return AlarmScheduler.resultJson(false, permissionBlockReason, 0);
            }

            Map<String, List<String>> settings = load(context);
            Map<String, String> schedule = ScheduleStore.asMap(context);
            JSONArray alarms = new JSONArray();
            long now = System.currentTimeMillis();
            String mode = loadMode(context);

            for (Map.Entry<String, String> day : schedule.entrySet()) {
                String shift = day.getValue() == null ? "" : day.getValue().trim();
                if (isNonWorkingShift(shift)) continue;

                List<String> times = resolveAlarmTimes(settings, shift);
                if (times.isEmpty()) continue;
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
                    alarm.put("title", "교대달력 · " + shift);
                    alarm.put("body", day.getKey() + " " + time + " 기상 알람입니다.");
                    alarm.put("shiftType", shift);
                    alarm.put("workDate", day.getKey());
                    alarm.put("alarmMode", mode);
                    alarms.put(alarm);
                }
            }
            return AlarmScheduler.replaceAlarms(context, alarms.toString());
        } catch (Exception error) {
            return AlarmScheduler.resultJson(
                false,
                error.getMessage() == null ? "schedule_failed" : error.getMessage(),
                0);
        }
    }

    private static List<String> resolveAlarmTimes(
        Map<String, List<String>> settings, String shift) {
        List<String> specific = settings.get(shift);
        if (specific != null && !specific.isEmpty()) return specific;

        String fallbackKey = isNightShift(shift) ? "야간" : "주간";
        List<String> fallback = settings.get(fallbackKey);
        return fallback == null ? new ArrayList<>() : fallback;
    }

    private static boolean isNightShift(String shift) {
        return shift.contains("야") || shift.contains("야간") || shift.contains("밤");
    }

    private static boolean isNonWorkingShift(String shift) {
        if (shift.isEmpty() || isActualWorkShift(shift)) return shift.isEmpty();
        return shift.equals("휴무") || shift.equals("휴") ||
            shift.contains("연차") || shift.contains("휴가") ||
            shift.contains("공가") || shift.contains("병가") ||
            shift.contains("경조") || shift.contains("대체휴무") ||
            shift.contains("공휴휴가");
    }

    private static boolean isActualWorkShift(String shift) {
        return shift.contains("OT") || shift.contains("일근") ||
            shift.contains("주간") || shift.contains("야간") ||
            shift.contains("교육") || shift.contains("출장") ||
            shift.contains("호출");
    }

    private static int stableId(String value) {
        int hash = value.hashCode() & 0x7fffffff;
        return 1_000_000 + (hash % 1_900_000_000);
    }
}
