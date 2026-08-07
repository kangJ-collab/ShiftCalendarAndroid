package com.kangj.shiftcalendar.widget;

import android.graphics.Color;

import com.kangj.shiftcalendar.ScheduleStore;

final class WidgetStyle {
    private WidgetStyle() {}

    static String label(ScheduleStore.ScheduleEntry entry, String fallback) {
        if (entry == null || entry.label.isEmpty()) return fallback;
        return entry.label;
    }

    static int background(ScheduleStore.ScheduleEntry entry, int fallback) {
        return parse(entry == null ? "" : entry.backgroundColor, fallback);
    }

    static int text(ScheduleStore.ScheduleEntry entry, int fallback) {
        return parse(entry == null ? "" : entry.textColor, fallback);
    }

    private static int parse(String value, int fallback) {
        if (value == null || value.trim().isEmpty()) return fallback;
        try {
            return Color.parseColor(value.trim());
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }
}
