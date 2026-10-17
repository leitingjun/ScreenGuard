package com.screenguard.app.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.screenguard.app.service.MonitorService;

/**
 * 开机自启广播接收器：系统启动完成后自动启动监控服务
 */
public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
            "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            Log.i(TAG, "Boot completed, starting MonitorService");

            Intent serviceIntent = new Intent(context, MonitorService.class);
            context.startForegroundService(serviceIntent);
        }
    }
}
