package com.screenguard.app;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.screenguard.app.db.AppDatabase;
import com.screenguard.app.model.AppRule;
import com.screenguard.app.service.GuardAccessibilityService;
import com.screenguard.app.service.MonitorService;
import com.screenguard.app.ui.AppRuleAdapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity implements AppRuleAdapter.OnRuleActionListener {

    private AppDatabase db;
    private AppRuleAdapter adapter;
    private RecyclerView rvRules;
    private TextView tvEmpty, tvStatus, tvTotalTime, tvBlockCount;
    private View dotAccessibility, dotUsageStats, dotOverlay;
    private TextView tvPermAccessibility, tvPermUsageStats, tvPermOverlay;

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            refreshUI();
            refreshHandler.postDelayed(this, 5000);
        }
    };

    // 已安装应用缓存(供选择器使用)
    private List<ApplicationInfo> installedApps;
    private List<String> appLabels;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = AppDatabase.getInstance(this);

        // 绑定视图
        tvStatus = findViewById(R.id.tvStatus);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        tvBlockCount = findViewById(R.id.tvBlockCount);
        tvEmpty = findViewById(R.id.tvEmpty);
        rvRules = findViewById(R.id.rvRules);
        dotAccessibility = findViewById(R.id.dotAccessibility);
        dotUsageStats = findViewById(R.id.dotUsageStats);
        dotOverlay = findViewById(R.id.dotOverlay);
        tvPermAccessibility = findViewById(R.id.tvPermAccessibility);
        tvPermUsageStats = findViewById(R.id.tvPermUsageStats);
        tvPermOverlay = findViewById(R.id.tvPermOverlay);
        Button btnAdd = findViewById(R.id.btnAdd);

        // RecyclerView
        adapter = new AppRuleAdapter(db, getPackageManager(), this);
        rvRules.setLayoutManager(new LinearLayoutManager(this));
        rvRules.setAdapter(adapter);

        // 添加按钮
        btnAdd.setOnClickListener(v -> showAddRuleDialog(null));

        // 权限点击事件
        findViewById(R.id.permAccessibility).setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        findViewById(R.id.permUsageStats).setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
        });

        findViewById(R.id.permOverlay).setOnClickListener(v -> {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        });

        // 启动前台服务
        startMonitorService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUI();
        refreshHandler.postDelayed(refreshRunnable, 5000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        refreshHandler.removeCallbacks(refreshRunnable);
    }

    private void startMonitorService() {
        Intent intent = new Intent(this, MonitorService.class);
        startForegroundService(intent);
    }

    // ==================== 刷新UI ====================

    private void refreshUI() {
        refreshPermissionStatus();
        refreshStats();
        refreshRuleList();
    }

    private void refreshPermissionStatus() {
        boolean accEnabled = isAccessibilityServiceEnabled();
        boolean usageEnabled = isUsageStatsPermissionGranted();
        boolean overlayEnabled = Settings.canDrawOverlays(this);

        setPermDot(dotAccessibility, accEnabled);
        setPermDot(dotUsageStats, usageEnabled);
        setPermDot(dotOverlay, overlayEnabled);

        tvPermAccessibility.setText(accEnabled ?
                getString(R.string.perm_granted) : getString(R.string.perm_not_granted));
        tvPermAccessibility.setTextColor(getColor(accEnabled ? R.color.success : R.color.danger));

        tvPermUsageStats.setText(usageEnabled ?
                getString(R.string.perm_granted) : getString(R.string.perm_not_granted));
        tvPermUsageStats.setTextColor(getColor(usageEnabled ? R.color.success : R.color.danger));

        tvPermOverlay.setText(overlayEnabled ?
                getString(R.string.perm_granted) : getString(R.string.perm_not_granted));
        tvPermOverlay.setTextColor(getColor(overlayEnabled ? R.color.success : R.color.danger));

        // 总状态
        boolean allGranted = accEnabled && usageEnabled;
        tvStatus.setText(allGranted ?
                getString(R.string.status_running) : getString(R.string.status_stopped));
    }

    private void setPermDot(View dot, boolean granted) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(getColor(granted ? R.color.success : R.color.danger));
        dot.setBackground(bg);
    }

    private void refreshStats() {
        long totalSeconds = db.getTodayTotalSeconds();
        long minutes = totalSeconds / 60;
        tvTotalTime.setText(minutes + "分钟");

        int blockCount = db.getTodayBlockCount();
        tvBlockCount.setText(blockCount + "次");
    }

    private void refreshRuleList() {
        List<AppRule> rules = db.getAllRules();
        adapter.setRules(rules);

        if (rules.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvRules.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            rvRules.setVisibility(View.VISIBLE);
        }
    }

    // ==================== 添加/编辑规则对话框 ====================

    private void showAddRuleDialog(AppRule existingRule) {
        View dialogView = LayoutInflater.from(this)
                .inflate(R.layout.dialog_add_rule, null);

        Spinner spinnerApps = dialogView.findViewById(R.id.spinnerApps);
        EditText etDailyLimit = dialogView.findViewById(R.id.etDailyLimit);
        EditText etMaxOpens = dialogView.findViewById(R.id.etMaxOpens);
        EditText etCooldown = dialogView.findViewById(R.id.etCooldown);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        Button btnSave = dialogView.findViewById(R.id.btnSave);

        // 加载已安装的应用列表(排除系统核心应用)
        loadInstalledApps();

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, appLabels);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerApps.setAdapter(spinnerAdapter);

        // 如果是编辑模式，填充已有数据
        if (existingRule != null) {
            etDailyLimit.setText(String.valueOf(existingRule.dailyLimitMinutes));
            etMaxOpens.setText(String.valueOf(existingRule.maxOpensPerDay));
            etCooldown.setText(String.valueOf(existingRule.cooldownMinutes));

            // 定位到已有包名
            for (int i = 0; i < installedApps.size(); i++) {
                if (installedApps.get(i).packageName.equals(existingRule.packageName)) {
                    spinnerApps.setSelection(i);
                    break;
                }
            }
            spinnerApps.setEnabled(false); // 编辑时不允许换应用
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            int selectedPos = spinnerApps.getSelectedItemPosition();
            if (selectedPos < 0 || selectedPos >= installedApps.size()) return;

            String dailyStr = etDailyLimit.getText().toString().trim();
            String opensStr = etMaxOpens.getText().toString().trim();
            String cooldownStr = etCooldown.getText().toString().trim();

            if (dailyStr.isEmpty() || opensStr.isEmpty() || cooldownStr.isEmpty()) return;

            int dailyLimit = Integer.parseInt(dailyStr);
            int maxOpens = Integer.parseInt(opensStr);
            int cooldown = Integer.parseInt(cooldownStr);

            ApplicationInfo appInfo = installedApps.get(selectedPos);
            String appName = getPackageManager().getApplicationLabel(appInfo).toString();

            if (existingRule != null) {
                existingRule.dailyLimitMinutes = dailyLimit;
                existingRule.maxOpensPerDay = maxOpens;
                existingRule.cooldownMinutes = cooldown;
                db.updateRule(existingRule);
            } else {
                AppRule rule = new AppRule(appInfo.packageName, appName,
                        dailyLimit, maxOpens, cooldown);
                db.insertRule(rule);
            }

            dialog.dismiss();
            refreshRuleList();
        });

        dialog.show();
    }

    private void loadInstalledApps() {
        if (installedApps != null) return; // 只加载一次

        PackageManager pm = getPackageManager();
        List<ApplicationInfo> allApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        installedApps = new ArrayList<>();
        appLabels = new ArrayList<>();

        // 常见短视频App包名 - 优先显示
        String[] videoApps = {
                "com.ss.android.ugc.aweme",     // 抖音
                "com.smile.gifmaker",             // 快手
                "tv.danmaku.bili",               // 哔哩哔哩
                "com.kuaishou.nebula",           // 快手极速版
                "com.ss.android.ugc.aweme.lite", // 抖音极速版
                "com.ss.android.article.news",   // 今日头条
                "com.tencent.qqlive",            // 腾讯视频
                "com.youku.phone",               // 优酷
                "com.ss.android.ugc.live",       // 西瓜视频
        };

        // 先添加已安装的短视频App
        for (String pkg : videoApps) {
            try {
                ApplicationInfo info = pm.getApplicationInfo(pkg, 0);
                installedApps.add(info);
                appLabels.add("★ " + pm.getApplicationLabel(info));
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }

        // 再添加其他非系统应用
        for (ApplicationInfo info : allApps) {
            if ((info.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (info.packageName.equals(getPackageName())) continue;

            // 跳过已经添加的短视频App
            boolean alreadyAdded = false;
            for (String pkg : videoApps) {
                if (info.packageName.equals(pkg)) {
                    alreadyAdded = true;
                    break;
                }
            }
            if (alreadyAdded) continue;

            installedApps.add(info);
            appLabels.add(pm.getApplicationLabel(info).toString());
        }
    }

    // ==================== 规则操作回调 ====================

    @Override
    public void onEdit(AppRule rule) {
        showAddRuleDialog(rule);
    }

    @Override
    public void onDelete(AppRule rule) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.confirm_delete))
                .setPositiveButton(getString(R.string.delete), (d, w) -> {
                    db.deleteRule(rule.id);
                    refreshRuleList();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    @Override
    public void onToggle(AppRule rule, boolean enabled) {
        db.setRuleEnabled(rule.id, enabled);
    }

    // ==================== 权限检测工具 ====================

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> enabledServices =
                am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : enabledServices) {
            ServiceInfo serviceInfo = info.getResolveInfo().serviceInfo;
            if (serviceInfo.packageName.equals(getPackageName()) &&
                serviceInfo.name.equals(GuardAccessibilityService.class.getName())) {
                return true;
            }
        }
        return false;
    }

    private boolean isUsageStatsPermissionGranted() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }
}
