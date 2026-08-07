package com.kangj.shiftcalendar;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

final class UpdateManager {
    private static final String RELEASE_API =
        "https://api.github.com/repos/kangJ-collab/ShiftCalendarAndroid/releases/latest";
    private static final String PREFS = "shiftcalendar_updater";
    private static final String KEY_DOWNLOAD_ID = "pending_download_id";

    private final Activity activity;
    private final DownloadManager downloadManager;
    private final SharedPreferences prefs;
    private BroadcastReceiver downloadReceiver;

    UpdateManager(Activity activity) {
        this.activity = activity;
        this.downloadManager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
        this.prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        registerDownloadReceiver();
    }

    void checkForUpdate() {
        new Thread(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(RELEASE_API);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "ShiftCalendarAndroid-Updater");

                int code = connection.getResponseCode();
                if (code < 200 || code >= 300) return;

                StringBuilder body = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                }

                JSONObject release = new JSONObject(body.toString());
                String tag = release.optString("tag_name", "");
                String latestVersion = tag.startsWith("v") ? tag.substring(1) : tag;
                if (latestVersion.isEmpty() || !isNewer(latestVersion, currentVersion())) return;

                JSONArray assets = release.optJSONArray("assets");
                if (assets == null) return;

                String apkUrl = null;
                String apkName = null;
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.optJSONObject(i);
                    if (asset == null) continue;
                    String name = asset.optString("name", "");
                    String downloadUrl = asset.optString("browser_download_url", "");
                    if (name.toLowerCase().endsWith(".apk") && !downloadUrl.isEmpty()) {
                        apkName = name;
                        apkUrl = downloadUrl;
                        break;
                    }
                }
                if (apkUrl == null) return;

                final String version = latestVersion;
                final String finalUrl = apkUrl;
                final String finalName = apkName == null
                    ? "ShiftCalendarAndroid-v" + version + ".apk"
                    : apkName;

                activity.runOnUiThread(() -> showUpdateDialog(version, finalUrl, finalName));
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
            }
        }, "ShiftCalendarUpdateCheck").start();
    }

    private void showUpdateDialog(String version, String apkUrl, String apkName) {
        if (activity.isFinishing() || (Build.VERSION.SDK_INT >= 17 && activity.isDestroyed())) return;

        new AlertDialog.Builder(activity)
            .setTitle("새 버전 v" + version)
            .setMessage("교대달력 새 버전이 있습니다. 지금 업데이트할까요?")
            .setNegativeButton("나중에", null)
            .setPositiveButton("업데이트", (dialog, which) -> startDownload(apkUrl, apkName))
            .show();
    }

    private void startDownload(String apkUrl, String apkName) {
        if (downloadManager == null) {
            openInBrowser(apkUrl);
            return;
        }

        try {
            File dir = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (dir != null) {
                File old = new File(dir, apkName);
                if (old.exists()) old.delete();
            }

            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(apkUrl));
            request.setTitle("교대달력 업데이트");
            request.setDescription(apkName);
            request.setMimeType("application/vnd.android.package-archive");
            request.setAllowedOverMetered(true);
            request.setAllowedOverRoaming(false);
            request.setNotificationVisibility(
                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
            );
            request.setDestinationInExternalFilesDir(
                activity,
                Environment.DIRECTORY_DOWNLOADS,
                apkName
            );

            long id = downloadManager.enqueue(request);
            prefs.edit().putLong(KEY_DOWNLOAD_ID, id).apply();
            Toast.makeText(activity, "업데이트 APK를 다운로드합니다.", Toast.LENGTH_SHORT).show();
        } catch (Exception error) {
            openInBrowser(apkUrl);
        }
    }

    void resumePendingInstall() {
        long id = prefs.getLong(KEY_DOWNLOAD_ID, -1L);
        if (id <= 0 || downloadManager == null) return;

        DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
        try (android.database.Cursor cursor = downloadManager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) return;
            int statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
            if (statusIndex < 0) return;
            int status = cursor.getInt(statusIndex);
            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                installDownloadedApk(id);
            } else if (status == DownloadManager.STATUS_FAILED) {
                prefs.edit().remove(KEY_DOWNLOAD_ID).apply();
            }
        } catch (Exception ignored) {
        }
    }

    private void installDownloadedApk(long downloadId) {
        if (Build.VERSION.SDK_INT >= 26 &&
            !activity.getPackageManager().canRequestPackageInstalls()) {
            try {
                Intent settingsIntent = new Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + activity.getPackageName())
                );
                activity.startActivity(settingsIntent);
                Toast.makeText(
                    activity,
                    "교대달력의 앱 설치 허용을 켠 뒤 돌아오세요.",
                    Toast.LENGTH_LONG
                ).show();
            } catch (Exception ignored) {
            }
            return;
        }

        try {
            Uri apkUri = downloadManager.getUriForDownloadedFile(downloadId);
            if (apkUri == null) return;

            Intent install = new Intent(Intent.ACTION_VIEW);
            install.setDataAndType(apkUri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(install);
            prefs.edit().remove(KEY_DOWNLOAD_ID).apply();
        } catch (Exception error) {
            Toast.makeText(activity, "설치 화면을 열지 못했습니다.", Toast.LENGTH_LONG).show();
        }
    }

    private void registerDownloadReceiver() {
        downloadReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) return;
                long completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L);
                long pendingId = prefs.getLong(KEY_DOWNLOAD_ID, -1L);
                if (completedId == pendingId && completedId > 0) {
                    installDownloadedApk(completedId);
                }
            }
        };

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= 33) {
            activity.registerReceiver(downloadReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            activity.registerReceiver(downloadReceiver, filter);
        }
    }

    void destroy() {
        if (downloadReceiver == null) return;
        try {
            activity.unregisterReceiver(downloadReceiver);
        } catch (Exception ignored) {
        }
        downloadReceiver = null;
    }

    private String currentVersion() {
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(
                activity.getPackageName(), 0
            );
            return info.versionName == null ? "0" : info.versionName;
        } catch (Exception ignored) {
            return "0";
        }
    }

    private boolean isNewer(String latest, String current) {
        String[] left = latest.split("\\.");
        String[] right = current.split("\\.");
        int length = Math.max(left.length, right.length);

        for (int i = 0; i < length; i++) {
            int a = i < left.length ? numberPart(left[i]) : 0;
            int b = i < right.length ? numberPart(right[i]) : 0;
            if (a != b) return a > b;
        }
        return false;
    }

    private int numberPart(String value) {
        try {
            String digits = value.replaceAll("[^0-9].*$", "");
            return digits.isEmpty() ? 0 : Integer.parseInt(digits);
        } catch (Exception ignored) {
            return 0;
        }
    }

    private void openInBrowser(String url) {
        try {
            activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception ignored) {
        }
    }
}
