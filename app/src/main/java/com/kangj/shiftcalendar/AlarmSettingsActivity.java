package com.kangj.shiftcalendar;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AlarmSettingsActivity extends Activity {
    private static final int RINGTONE_PICKER_REQUEST = 7004;
    private final Map<String, List<String>> values = new LinkedHashMap<>();

    private LinearLayout listContainer;
    private RadioGroup modeGroup;

    private UpdateManager updateManager;
    private TextView currentVersionText;
    private TextView latestVersionText;
    private Button updateButton;
    private TextView scheduleStatusText;
    private TextView alarmSoundName;

    private TextView scheduleSummary;
    private TextView updateSummary;
    private TextView modeSummary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm_settings);
        applyAndroid15Insets(findViewById(R.id.alarmSettingsRoot));

        listContainer = findViewById(R.id.alarmSettingsList);
        modeGroup = findViewById(R.id.alarmModeGroup);

        currentVersionText = findViewById(R.id.appCurrentVersionText);
        latestVersionText = findViewById(R.id.appLatestVersionText);
        updateButton = findViewById(R.id.appUpdateButton);
        scheduleStatusText = findViewById(R.id.alarmScheduleStatus);
        alarmSoundName = findViewById(R.id.alarmSoundName);

        scheduleSummary = findViewById(R.id.alarmScheduleSummary);
        updateSummary = findViewById(R.id.updateSummary);
        modeSummary = findViewById(R.id.modeSummary);

        values.putAll(AlarmSettingsStore.load(this));
        restoreMode();

        setupAccordion(
            R.id.scheduleAccordionHeader,
            R.id.scheduleAccordionContent,
            R.id.scheduleAccordionArrow
        );
        setupAccordion(
            R.id.updateAccordionHeader,
            R.id.updateAccordionContent,
            R.id.updateAccordionArrow
        );
        setupAccordion(
            R.id.modeAccordionHeader,
            R.id.modeAccordionContent,
            R.id.modeAccordionArrow
        );

        findViewById(R.id.alarmPermissionButton)
            .setOnClickListener(v -> requestRequiredPermissions());
        findViewById(R.id.alarmSaveButton)
            .setOnClickListener(v -> save());
        findViewById(R.id.alarmCloseButton)
            .setOnClickListener(v -> finish());
        findViewById(R.id.alarmTestButton)
            .setOnClickListener(v -> testAlarm());
        findViewById(R.id.alarmSoundPickerButton)
            .setOnClickListener(v -> openAlarmSoundPicker());

        updateManager = new UpdateManager(this);

        currentVersionText.setText(
            "현재 버전  v" + updateManager.getCurrentVersion());

        updateButton.setOnClickListener(v -> {
            if (updateManager.hasCachedUpdate()) {
                updateManager.startCachedUpdate();
            } else {
                refreshVersionInfo(true);
            }
        });

        render();
        renderSoundSelection();
        refreshModeSummary();
        refreshScheduleStatus();
        refreshVersionInfo(false);
    }

    private void setupAccordion(int headerId, int contentId, int arrowId) {
        View header = findViewById(headerId);
        View content = findViewById(contentId);
        TextView arrow = findViewById(arrowId);

        content.setVisibility(View.GONE);
        arrow.setText("⌄");

        header.setOnClickListener(v -> {
            boolean opening = content.getVisibility() != View.VISIBLE;
            content.setVisibility(opening ? View.VISIBLE : View.GONE);
            arrow.setText(opening ? "⌃" : "⌄");
        });
    }

    private void refreshVersionInfo(boolean userRequested) {
        if (updateManager == null) return;

        updateButton.setEnabled(false);
        updateButton.setText("확인 중...");
        latestVersionText.setText("최신 버전 확인 중...");
        if (updateSummary != null) {
            updateSummary.setText("버전 확인 중...");
        }

        updateManager.checkForUpdate(info -> {
            currentVersionText.setText(
                "현재 버전  v" + info.currentVersion);

            if (!info.success) {
                latestVersionText.setText(
                    "최신 버전 확인 실패 · " + info.errorMessage);
                updateButton.setText("다시 확인");
                updateButton.setEnabled(true);

                if (updateSummary != null) {
                    updateSummary.setText("업데이트 확인 실패");
                }

                if (userRequested) {
                    Toast.makeText(
                        this,
                        "업데이트 정보를 확인하지 못했습니다.",
                        Toast.LENGTH_SHORT).show();
                }
                return;
            }

            if (info.updateAvailable) {
                latestVersionText.setText(
                    "최신 버전  v" + info.latestVersion +
                    " · 업데이트 가능");
                updateButton.setText(
                    "v" + info.latestVersion + " 업데이트");
                updateButton.setEnabled(true);

                if (updateSummary != null) {
                    updateSummary.setText(
                        "v" + info.currentVersion +
                        " → v" + info.latestVersion);
                }
            } else {
                latestVersionText.setText(
                    "최신 버전  v" + info.latestVersion +
                    " · 최신입니다");
                updateButton.setText("업데이트 확인");
                updateButton.setEnabled(true);

                if (updateSummary != null) {
                    updateSummary.setText(
                        "v" + info.currentVersion + " · 최신");
                }

                if (userRequested) {
                    Toast.makeText(
                        this,
                        "현재 최신 버전입니다.",
                        Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void applyAndroid15Insets(View root) {
        if (root == null || Build.VERSION.SDK_INT < 35) return;

        final int baseLeft = root.getPaddingLeft();
        final int baseTop = root.getPaddingTop();
        final int baseRight = root.getPaddingRight();
        final int baseBottom = root.getPaddingBottom();

        root.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets safeInsets = insets.getInsets(
                WindowInsets.Type.systemBars() |
                WindowInsets.Type.displayCutout());

            view.setPadding(
                baseLeft + safeInsets.left,
                baseTop + safeInsets.top,
                baseRight + safeInsets.right,
                baseBottom + safeInsets.bottom);
            return insets;
        });

        root.requestApplyInsets();
    }

    private void restoreMode() {
        String mode = AlarmSettingsStore.loadMode(this);

        if (AlarmSettingsStore.MODE_SOUND.equals(mode)) {
            modeGroup.check(R.id.alarmModeSound);
        } else if (AlarmSettingsStore.MODE_VIBRATE.equals(mode)) {
            modeGroup.check(R.id.alarmModeVibrate);
        } else {
            modeGroup.check(R.id.alarmModeSoundVibrate);
        }
    }

    private String selectedMode() {
        int checkedId = modeGroup.getCheckedRadioButtonId();

        if (checkedId == R.id.alarmModeSound) {
            return AlarmSettingsStore.MODE_SOUND;
        }
        if (checkedId == R.id.alarmModeVibrate) {
            return AlarmSettingsStore.MODE_VIBRATE;
        }
        return AlarmSettingsStore.MODE_SOUND_VIBRATE;
    }

    private void render() {
        listContainer.removeAllViews();

        for (Map.Entry<String, List<String>> entry : values.entrySet()) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(14), dp(12), dp(14), dp(12));

            LinearLayout.LayoutParams cardParams =
                new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(0, 0, 0, dp(10));
            card.setLayoutParams(cardParams);
            card.setBackgroundResource(R.drawable.bg_alarm_card);

            TextView title = new TextView(this);
            title.setText(entry.getKey());
            title.setTextSize(17);
            title.setTextColor(0xff202428);
            title.setTypeface(null, android.graphics.Typeface.BOLD);
            card.addView(title);

            LinearLayout times = new LinearLayout(this);
            times.setOrientation(LinearLayout.VERTICAL);
            times.setPadding(0, dp(8), 0, 0);
            card.addView(times);

            if (entry.getValue().isEmpty()) {
                TextView empty = new TextView(this);
                empty.setText("등록된 알람이 없습니다.");
                empty.setTextColor(0xff68727a);
                empty.setPadding(0, dp(4), 0, dp(6));
                times.addView(empty);
            }

            for (String time : new ArrayList<>(entry.getValue())) {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(0, dp(4), 0, dp(4));

                Button timeButton = new Button(this);
                timeButton.setText(time);
                timeButton.setAllCaps(false);
                timeButton.setOnClickListener(
                    v -> editTime(entry.getKey(), time));

                LinearLayout.LayoutParams timeParams =
                    new LinearLayout.LayoutParams(0, dp(48), 1f);
                row.addView(timeButton, timeParams);

                Button delete = new Button(this);
                delete.setText("삭제");
                delete.setAllCaps(false);
                delete.setOnClickListener(v -> {
                    values.get(entry.getKey()).remove(time);
                    render();
                });

                LinearLayout.LayoutParams deleteParams =
                    new LinearLayout.LayoutParams(dp(84), dp(48));
                deleteParams.setMargins(dp(8), 0, 0, 0);
                row.addView(delete, deleteParams);
                times.addView(row);
            }

            Button add = new Button(this);
            add.setText("+ 알람 추가");
            add.setAllCaps(false);
            add.setOnClickListener(v -> addTime(entry.getKey()));
            card.addView(
                add,
                new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(48)));

            listContainer.addView(card);
        }
    }

    private void addTime(String shift) {
        TimePickerDialog dialog = new TimePickerDialog(
            this,
            (view, hour, minute) -> {
                String value = String.format(
                    java.util.Locale.KOREA,
                    "%02d:%02d", hour, minute);
                List<String> list = values.get(shift);
                if (!list.contains(value)) list.add(value);
                list.sort(String::compareTo);
                render();
            },
            6, 0, true);

        dialog.setTitle(shift + " 기상 시각");
        dialog.show();
    }

    private void editTime(String shift, String oldValue) {
        String[] parts = oldValue.split(":");

        TimePickerDialog dialog = new TimePickerDialog(
            this,
            (view, hour, minute) -> {
                String value = String.format(
                    java.util.Locale.KOREA,
                    "%02d:%02d", hour, minute);
                List<String> list = values.get(shift);
                list.remove(oldValue);
                if (!list.contains(value)) list.add(value);
                list.sort(String::compareTo);
                render();
            },
            Integer.parseInt(parts[0]),
            Integer.parseInt(parts[1]),
            true);

        dialog.setTitle(shift + " 기상 시각 변경");
        dialog.show();
    }

    private void save() {
        AlarmSettingsStore.saveMode(this, selectedMode());
        AlarmSettingsStore.save(this, values);
        com.kangj.shiftcalendar.widget.WidgetUpdater.updateAll(this);
        refreshScheduleStatus();
        renderSoundSelection();
        refreshModeSummary();

        Toast.makeText(
            this,
            "알람 방식과 근무별 알람을 저장하고 다시 예약했습니다.",
            Toast.LENGTH_LONG).show();
    }

    private void testAlarm() {
        requestRequiredPermissions();
        AlarmSettingsStore.saveMode(this, selectedMode());

        try {
            String result = AlarmScheduler.scheduleTest(
                this, System.currentTimeMillis() + 10_000L);

            Toast.makeText(
                this,
                result.contains("\"ok\":true")
                    ? "10초 뒤 휴대폰 알람 + 워치 알림을 확인하세요."
                    : "권한을 확인해주세요.",
                Toast.LENGTH_LONG).show();
            refreshScheduleStatus();
        } catch (Exception error) {
            Toast.makeText(
                this,
                "테스트 알람을 예약하지 못했습니다.",
                Toast.LENGTH_LONG).show();
        }
    }

    private void requestRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                7001);
        }

        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager manager = getSystemService(AlarmManager.class);

            if (manager != null &&
                !manager.canScheduleExactAlarms()) {
                try {
                    Intent intent = new Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                    intent.setData(
                        Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private void refreshScheduleStatus() {
        if (scheduleStatusText == null) return;

        int count = AlarmScheduler.getStoredFutureCount(this);
        String permissionReason =
            AlarmSettingsStore.getPermissionBlockReason(this);

        if (!permissionReason.isEmpty()) {
            scheduleStatusText.setText(
                "앞으로 예약된 기상 알람 " + count + "회\n" +
                permissionReason);

            if (scheduleSummary != null) {
                scheduleSummary.setText(
                    "총 " + count + "회 · " + permissionReason);
            }
            return;
        }

        if (count == 0) {
            scheduleStatusText.setText(
                "앞으로 예약된 기상 알람 0회\n" +
                "권한/근무표 동기화/설정을 확인하세요.");

            if (scheduleSummary != null) {
                scheduleSummary.setText(
                    "예약 없음 · 설정을 확인하세요");
            }
            return;
        }

        String next =
            AlarmScheduler.getNextStoredAlarmSummary(this);

        scheduleStatusText.setText(
            "앞으로 예약된 기상 알람 " + count + "회\n" +
            "다음 알람 " + next);

        if (scheduleSummary != null) {
            scheduleSummary.setText(
                "다음 " + next + " · 총 " + count + "회");
        }
    }

    private void renderSoundSelection() {
        if (alarmSoundName == null) return;

        String uriValue = AlarmSettingsStore.loadSoundUri(this);
        if (uriValue == null || uriValue.isEmpty()) {
            alarmSoundName.setText(
                "알람 소리 · 시스템 기본 알람음");
            refreshModeSummary();
            return;
        }

        try {
            Ringtone ringtone = RingtoneManager.getRingtone(
                this, Uri.parse(uriValue));
            String title =
                ringtone == null
                    ? "선택한 알람음"
                    : ringtone.getTitle(this);

            alarmSoundName.setText(
                "알람 소리 · " +
                (title == null || title.isEmpty()
                    ? "선택한 알람음"
                    : title));
        } catch (Exception ignored) {
            alarmSoundName.setText(
                "알람 소리 · 선택한 알람음");
        }

        refreshModeSummary();
    }

    private void refreshModeSummary() {
        if (modeSummary == null) return;

        String mode = AlarmSettingsStore.loadMode(this);

        String modeLabel =
            AlarmSettingsStore.MODE_SOUND.equals(mode)
                ? "소리만"
                : AlarmSettingsStore.MODE_VIBRATE.equals(mode)
                    ? "진동만"
                    : "소리+진동";

        String sound =
            alarmSoundName == null
                ? "시스템 기본 알람음"
                : alarmSoundName.getText()
                    .toString()
                    .replace("알람 소리 · ", "");

        modeSummary.setText(
            modeLabel + " · " + sound);
    }

    private void openAlarmSoundPicker() {
        Intent intent =
            new Intent(
                RingtoneManager.ACTION_RINGTONE_PICKER);

        intent.putExtra(
            RingtoneManager.EXTRA_RINGTONE_TYPE,
            RingtoneManager.TYPE_ALARM);

        intent.putExtra(
            RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,
            true);

        intent.putExtra(
            RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,
            false);

        String existing =
            AlarmSettingsStore.loadSoundUri(this);

        if (existing != null && !existing.isEmpty()) {
            intent.putExtra(
                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                Uri.parse(existing));
        }

        try {
            startActivityForResult(
                intent,
                RINGTONE_PICKER_REQUEST);
        } catch (Exception error) {
            Toast.makeText(
                this,
                "시스템 알람음 선택기를 열 수 없습니다.",
                Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(
        int requestCode,
        int resultCode,
        Intent data) {

        super.onActivityResult(
            requestCode,
            resultCode,
            data);

        if (requestCode != RINGTONE_PICKER_REQUEST ||
            resultCode != RESULT_OK ||
            data == null) {
            return;
        }

        Uri uri =
            data.getParcelableExtra(
                RingtoneManager.EXTRA_RINGTONE_PICKED_URI);

        AlarmSettingsStore.saveSoundUri(
            this,
            uri == null ? "" : uri.toString());

        renderSoundSelection();
        refreshModeSummary();
    }

    @Override
    protected void onResume() {
        super.onResume();

        AlarmSettingsStore.rescheduleAll(this);
        refreshScheduleStatus();
        renderSoundSelection();
        refreshModeSummary();

        if (updateManager != null) {
            updateManager.resumePendingInstall();
        }
    }

    @Override
    protected void onDestroy() {
        if (updateManager != null) {
            updateManager.destroy();
            updateManager = null;
        }
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(
            value *
            getResources()
                .getDisplayMetrics()
                .density);
    }
}
