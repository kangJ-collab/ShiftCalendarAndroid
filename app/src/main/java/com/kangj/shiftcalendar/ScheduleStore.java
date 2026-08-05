package com.kangj.shiftcalendar;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ScheduleStore {
    private static final String PREFS = "shiftcalendar_native";
    private static final String KEY_SCHEDULE = "schedule_json";

    private ScheduleStore() {}

    public static void saveSchedule(Context context, String json) throws Exception {
        JSONArray array = new JSONArray(json == null ? "[]" : json);
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_SCHEDULE, array.toString()).apply();
    }

    public static JSONArray getSchedule(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_SCHEDULE, "[]");
        try { return new JSONArray(raw); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    public static Map<String, String> asMap(Context context) {
        Map<String, String> map = new LinkedHashMap<>();
        JSONArray array = getSchedule(context);
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) continue;
            String date = item.optString("date", "");
            String shift = item.optString("shift", "");
            if (!date.isEmpty()) map.put(date, shift);
        }
        return map;
    }

    public static List<String> discoveredShiftTypes(Context context) {
        List<String> values = new ArrayList<>();
        for (String shift : asMap(context).values()) {
            if (!shift.isEmpty() && !values.contains(shift)) values.add(shift);
        }
        return values;
    }
}
