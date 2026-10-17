package com.screenguard.app.model;

public class AppRule {
    public long id;
    public String packageName;
    public String appName;
    public int dailyLimitMinutes;
    public int maxOpensPerDay;
    public int cooldownMinutes;
    public boolean isEnabled;

    public AppRule() {
        this.dailyLimitMinutes = 30;
        this.maxOpensPerDay = 10;
        this.cooldownMinutes = 30;
        this.isEnabled = true;
    }

    public AppRule(String packageName, String appName, int dailyLimitMinutes,
                   int maxOpensPerDay, int cooldownMinutes) {
        this.packageName = packageName;
        this.appName = appName;
        this.dailyLimitMinutes = dailyLimitMinutes;
        this.maxOpensPerDay = maxOpensPerDay;
        this.cooldownMinutes = cooldownMinutes;
        this.isEnabled = true;
    }
}
