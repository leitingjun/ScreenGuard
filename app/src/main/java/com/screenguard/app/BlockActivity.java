package com.screenguard.app;

import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.screenguard.app.db.AppDatabase;
import com.screenguard.app.model.AppRule;
import com.screenguard.app.model.UsageRecord;

/**
 * 封锁提示页面：当App被封锁时全屏展示，显示封锁原因和剩余冷却时间
 */
public class BlockActivity extends AppCompatActivity {

    private TextView tvBlockReason;
    private TextView tvCooldownRemaining;
    private CountDownTimer countDownTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_block);

        tvBlockReason = findViewById(R.id.tvBlockReason);
        tvCooldownRemaining = findViewById(R.id.tvCooldownRemaining);
        Button btnGoHome = findViewById(R.id.btnGoHome);

        String packageName = getIntent().getStringExtra("package_name");
        String reason = getIntent().getStringExtra("reason");

        if (packageName == null) {
            finish();
            return;
        }

        AppDatabase db = AppDatabase.getInstance(this);
        AppRule rule = db.getRule(packageName);
        UsageRecord record = db.getTodayUsage(packageName);

        // 显示封锁原因
        String appName = (rule != null) ? rule.appName : packageName;
        if ("count".equals(reason)) {
            int opens = (record != null) ? record.openCount : 0;
            int maxOpens = (rule != null) ? rule.maxOpensPerDay : 0;
            tvBlockReason.setText(String.format(
                    "%s 今日已打开 %d 次，超过限制 %d 次。\n冷却后可再次使用。",
                    appName, opens, maxOpens));
        } else {
            long minutes = (record != null) ? record.totalSeconds / 60 : 0;
            int limit = (rule != null) ? rule.dailyLimitMinutes : 0;
            tvBlockReason.setText(String.format(
                    "%s 今日已使用 %d 分钟，超过限额 %d 分钟。\n冷却后可再次使用。",
                    appName, minutes, limit));
        }

        // 倒计时
        long blockedUntil = db.getBlockedUntil(packageName);
        long remaining = blockedUntil - System.currentTimeMillis();
        if (remaining > 0) {
            countDownTimer = new CountDownTimer(remaining, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    long mins = millisUntilFinished / 60000;
                    long secs = (millisUntilFinished % 60000) / 1000;
                    tvCooldownRemaining.setText(
                            String.format("剩余冷却时间：%02d:%02d", mins, secs));
                }

                @Override
                public void onFinish() {
                    tvCooldownRemaining.setText("冷却结束，可以使用了");
                }
            }.start();
        }

        // 返回桌面按钮
        btnGoHome.setOnClickListener(v -> {
            Intent homeIntent = new Intent(Intent.ACTION_MAIN);
            homeIntent.addCategory(Intent.CATEGORY_HOME);
            homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(homeIntent);
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        // 禁止返回，强制用户点击按钮
        Intent homeIntent = new Intent(Intent.ACTION_MAIN);
        homeIntent.addCategory(Intent.CATEGORY_HOME);
        homeIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(homeIntent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }
}
