package com.screenguard.app.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.screenguard.app.model.AppRule;
import com.screenguard.app.model.UsageRecord;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AppDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME = "screenguard.db";
    private static final int DB_VERSION = 1;
    private static volatile AppDatabase instance;

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = new AppDatabase(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private AppDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE app_rules (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "package_name TEXT UNIQUE NOT NULL," +
                "app_name TEXT," +
                "daily_limit_minutes INTEGER DEFAULT 30," +
                "max_opens_per_day INTEGER DEFAULT 10," +
                "cooldown_minutes INTEGER DEFAULT 30," +
                "is_enabled INTEGER DEFAULT 1)");

        db.execSQL("CREATE TABLE usage_log (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "package_name TEXT NOT NULL," +
                "date TEXT NOT NULL," +
                "total_seconds INTEGER DEFAULT 0," +
                "open_count INTEGER DEFAULT 0," +
                "UNIQUE(package_name, date))");

        db.execSQL("CREATE TABLE block_events (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "package_name TEXT NOT NULL," +
                "blocked_until INTEGER NOT NULL," +
                "reason TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // 未来版本升级处理
    }

    // ==================== 规则管理 ====================

    public List<AppRule> getAllRules() {
        List<AppRule> rules = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM app_rules ORDER BY app_name", null);
        while (c.moveToNext()) {
            rules.add(cursorToRule(c));
        }
        c.close();
        return rules;
    }

    public AppRule getRule(String packageName) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM app_rules WHERE package_name = ?",
                new String[]{packageName});
        AppRule rule = null;
        if (c.moveToFirst()) {
            rule = cursorToRule(c);
        }
        c.close();
        return rule;
    }

    public long insertRule(AppRule rule) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("package_name", rule.packageName);
        cv.put("app_name", rule.appName);
        cv.put("daily_limit_minutes", rule.dailyLimitMinutes);
        cv.put("max_opens_per_day", rule.maxOpensPerDay);
        cv.put("cooldown_minutes", rule.cooldownMinutes);
        cv.put("is_enabled", rule.isEnabled ? 1 : 0);
        return db.insertWithOnConflict("app_rules", null, cv,
                SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void updateRule(AppRule rule) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("app_name", rule.appName);
        cv.put("daily_limit_minutes", rule.dailyLimitMinutes);
        cv.put("max_opens_per_day", rule.maxOpensPerDay);
        cv.put("cooldown_minutes", rule.cooldownMinutes);
        cv.put("is_enabled", rule.isEnabled ? 1 : 0);
        db.update("app_rules", cv, "id = ?", new String[]{String.valueOf(rule.id)});
    }

    public void deleteRule(long id) {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("app_rules", "id = ?", new String[]{String.valueOf(id)});
    }

    public void setRuleEnabled(long id, boolean enabled) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("is_enabled", enabled ? 1 : 0);
        db.update("app_rules", cv, "id = ?", new String[]{String.valueOf(id)});
    }

    // ==================== 使用记录 ====================

    private String today() {
        return DATE_FMT.format(new Date());
    }

    public UsageRecord getTodayUsage(String packageName) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT * FROM usage_log WHERE package_name = ? AND date = ?",
                new String[]{packageName, today()});
        UsageRecord record = null;
        if (c.moveToFirst()) {
            record = new UsageRecord();
            record.id = c.getLong(c.getColumnIndexOrThrow("id"));
            record.packageName = c.getString(c.getColumnIndexOrThrow("package_name"));
            record.date = c.getString(c.getColumnIndexOrThrow("date"));
            record.totalSeconds = c.getLong(c.getColumnIndexOrThrow("total_seconds"));
            record.openCount = c.getInt(c.getColumnIndexOrThrow("open_count"));
        }
        c.close();
        return record;
    }

    public void addUsageTime(String packageName, long seconds) {
        if (seconds <= 0) return;
        SQLiteDatabase db = getWritableDatabase();
        String date = today();
        // 尝试更新，如果记录存在
        int rows = db.rawQuery(
                "SELECT 1 FROM usage_log WHERE package_name = ? AND date = ?",
                new String[]{packageName, date}).getCount();
        if (rows > 0) {
            db.execSQL("UPDATE usage_log SET total_seconds = total_seconds + ? " +
                            "WHERE package_name = ? AND date = ?",
                    new Object[]{seconds, packageName, date});
        } else {
            ContentValues cv = new ContentValues();
            cv.put("package_name", packageName);
            cv.put("date", date);
            cv.put("total_seconds", seconds);
            cv.put("open_count", 0);
            db.insert("usage_log", null, cv);
        }
    }

    public void incrementOpenCount(String packageName) {
        SQLiteDatabase db = getWritableDatabase();
        String date = today();
        Cursor c = db.rawQuery(
                "SELECT 1 FROM usage_log WHERE package_name = ? AND date = ?",
                new String[]{packageName, date});
        boolean exists = c.getCount() > 0;
        c.close();

        if (exists) {
            db.execSQL("UPDATE usage_log SET open_count = open_count + 1 " +
                            "WHERE package_name = ? AND date = ?",
                    new Object[]{packageName, date});
        } else {
            ContentValues cv = new ContentValues();
            cv.put("package_name", packageName);
            cv.put("date", date);
            cv.put("total_seconds", 0);
            cv.put("open_count", 1);
            db.insert("usage_log", null, cv);
        }
    }

    public long getTodayTotalSeconds() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT COALESCE(SUM(total_seconds), 0) FROM usage_log WHERE date = ?",
                new String[]{today()});
        long total = 0;
        if (c.moveToFirst()) {
            total = c.getLong(0);
        }
        c.close();
        return total;
    }

    // ==================== 封锁管理 ====================

    public boolean isBlocked(String packageName) {
        SQLiteDatabase db = getReadableDatabase();
        long now = System.currentTimeMillis();
        Cursor c = db.rawQuery(
                "SELECT 1 FROM block_events WHERE package_name = ? AND blocked_until > ?",
                new String[]{packageName, String.valueOf(now)});
        boolean blocked = c.getCount() > 0;
        c.close();
        return blocked;
    }

    public long getBlockedUntil(String packageName) {
        SQLiteDatabase db = getReadableDatabase();
        long now = System.currentTimeMillis();
        Cursor c = db.rawQuery(
                "SELECT MAX(blocked_until) FROM block_events " +
                        "WHERE package_name = ? AND blocked_until > ?",
                new String[]{packageName, String.valueOf(now)});
        long until = 0;
        if (c.moveToFirst()) {
            until = c.getLong(0);
        }
        c.close();
        return until;
    }

    public void addBlockEvent(String packageName, long blockedUntil, String reason) {
        SQLiteDatabase db = getWritableDatabase();
        // 先清除该应用的旧封锁
        db.delete("block_events", "package_name = ?", new String[]{packageName});
        ContentValues cv = new ContentValues();
        cv.put("package_name", packageName);
        cv.put("blocked_until", blockedUntil);
        cv.put("reason", reason);
        db.insert("block_events", null, cv);
    }

    public void clearExpiredBlocks() {
        SQLiteDatabase db = getWritableDatabase();
        db.delete("block_events", "blocked_until < ?",
                new String[]{String.valueOf(System.currentTimeMillis())});
    }

    public int getTodayBlockCount() {
        // 简单统计今日封锁次数
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM block_events", null);
        int count = 0;
        if (c.moveToFirst()) {
            count = c.getInt(0);
        }
        c.close();
        return count;
    }

    // ==================== 工具方法 ====================

    private AppRule cursorToRule(Cursor c) {
        AppRule rule = new AppRule();
        rule.id = c.getLong(c.getColumnIndexOrThrow("id"));
        rule.packageName = c.getString(c.getColumnIndexOrThrow("package_name"));
        rule.appName = c.getString(c.getColumnIndexOrThrow("app_name"));
        rule.dailyLimitMinutes = c.getInt(c.getColumnIndexOrThrow("daily_limit_minutes"));
        rule.maxOpensPerDay = c.getInt(c.getColumnIndexOrThrow("max_opens_per_day"));
        rule.cooldownMinutes = c.getInt(c.getColumnIndexOrThrow("cooldown_minutes"));
        rule.isEnabled = c.getInt(c.getColumnIndexOrThrow("is_enabled")) == 1;
        return rule;
    }
}
