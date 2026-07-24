package com.kangj.shiftcalendar;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

public class AlarmActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            );
        }
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        );

        setContentView(R.layout.activity_alarm);
        renderAlarm(getIntent());

        Button stopButton = findViewById(R.id.stopAlarmButton);
        stopButton.setOnClickListener(view -> stopAlarmAndClose());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        renderAlarm(intent);
    }

    private void renderAlarm(Intent intent) {
        TextView dateView = findViewById(R.id.alarmDate);
        TextView shiftView = findViewById(R.id.alarmShift);
        TextView messageView = findViewById(R.id.alarmMessage);

        String workDate = intent == null ? "" : intent.getStringExtra("workDate");
        String shiftType = intent == null ? "근무" : intent.getStringExtra("shiftType");
        String body = intent == null ? "설정한 기상 시각입니다." : intent.getStringExtra("body");

        dateView.setText(workDate == null || workDate.isEmpty() ? "교대달력" : workDate);
        shiftView.setText(shiftType == null || shiftType.isEmpty() ? "근무 알람" : shiftType);
        messageView.setText(body == null || body.isEmpty() ? "설정한 기상 시각입니다." : body);
    }

    private void stopAlarmAndClose() {
        Intent stopIntent = new Intent(this, AlarmRingingService.class);
        stopService(stopIntent);
        finishAndRemoveTask();
    }

    @Override
    public void onBackPressed() {
        stopAlarmAndClose();
    }
}
