package com.kangj.shiftcalendar;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class AndroidAlarmBridge {
    private static final int NOTIFICATION_PERMISSION_REQUEST = 4012;
    private final MainActivity activity;

    AndroidAlarmBridge(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public String getPermissionState() {
        JSONObject result = new JSONObject();
        try {
            result.put("native", true);
            result.put("sdk", Build.VERSION.SDK_INT);
            result.put("notificationGranted", hasNotificationPermission());
            result.put("exactAlarmGranted", canScheduleExactAlarms());
            result.put("fullScreenGranted", canUseFullScreenIntent());
            result.put("scheduledCount", AlarmScheduler.getStoredFutureCount(activity));
        } catch (Exception ignored) {
        }
        return result.toString();
    }

    @JavascriptInterface
    public void requestNotificationPermission() {
        activity.runOnUiThread(() -> {
            if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) {
                activity.requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST
                );
            } else {
                activity.notifyPermissionStateChanged();
            }
        });
    }

    @JavascriptInterface
    public void openNotificationSettings() {
        activity.runOnUiThread(activity::openAppSettings);
    }

    @JavascriptInterface
    public void openExactAlarmSettings() {
        activity.runOnUiThread(() -> {
            if (Build.VERSION.SDK_INT < 31 || canScheduleExactAlarms()) {
                activity.notifyPermissionStateChanged();
                return;
            }
            try {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
            } catch (Exception error) {
                activity.openAppSettings();
            }
        });
    }

    @JavascriptInterface
    public void openFullScreenSettings() {
        activity.runOnUiThread(() -> {
            if (Build.VERSION.SDK_INT < 34 || canUseFullScreenIntent()) {
                activity.notifyPermissionStateChanged();
                return;
            }
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT);
                intent.setData(Uri.parse("package:" + activity.getPackageName()));
                activity.startActivity(intent);
            } catch (Exception error) {
                activity.openAppSettings();
            }
        });
    }

    @JavascriptInterface
    public String scheduleAlarms(String alarmsJson) {
        if (!hasNotificationPermission()) {
            return AlarmScheduler.resultJson(false, "notification_permission_required", 0);
        }
        if (!canScheduleExactAlarms()) {
            return AlarmScheduler.resultJson(false, "exact_alarm_permission_required", 0);
        }
        return AlarmScheduler.replaceAlarms(activity, alarmsJson);
    }

    @JavascriptInterface
    public String cancelAllAlarms() {
        return AlarmScheduler.cancelAll(activity);
    }

    @JavascriptInterface
    public String scheduleTestAlarm(long triggerAtMillis) {
        if (!hasNotificationPermission()) {
            return AlarmScheduler.resultJson(false, "notification_permission_required", 0);
        }
        if (!canScheduleExactAlarms()) {
            return AlarmScheduler.resultJson(false, "exact_alarm_permission_required", 0);
        }
        return AlarmScheduler.scheduleTest(activity, triggerAtMillis);
    }

    @JavascriptInterface
    public String saveTextFile(String filename, String content) {
        JSONObject result = new JSONObject();
        try {
            if (Build.VERSION.SDK_INT < 29) {
                result.put("ok", false);
                result.put("message", "Android 10 이상에서 다운로드 폴더 저장을 지원합니다.");
                return result.toString();
            }

            String safeName = sanitizeFilename(filename);
            ContentResolver resolver = activity.getContentResolver();
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, safeName);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
            values.put(
                MediaStore.Downloads.RELATIVE_PATH,
                Environment.DIRECTORY_DOWNLOADS + "/교대달력"
            );
            values.put(MediaStore.Downloads.IS_PENDING, 1);

            Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IllegalStateException("다운로드 파일을 만들 수 없습니다.");

            try (OutputStream stream = resolver.openOutputStream(uri, "w")) {
                if (stream == null) throw new IllegalStateException("파일을 열 수 없습니다.");
                stream.write(content.getBytes(StandardCharsets.UTF_8));
                stream.flush();
            }

            values.clear();
            values.put(MediaStore.Downloads.IS_PENDING, 0);
            resolver.update(uri, values, null, null);

            result.put("ok", true);
            result.put("filename", safeName);
            result.put("message", "다운로드/교대달력 폴더에 저장했습니다.");
            activity.runOnUiThread(() -> Toast.makeText(
                activity,
                "다운로드/교대달력 폴더에 저장했습니다.",
                Toast.LENGTH_LONG
            ).show());
        } catch (Exception error) {
            try {
                result.put("ok", false);
                result.put("message", error.getMessage() == null ? "파일 저장 실패" : error.getMessage());
            } catch (Exception ignored) {
            }
        }
        return result.toString();
    }

    private boolean hasNotificationPermission() {
        return Build.VERSION.SDK_INT < 33 ||
            activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean canScheduleExactAlarms() {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager manager = activity.getSystemService(AlarmManager.class);
        return manager != null && manager.canScheduleExactAlarms();
    }

    private boolean canUseFullScreenIntent() {
        if (Build.VERSION.SDK_INT < 34) return true;
        NotificationManager manager = activity.getSystemService(NotificationManager.class);
        return manager != null && manager.canUseFullScreenIntent();
    }

    private static String sanitizeFilename(String filename) {
        String value = filename == null ? "교대달력_BACKUP.json" : filename;
        value = value.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (!value.toLowerCase().endsWith(".json")) value += ".json";
        return value;
    }
}
