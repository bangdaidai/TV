package com.fongmi.android.tv.utils;

import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import com.fongmi.android.tv.App;
import com.google.gson.JsonObject;

public class WatchRecordSync {

    private static final String TAG = "WatchRecordSync";
    private static final String[] LEGADO_PACKAGES = {
        "io.legado.app.release",
        "io.legato.kazusa.q"
    };
    private static final String LEGADO_AUTHORITY_BASE = "io.legado.app.watchRecordProvider";
    private static String PROVIDER_AUTHORITY = null;
    private static String FOUND_PACKAGE = null;

    private static String getProviderAuthority() {
        if (PROVIDER_AUTHORITY != null) return PROVIDER_AUTHORITY;

        for (String pkg : LEGADO_PACKAGES) {
            try {
                PackageManager pm = App.get().getPackageManager();
                android.content.pm.PackageInfo pi = pm.getPackageInfo(pkg, PackageManager.GET_PROVIDERS);
                if (pi.providers != null) {
                    for (android.content.pm.ProviderInfo provider : pi.providers) {
                        if (provider.name != null && provider.name.contains("WatchRecordProvider")) {
                            PROVIDER_AUTHORITY = provider.authority;
                            FOUND_PACKAGE = pkg;
                            Log.d(TAG, "Found authority: " + PROVIDER_AUTHORITY + " in package: " + pkg);
                            return PROVIDER_AUTHORITY;
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting authority for " + pkg, e);
            }
        }

        PROVIDER_AUTHORITY = LEGADO_AUTHORITY_BASE;
        Log.d(TAG, "Using default authority: " + PROVIDER_AUTHORITY);
        return PROVIDER_AUTHORITY;
    }

    public static boolean isLegadoInstalled() {
        for (String pkg : LEGADO_PACKAGES) {
            try {
                App.get().getPackageManager().getPackageInfo(pkg, PackageManager.GET_ACTIVITIES);
                FOUND_PACKAGE = pkg;
                Log.d(TAG, "Legado is installed: " + pkg);
                return true;
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Legado not installed: " + pkg);
            }
        }
        return false;
    }

    public static void addWatchRecord(JsonObject record) {
        Log.d(TAG, "addWatchRecord called");

        if (!isLegadoInstalled()) {
            Log.d(TAG, "Legado not installed, skip sync");
            return;
        }

        boolean providerSuccess = tryContentProvider(record);

        if (!providerSuccess) {
            Log.d(TAG, "ContentProvider failed, trying broadcast...");
            tryBroadcast(record);
        }
    }

    private static boolean tryContentProvider(JsonObject record) {
        Log.d(TAG, "Trying ContentProvider...");

        String authority = getProviderAuthority();
        Uri uri = Uri.parse("content://" + authority + "/watchRecord/add");
        Log.d(TAG, "URI: " + uri.toString());

        try {
            ContentValues values = new ContentValues();
            String json = record.toString();
            Log.d(TAG, "JSON: " + json);

            values.put("json", json);
            Log.d(TAG, "Calling contentResolver.insert...");

            android.net.Uri resultUri = App.get().getContentResolver().insert(uri, values);
            Log.d(TAG, "insert returned, resultUri: " + resultUri);

            if (resultUri != null) {
                Log.d(TAG, "ContentProvider SUCCESS");
                return true;
            } else {
                Log.e(TAG, "ContentProvider returned NULL!");
                return false;
            }
        } catch (Exception e) {
            Log.e(TAG, "ContentProvider failed: " + e.getMessage(), e);
            return false;
        }
    }

    private static void tryBroadcast(JsonObject record) {
        Log.d(TAG, "Trying Broadcast...");

        String targetPackage = FOUND_PACKAGE != null ? FOUND_PACKAGE : LEGADO_PACKAGES[0];

        try {
            Intent intent = new Intent();
            intent.setAction("io.legado.app.action.ADD_WATCH_RECORD");
            intent.setPackage(targetPackage);
            intent.putExtra("json", record.toString());

            Log.d(TAG, "Sending broadcast to: " + targetPackage);
            Log.d(TAG, "Action: io.legado.app.action.ADD_WATCH_RECORD");
            Log.d(TAG, "JSON: " + record.toString());

            App.get().sendBroadcast(intent);
            Log.d(TAG, "Broadcast sent SUCCESS");
        } catch (Exception e) {
            Log.e(TAG, "Broadcast failed: " + e.getMessage(), e);
        }
    }

    public static void syncWatchRecord(String vodName, String vodPic, long watchDuration, long startTime, String episodeTitle) {
        String bookName = (vodName != null && !vodName.isEmpty()) ? vodName : "未知视频";
        String coverUrl = (vodPic != null && !vodPic.isEmpty()) ? vodPic : "";

        JsonObject record = new JsonObject();
        record.addProperty("bookName", bookName);
        record.addProperty("author", "");
        record.addProperty("bookUrl", "");
        record.addProperty("coverUrl", coverUrl);
        record.addProperty("duration", watchDuration);
        record.addProperty("startTime", startTime);
        record.addProperty("endTime", System.currentTimeMillis());
        record.addProperty("episodeTitle", episodeTitle != null ? episodeTitle : "");

        Log.d(TAG, "=== syncWatchRecord ===");
        Log.d(TAG, "bookName: " + bookName);
        Log.d(TAG, "coverUrl: " + coverUrl);
        Log.d(TAG, "duration: " + watchDuration);
        Log.d(TAG, "episodeTitle: " + (episodeTitle != null ? episodeTitle : ""));

        addWatchRecord(record);
    }
}
