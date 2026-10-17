package com.screenguard.app.service;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Toast;

import com.screenguard.app.BlockActivity;
import com.screenguard.app.db.AppDatabase;
import com.screenguard.app.model.AppRule;
import com.screenguard.app.model.UsageRecord;

/**
 * 核心无障碍服务：监控前台应用切换，追踪使用时间，触发封锁
 */
public class GuardAccessibilityService extends AccessibilityService {

    private static final String TAG = "GuardAccessibility";
    private static final long TIME_CHECK_INTERVAL = 3000; // 每3秒检查一次时间

    private AppDatabase db;
    private String currentForegroundPkg = "";
    private long foregroundStartTime = 0;
    private boolean isTracking = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeCheckRunnable = new Runnable() {
        @Override
        public void run() {
            checkCurrentAppTimeLimit();
            handler.postDelayed(this, TIME_CHECK_INTERVAL);
        }
    };

    // 静态引用，方便外部检查服务状态
    private static GuardAccessibilityService instance;

    public static boolean isRunning() {
        return instance != null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        db = AppDatabase.getInstance(this);
        instance = this;
        Log.i(TAG, "GuardAccessibilityService created");
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        handler.post(timeCheckRunnable);
        Log.i(TAG, "GuardAccessibilityService connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getPackageName() == null) return;
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        String packageName = event.getPackageName().toString();

        // 忽略自身和系统UI
        if (packageName.equals(getPackageName()) ||
            packageName.equals("com.android.systemui") ||
            packageName.equals("com.android.launcher") ||
            packageName.contains("launcher")) {
            // 离开了被监控的应用，保存使用时间
            if (isTracking) {
                saveCurrentSession();
                isTracking = false;
            }
            currentForegroundPkg = packageName;
            return;
        }

        // 检测到新前台应用
        if (!packageName.equals(currentForegroundPkg)) {
            // 保存上一个被监控应用的使用时间
            if (isTracking) {
                saveCurrentSession();
            }

            currentForegroundPkg = packageName;
            foregroundStartTime = System.currentTimeMillis();
            isTracking = false;

            // 检查新前台应用是否在监控列表
            AppRule rule = db.getRule(packageName);
            if (rule == null || !rule.isEnabled) return;

            // === 检查是否处于封锁状态 ===
            if (db.isBlocked(packageName)) {
                Log.i(TAG, "App blocked: " + packageName);
                launchBlockScreen(packageName, "cooldown");
                return;
            }

            // === 检查打开次数 ===
            UsageRecord record = db.getTodayUsage(packageName);
            int openCount = (record != null) ? record.openCount : 0;

            if (openCount >= rule.maxOpensPerDay) {
                // 超过打开次数限制
                long blockedUntil = System.currentTimeMillis() +
                        (long) rule.cooldownMinutes * 60 * 1000;
                db.addBlockEvent(packageName, blockedUntil, "open_count_exceeded");
                Log.i(TAG, "Open count exceeded for: " + packageName);
                launchBlockScreen(packageName, "count");
                return;
            }

            // === 检查累计时间 ===
            long totalSeconds = (record != null) ? record.totalSeconds : 0;
            if (totalSeconds >= (long) rule.dailyLimitMinutes * 60) {
                long blockedUntil = System.currentTimeMillis() +
                        (long) rule.cooldownMinutes * 60 * 1000;
                db.addBlockEvent(packageName, blockedUntil, "time_exceeded");
                Log.i(TAG, "Time exceeded for: " + packageName);
                launchBlockScreen(packageName, "time");
                return;
            }

            // 正常使用，开始追踪并记录一次打开
            isTracking = true;
            db.incrementOpenCount(packageName);
            Log.d(TAG, "Tracking started: " + packageName +
                    " (opens: " + (openCount + 1) + "/" + rule.maxOpensPerDay + ")");
        }
    }

    /**
     * 定期检查当前正在使用的App是否超时
     */
    private void checkCurrentAppTimeLimit() {
        if (!isTracking || currentForegroundPkg.isEmpty()) return;

        AppRule rule = db.getRule(currentForegroundPkg);
        if (rule == null || !rule.isEnabled) return;

        // 计算当前会话时长 + 累计时长
        long sessionSeconds = (System.currentTimeMillis() - foregroundStartTime) / 1000;
        UsageRecord record = db.getTodayUsage(currentForegroundPkg);
        long totalSeconds = (record != null ? record.totalSeconds : 0) + sessionSeconds;

        if (totalSeconds >= (long) rule.dailyLimitMinutes * 60) {
            // 超时! 保存当前会话，触发封锁
            saveCurrentSession();
            isTracking = false;

            long blockedUntil = System.currentTimeMillis() +
                    (long) rule.cooldownMinutes * 60 * 1000;
            db.addBlockEvent(currentForegroundPkg, blockedUntil, "time_exceeded");
            Log.i(TAG, "Time limit reached during session: " + currentForegroundPkg);
            launchBlockScreen(currentForegroundPkg, "time");
        }
    }

    /**
     * 保存当前使用会话的时长到数据库
     */
    private void saveCurrentSession() {
        if (foregroundStartTime <= 0 || currentForegroundPkg.isEmpty()) return;

        long sessionSeconds = (System.currentTimeMillis() - foregroundStartTime) / 1000;
        if (sessionSeconds > 0) {
            db.addUsageTime(currentForegroundPkg, sessionSeconds);
            Log.d(TAG, "Saved session: " + currentForegroundPkg +
                    " (" + sessionSeconds + "s)");
        }
        foregroundStartTime = System.currentTimeMillis(); // 重置起点
    }

    /**
     * 启动封锁界面，强制退出目标应用
     */
    private void launchBlockScreen(String packageName, String reason) {
        // 先执行HOME键回到桌面
        performGlobalAction(GLOBAL_ACTION_HOME);

        // 然后启动封锁提示页面
        handler.postDelayed(() -> {
            Intent intent = new Intent(this, BlockActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                    Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("package_name", packageName);
            intent.putExtra("reason", reason);
            startActivity(intent);
        }, 300);
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "GuardAccessibilityService interrupted");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(timeCheckRunnable);
        if (isTracking) {
            saveCurrentSession();
        }
        instance = null;
        Log.i(TAG, "GuardAccessibilityService destroyed");
    }
}
