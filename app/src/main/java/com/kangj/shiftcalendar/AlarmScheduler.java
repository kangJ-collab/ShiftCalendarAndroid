package com.kangj.shiftcalendar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

final class AlarmScheduler {
    private static final String PREFS = "shift_alarm_native";
    private static final String KEY_ALARMS = "scheduled_alarms";
    private static final int MAX_ALARMS = 180;
    private static final int TEST_ALARM_ID = 2_147_000_001;

    private AlarmScheduler() {}

    static String replaceAlarms(Context context, String alarmsJson) {
        try {
            JSONArray requested = new JSONArray(alarmsJson == null ? "[]" : alarmsJson);
            cancelStoredPendingIntents(context);

            JSONArray accepted = new JSONArray();
            long now = System.currentTimeMillis();
            int limit = Math.min(requested.length(), MAX_ALARMS);

            for (int index = 0; index < limit; index++) {
                JSONObject item = normalizeAlarm(requested.optJSONObject(index));
                if (item == null || item.optLong("triggerAt", 0) <= now + 1_000L) continue;
                scheduleOne(context, item);
                accepted.put(item);
            }

            saveStored(context, accepted);
            return resultJson(true, "scheduled", accepted.length());
        } catch (Exception error) {
            return resultJson(false, error.getMessage() == null ? "schedule_failed" : error.getMessage(), 0);
        }
    }

    static String cancelAll(Context context) {
        cancelStoredPendingIntents(context);
        saveStored(context, new JSONArray());
        return resultJson(true, "cancelled", 0);
    }

    static String scheduleTest(Context context, long triggerAtMillis) {
        try {
            JSONObject item = new JSONObject();
            item.put("id", TEST_ALARM_ID);
            item.put("triggerAt", triggerAtMillis);
            item.put("title", "교대달력 알람 테스트");
            item.put("body", "선택한 소리·진동 방식과 잠금화면 표시를 확인하세요.");
            item.put("shiftType", "테스트");
            item.put("workDate", "");
            item.put("alarmMode", AlarmSettingsStore.loadMode(context));
            scheduleOne(context, item);
            return resultJson(true, "test_scheduled", 1);
        } catch (Exception error) {
            return resultJson(false, error.getMessage() == null ? "test_failed" : error.getMessage(), 0);
        }
    }

    static void rescheduleStored(Context context) {
        try {
            JSONArray stored = getStored(context);
            JSONArray future = new JSONArray();
            long now = System.currentTimeMillis();

            for (int index = 0; index < stored.length(); index++) {
                JSONObject item = normalizeAlarm(stored.optJSONObject(index));
                if (item == null || item.optLong("triggerAt", 0) <= now + 1_000L) continue;
                scheduleOne(context, item);
                future.put(item);
            }
            saveStored(context, future);
        } catch (Exception ignored) {}
    }

    static void removeStoredAlarm(Context context, int alarmId) {
        JSONArray stored = getStored(context);
        JSONArray remaining = new JSONArray();
        for (int index = 0; index < stored.length(); index++) {
            JSONObject item = stored.optJSONObject(index);
            if (item == null || item.optInt("id", Integer.MIN_VALUE) == alarmId) continue;
            remaining.put(item);
        }
        saveStored(context, remaining);
    }

    static int getStoredFutureCount(Context context) {
        JSONArray stored = getStored(context);
        long now = System.currentTimeMillis();
        int count = 0;
        for (int index = 0; index < stored.length(); index++) {
            JSONObject item = stored.optJSONObject(index);
            if (item != null && item.optLong("triggerAt", 0) > now) count++;
        }
        return count;
    }

    static String getNextStoredAlarmSummary(Context context) {
        JSONArray stored = getStored(context);
        long now = System.currentTimeMillis();
        JSONObject next = null;
        long nextTrigger = Long.MAX_VALUE;

        for (int index = 0; index < stored.length(); index++) {
            JSONObject item = stored.optJSONObject(index);
            if (item == null) continue;
            long trigger = item.optLong("triggerAt", 0);
            if (trigger > now && trigger < nextTrigger) {
                next = item;
                nextTrigger = trigger;
            }
        }

        if (next == null) return "없음";

        ZonedDateTime dateTime = Instant.ofEpochMilli(nextTrigger)
            .atZone(ZoneId.systemDefault());
        String date = DateTimeFormatter.ofPattern("MM-dd HH:mm")
            .format(dateTime);
        return date + " · " + next.optString("shiftType", "근무");
    }

    private static void scheduleOne(Context context, JSONObject item) throws Exception {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) throw new IllegalStateException("alarm_manager_unavailable");
        if (Build.VERSION.SDK_INT >= 31 && !manager.canScheduleExactAlarms()) {
            throw new SecurityException("exact_alarm_permission_required");
        }

        int id = item.getInt("id");
        long triggerAt = item.getLong("triggerAt");

        Intent alarmIntent = new Intent(context, AlarmReceiver.class);
        alarmIntent.setAction("com.kangj.shiftcalendar.ALARM." + id);
        alarmIntent.putExtra("alarmId", id);
        alarmIntent.putExtra("title", item.optString("title", "교대달력 근무 알람"));
        alarmIntent.putExtra("body", item.optString("body", "설정한 기상 시각입니다."));
        alarmIntent.putExtra("shiftType", item.optString("shiftType", "근무"));
        alarmIntent.putExtra("workDate", item.optString("workDate", ""));
        alarmIntent.putExtra(
            "alarmMode",
            item.optString("alarmMode", AlarmSettingsStore.MODE_SOUND_VIBRATE)
        );

        PendingIntent operation = PendingIntent.getBroadcast(
            context,
            id,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent showIntent = new Intent(context, MainActivity.class);
        showIntent.setAction("com.kangj.shiftcalendar.SHOW." + id);
        showIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent showOperation = PendingIntent.getActivity(
            context,
            id ^ 0x40000000,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(triggerAt, showOperation);
        manager.setAlarmClock(info, operation);
    }

    private static void cancelStoredPendingIntents(Context context) {
        AlarmManager manager = context.getSystemService(AlarmManager.class);
        if (manager == null) return;

        JSONArray stored = getStored(context);
        for (int index = 0; index < stored.length(); index++) {
            JSONObject item = stored.optJSONObject(index);
            if (item == null) continue;
            int id = item.optInt("id", Integer.MIN_VALUE);
            if (id == Integer.MIN_VALUE) continue;

            Intent intent = new Intent(context, AlarmReceiver.class);
            intent.setAction("com.kangj.shiftcalendar.ALARM." + id);
            PendingIntent operation = PendingIntent.getBroadcast(
                context,
                id,
                intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
            );
            if (operation != null) {
                manager.cancel(operation);
                operation.cancel();
            }
        }
    }

    private static JSONObject normalizeAlarm(JSONObject item) {
        if (item == null) return null;
        try {
            int id = item.getInt("id");
            long triggerAt = item.getLong("triggerAt");
            if (id == 0 || triggerAt <= 0) return null;

            JSONObject normalized = new JSONObject();
            normalized.put("id", id);
            normalized.put("triggerAt", triggerAt);
            normalized.put("title", item.optString("title", "교대달력 근무 알람"));
            normalized.put("body", item.optString("body", "설정한 기상 시각입니다."));
            normalized.put("shiftType", item.optString("shiftType", "근무"));
            normalized.put("workDate", item.optString("workDate", ""));
            normalized.put(
                "alarmMode",
                item.optString("alarmMode", AlarmSettingsStore.MODE_SOUND_VIBRATE)
            );
            return normalized;
        } catch (Exception error) {
            return null;
        }
    }

    private static JSONArray getStored(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = preferences.getString(KEY_ALARMS, "[]");
        try {
            return new JSONArray(raw == null ? "[]" : raw);
        } catch (Exception error) {
            return new JSONArray();
        }
    }

    private static void saveStored(Context context, JSONArray alarms) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ALARMS, alarms == null ? "[]" : alarms.toString())
            .apply();
    }

    static String resultJson(boolean ok, String code, int count) {
        JSONObject result = new JSONObject();
        try {
            result.put("ok", ok);
            result.put("code", code == null ? "" : code);
            result.put("count", count);
        } catch (Exception ignored) {}
        return result.toString();
    }
}
