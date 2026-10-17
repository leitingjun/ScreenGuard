package com.screenguard.app.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.screenguard.app.MainActivity;
import com.screenguard.app.R;
import com.screenguard.app.db.AppDatabase;

/**
 * 前台服务：保持进程存活，显示持久通知，定期清理过期封锁
 */
public class MonitorService extends Service {

    private static final String TAG = "MonitorService";
    private static final String CHANNEL_ID = "screenguard_monitor";
    private static final int NOTIFICATION_ID = 1001;
    private static final long CLEANUP_INTERVAL = 60_000; // 每分钟清理一次

    private AppDatabase db;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable cleanupRunnable = new Runnable() {
        @Override
        public void run() {
            if (db != null) {
                db.clearExpiredBlocks();
                updateNotification();
            }
            handler.postDelayed(this, CLEANUP_INTERVAL);
        }
    };

    private static MonitorService instance;

    public static boolean isRunning() {
        return instance != null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        db = AppDatabase.getInstance(this);
        createNotificationChannel();
        Log.i(TAG, "MonitorService created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        handler.post(cleanupRunnable);
        Log.i(TAG, "MonitorService started");
        return START_STICKY; // 被系统杀死后自动重启
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(cleanupRunnable);
        instance = null;
        Log.i(TAG, "MonitorService destroyed");
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setDescription(getString(R.string.notif_channel_desc));
        channel.setShowBadge(false);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        int ruleCount = db.getAllRules().size();

        Intent tapIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, tapIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(String.format(getString(R.string.notif_monitoring), ruleCount))
                .setContentIntent(pi)
                .setOngoing(true)
                .setSilent(true)
                .build();
    }

    private void updateNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIFICATION_ID, buildNotification());
    }
}
