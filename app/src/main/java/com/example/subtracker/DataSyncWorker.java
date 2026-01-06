package com.example.subtracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URL;
import java.io.OutputStream;

public class DataSyncWorker extends Worker {

    public DataSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            sendUsageData();
            return Result.success();
        } catch (Exception e) {
            e.printStackTrace();
            return Result.retry();
        }
    }

    private void sendUsageData() {
        Context context = getApplicationContext();

        SharedPreferences prefs = context.getSharedPreferences("UserSession", Context.MODE_PRIVATE);
        String userEmail = prefs.getString("user_email", "ram@gmail.com");

        if (userEmail.equals("unknown@gmail.com")) {
            System.out.println("⚠️ Worker: No Email found. Skipping sync.");
            return;
        }

        UsageStatsManager usm = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);
        long endTime = System.currentTimeMillis();

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startTime = calendar.getTimeInMillis();

        List<UsageStats> usageStats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

        JSONArray jsonArray = new JSONArray();

        if (usageStats != null) {
            for (UsageStats stats : usageStats) {
                String pkg = stats.getPackageName();

                if (stats.getLastTimeUsed() < startTime) continue;

                long totalTime = stats.getTotalTimeInForeground();

                if (totalTime > 0) {
                    String appName = "";
                    if (pkg.contains("youtube")) appName = "YouTube";
                    else if (pkg.contains("hotstar")) appName = "Hotstar";
                    else if (pkg.contains("netflix")) appName = "Netflix";
                    else if (pkg.contains("amazon")) appName = "Prime Video";
                    else if (pkg.contains("subtracker")) appName = "SubTracker";

                    if (!appName.isEmpty()) {
                        long minutes = (totalTime / 1000) / 60;

                        if (minutes > 0) {
                            try {
                                JSONObject item = new JSONObject();
                                item.put("email", userEmail);
                                item.put("app_name", appName);
                                item.put("usage_minutes", minutes);
                                item.put("date", new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));

                                jsonArray.put(item);
                            } catch (Exception e) { e.printStackTrace(); }
                        }
                    }
                }
            }
        }
        System.out.println("📦 WORKER SENDING DATA: " + jsonArray.toString());
        if (jsonArray.length() > 0) {
            try {
                URL url = new URL(Config.SERVER_URL);

                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String jsonInputString = jsonArray.toString();
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                System.out.println("🔥 Auto-Sync Success! Status Code: " + code);

            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("❌ Auto-Sync Failed: " + e.getMessage());
            }
        } else {
            System.out.println("⚠️ Worker: No usage data to sync.");
        }
    }
}