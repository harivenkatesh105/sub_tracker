package com.example.subtracker;

import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.ExistingPeriodicWorkPolicy;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    Button btnRefresh;
    TextView tvResult;
    String userEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnRefresh = findViewById(R.id.btnRefresh);
        tvResult = findViewById(R.id.tvResult);

        SharedPreferences prefs = getSharedPreferences("UserSession", MODE_PRIVATE);
        prefs.edit().putString("user_email", "ram@gmail.com").apply();
        userEmail = prefs.getString("user_email", "ram@gmail.com");

        if (checkPermission()) {

            PeriodicWorkRequest uploadRequest =
                    new PeriodicWorkRequest.Builder(DataSyncWorker.class, 15, TimeUnit.MINUTES)
                            .build();

            WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                    "UploadUsageData",
                    ExistingPeriodicWorkPolicy.KEEP,
                    uploadRequest
            );

            System.out.println("✅ Background Auto-Sync Started!");
        }

        if (!checkPermission()) {
            startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
        }

        btnRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showUsageStats();
            }
        });
    }

    private boolean checkPermission() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void showUsageStats() {
        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        long endTime = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        long startTime = calendar.getTimeInMillis();

        StringBuilder output = new StringBuilder();

        List<UsageStats> usageStats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, startTime, endTime);

        JSONArray jsonArray = new JSONArray();

        if (usageStats != null) {
            for (UsageStats stats : usageStats) {
                if (stats.getLastTimeUsed() < startTime) {
                    continue;
                }
                String pkg = stats.getPackageName();
                long totalTime = stats.getTotalTimeInForeground();

                if (totalTime > 0) {
                    String appName = "";
                    if (pkg.contains("youtube")) appName = "YouTube";
                    else if (pkg.contains("hotstar")) appName = "Hotstar";
                    else if (pkg.contains("netflix")) appName = "Netflix";
                    else if (pkg.contains("amazon")) appName = "Prime Video";

                    if (!appName.isEmpty()) {
                        long totalSeconds = totalTime / 1000;

                        long hours = totalSeconds / 3600;
                        long displayMinutes = (totalSeconds % 3600) / 60; // ⚠️ Name changed
                        long seconds = totalSeconds % 60;

                        output.append(appName).append(": ");
                        if (hours > 0) output.append(hours).append("h ");
                        if (displayMinutes > 0) output.append(displayMinutes).append("m ");
                        output.append(seconds).append("s\n\n");

                        long totalMinutesForServer = (totalSeconds / 60);

                        if (totalMinutesForServer > 0) {
                            try {
                                JSONObject item = new JSONObject();
                                item.put("email", userEmail);
                                item.put("app_name", appName);
                                item.put("usage_minutes", totalMinutesForServer);
                                item.put("date", new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date()));

                                jsonArray.put(item);

                            } catch (Exception e) { e.printStackTrace(); }
                        }
                    }
                }
            }
        }

        if (jsonArray.length() > 0) {
            sendDataToServer(jsonArray.toString());
            output.append("\n✅ Synced ").append(jsonArray.length()).append(" apps to Server.");
        } else {
            output.append("\n⚠️ No usage found to sync.");
        }

        tvResult.setText(output.toString());
    }
    public void sendDataToServer(String jsonString) {
        new Thread(() -> {
            try {
                URL url = new URL(Config.SERVER_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonString.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int code = conn.getResponseCode();
                System.out.println("Batch Sync Status: " + code);

            } catch (Exception e) { e.printStackTrace(); }
        }).start();
    }
}