package com.kangj.shiftcalendar;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.provider.Settings;

public class AlarmRingingService extends Service {
    static final String ACTION_START = "com.kangj.shiftcalendar.action.START_ALARM";
    static final String ACTION_STOP = "com.kangj.shiftcalendar.action.STOP_ALARM";
    private static final String CHANNEL_ID = "shift_alarm_channel_v2";
    private static final String WATCH_CHANNEL_ID = "shift_alarm_watch_v1";
    private static final int NOTIFICATION_ID = 7001;
    private static final int WATCH_NOTIFICATION_ID = 7005;
    private static final long MAX_RING_MILLIS = 10 * 60 * 1_000L;

    private MediaPlayer mediaPlayer;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable autoStopRunnable = this::stopSelf;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        Intent source = intent == null ? new Intent() : intent;
        Notification notification = buildNotification(source);
        startForeground(NOTIFICATION_ID, notification);
        postWatchBridgeNotification(source);

        String mode = source.getStringExtra("alarmMode");
        if (mode == null || mode.isEmpty()) {
            mode = AlarmSettingsStore.MODE_SOUND_VIBRATE;
        }
        startRinging(mode);

        handler.removeCallbacks(autoStopRunnable);
        handler.postDelayed(autoStopRunnable, MAX_RING_MILLIS);
        return START_NOT_STICKY;
    }

    private Notification buildNotification(Intent source) {
        String title = source.getStringExtra("title");
        String body = source.getStringExtra("body");
        String shiftType = source.getStringExtra("shiftType");
        String workDate = source.getStringExtra("workDate");

        Intent fullScreenIntent = new Intent(this, AlarmActivity.class);
        fullScreenIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        );
        fullScreenIntent.putExtra("title", title);
        fullScreenIntent.putExtra("body", body);
        fullScreenIntent.putExtra("shiftType", shiftType);
        fullScreenIntent.putExtra("workDate", workDate);

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
            this,
            7002,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, AlarmActionReceiver.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
            this,
            7003,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, CHANNEL_ID)
            : new Notification.Builder(this);

        return builder
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle(title == null || title.isEmpty() ? "교대달력 근무 알람" : title)
            .setContentText(body == null || body.isEmpty() ? "설정한 기상 시각입니다." : body)
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreenPendingIntent)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .addAction(new Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.stop_alarm),
                stopPendingIntent
            ).build())
            .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT < 26) return;

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;

        NotificationChannel channel = new NotificationChannel(
            CHANNEL_ID,
            getString(R.string.alarm_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(getString(R.string.alarm_channel_description));
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        channel.enableVibration(false);
        channel.setSound(null, null);
        manager.createNotificationChannel(channel);

        NotificationChannel watchChannel = new NotificationChannel(
            WATCH_CHANNEL_ID,
            "워치 알람 알림",
            NotificationManager.IMPORTANCE_HIGH
        );
        watchChannel.setDescription("교대달력 알람을 워치로 전달합니다.");
        watchChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        watchChannel.setSound(null, null);
        watchChannel.enableVibration(true);
        watchChannel.setVibrationPattern(new long[]{0, 500, 250, 500, 250});
        manager.createNotificationChannel(watchChannel);
    }

    private void postWatchBridgeNotification(Intent source) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;

        String title = source.getStringExtra("title");
        String body = source.getStringExtra("body");

        Intent fullScreenIntent = new Intent(this, AlarmActivity.class);
        fullScreenIntent.addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        );
        fullScreenIntent.putExtras(source);
        PendingIntent contentIntent = PendingIntent.getActivity(
            this,
            7006,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, AlarmActionReceiver.class);
        stopIntent.setAction(ACTION_STOP);
        PendingIntent stopPendingIntent = PendingIntent.getBroadcast(
            this,
            7007,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
            ? new Notification.Builder(this, WATCH_CHANNEL_ID)
            : new Notification.Builder(this);

        Notification notification = builder
            .setSmallIcon(R.drawable.ic_alarm_notification)
            .setContentTitle(title == null || title.isEmpty()
                ? "교대달력 근무 알람" : title)
            .setContentText(body == null || body.isEmpty()
                ? "교대달력 알람이 울리고 있습니다." : body)
            .setCategory(Notification.CATEGORY_ALARM)
            .setPriority(Notification.PRIORITY_MAX)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(false)
            .setAutoCancel(true)
            .setLocalOnly(false)
            .setContentIntent(contentIntent)
            .setVibrate(new long[]{0, 500, 250, 500, 250})
            .addAction(new Notification.Action.Builder(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.stop_alarm),
                stopPendingIntent
            ).build())
            .build();

        manager.notify(WATCH_NOTIFICATION_ID, notification);
    }

    private void startRinging(String mode) {
        stopRinging();
        acquireWakeLock();

        if (!AlarmSettingsStore.MODE_VIBRATE.equals(mode)) {
            startAlarmSound();
        }
        if (!AlarmSettingsStore.MODE_SOUND.equals(mode)) {
            startVibration();
        }
    }

    private void startAlarmSound() {
        String storedUri = AlarmSettingsStore.loadSoundUri(this);
        if (storedUri != null && !storedUri.isEmpty()) {
            try {
                if (tryPlayAlarmSound(Uri.parse(storedUri))) return;
            } catch (Exception ignored) {}
        }

        Uri alarmUri = android.media.RingtoneManager.getDefaultUri(
            android.media.RingtoneManager.TYPE_ALARM);
        if (alarmUri == null) {
            alarmUri = android.media.RingtoneManager.getDefaultUri(
                android.media.RingtoneManager.TYPE_NOTIFICATION);
        }
        if (alarmUri == null) alarmUri = Settings.System.DEFAULT_ALARM_ALERT_URI;
        tryPlayAlarmSound(alarmUri);
    }

    private boolean tryPlayAlarmSound(Uri alarmUri) {
        if (alarmUri == null) return false;
        try {
            MediaPlayer player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build());
            player.setDataSource(this, alarmUri);
            player.setLooping(true);
            player.prepare();
            player.start();
            mediaPlayer = player;
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    private void startVibration() {
        if (Build.VERSION.SDK_INT >= 31) {
            VibratorManager manager = getSystemService(VibratorManager.class);
            vibrator = manager == null ? null : manager.getDefaultVibrator();
        } else {
            vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        }

        if (vibrator == null || !vibrator.hasVibrator()) return;
        long[] pattern = {0, 700, 350, 700, 350};
        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
    }

    private void acquireWakeLock() {
        PowerManager manager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (manager == null) return;
        wakeLock = manager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ShiftCalendar:AlarmWakeLock"
        );
        wakeLock.acquire(MAX_RING_MILLIS + 30_000L);
    }

    private void stopRinging() {
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(WATCH_NOTIFICATION_ID);

        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
            } catch (Exception ignored) {}
            mediaPlayer.release();
            mediaPlayer = null;
        }

        if (vibrator != null) {
            vibrator.cancel();
            vibrator = null;
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = null;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(autoStopRunnable);
        stopRinging();
        stopForeground(STOP_FOREGROUND_REMOVE);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
