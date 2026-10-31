package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public final class RedditWallpapers {
    private static final String PREF_NAME = "reddit_wallpapers";
    private static final String KEY_INDEX = "fetch_index";
    private static final String KEY_SAVED_TITLE = "current_wall_title";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public static class WallpaperItem {
        public final String title;
        public final String imageUrl;

        public WallpaperItem(String title, String imageUrl) {
            this.title = title;
            this.imageUrl = imageUrl;
        }
    }

    public interface FetchCallback {
        void onSuccess(WallpaperItem item, Bitmap bitmap);
        void onError(String message);
    }

    public interface ListCallback {
        void onSuccess(List<WallpaperItem> items);
        void onError(String message);
    }

    public static String getCurrentWallpaperTitle(Context c) {
        return c.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .getString(KEY_SAVED_TITLE, "r/TVWallpapersSFW Wallpaper");
    }

    public static void setCurrentWallpaperTitle(Context c, String title) {
        c.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_SAVED_TITLE, title).apply();
    }

    public static void fetchWallpaperList(Context context, ListCallback callback) {
        new Thread(() -> {
            try {
                List<WallpaperItem> items = loadSubmissions();
                if (items.isEmpty()) {
                    MAIN_HANDLER.post(() -> callback.onError("No wallpapers found on r/TVWallpapersSFW"));
                    return;
                }
                MAIN_HANDLER.post(() -> callback.onSuccess(items));
            } catch (Throwable t) {
                MAIN_HANDLER.post(() -> callback.onError("Fetch error: " + t.getMessage()));
            }
        }).start();
    }

    public static void fetchNextWallpaper(Context context, FetchCallback callback) {
        new Thread(() -> {
            try {
                List<WallpaperItem> items = loadSubmissions();
                if (items.isEmpty()) {
                    MAIN_HANDLER.post(() -> callback.onError("No wallpapers found on r/TVWallpapersSFW"));
                    return;
                }
                SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                int idx = prefs.getInt(KEY_INDEX, 0);
                if (idx >= items.size()) idx = 0;
                WallpaperItem chosen = items.get(idx);
                prefs.edit().putInt(KEY_INDEX, (idx + 1) % items.size()).apply();

                Bitmap bmp = downloadBitmap(chosen.imageUrl);
                if (bmp == null) {
                    MAIN_HANDLER.post(() -> callback.onError("Failed to download image: " + chosen.imageUrl));
                    return;
                }
                MAIN_HANDLER.post(() -> callback.onSuccess(chosen, bmp));
            } catch (Throwable t) {
                MAIN_HANDLER.post(() -> callback.onError("Fetch error: " + t.getMessage()));
            }
        }).start();
    }

    private static List<WallpaperItem> loadSubmissions() {
        List<WallpaperItem> results = new ArrayList<>();
        String[] endpoints = {
            "https://api.pullpush.io/reddit/submission/search/?subreddit=TVWallpapersSFW&size=50",
            "https://www.reddit.com/r/TVWallpapersSFW/hot.json?limit=50"
        };
        for (String ep : endpoints) {
            try {
                URL url = new URL(ep);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(12000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                conn.setRequestProperty("Accept", "application/json");
                if (conn.getResponseCode() == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    JSONObject root = new JSONObject(sb.toString());
                    if (root.has("data")) {
                        Object dataObj = root.get("data");
                        if (dataObj instanceof JSONArray) {
                            JSONArray arr = (JSONArray) dataObj;
                            for (int i = 0; i < arr.length(); i++) {
                                JSONObject post = arr.getJSONObject(i);
                                String postUrl = post.optString("url", "");
                                String title = post.optString("title", "Reddit Wallpaper");
                                if (isImageUrl(postUrl)) {
                                    results.add(new WallpaperItem(cleanTitle(title), postUrl));
                                }
                            }
                        } else if (dataObj instanceof JSONObject) {
                            JSONObject data = (JSONObject) dataObj;
                            JSONArray children = data.optJSONArray("children");
                            if (children != null) {
                                for (int i = 0; i < children.length(); i++) {
                                    JSONObject child = children.getJSONObject(i);
                                    JSONObject postData = child.optJSONObject("data");
                                    if (postData != null) {
                                        String postUrl = postData.optString("url", "");
                                        String title = postData.optString("title", "Reddit Wallpaper");
                                        if (isImageUrl(postUrl)) {
                                            results.add(new WallpaperItem(cleanTitle(title), postUrl));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                conn.disconnect();
                if (!results.isEmpty()) break;
            } catch (Throwable ignored) { }
        }
        return results;
    }

    private static String cleanTitle(String raw) {
        if (raw == null) return "Wallpaper";
        // Clean up common reddit title artifacts like &amp;
        return raw.replace("&amp;", "&").trim();
    }

    private static boolean isImageUrl(String u) {
        if (u == null || u.isEmpty()) return false;
        String lower = u.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.contains("i.redd.it") || lower.contains("preview.redd.it");
    }

    public static Bitmap downloadBitmap(String urlStr) {
        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)");
            if (conn.getResponseCode() == 200) {
                try (InputStream in = conn.getInputStream()) {
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    return BitmapFactory.decodeStream(in, null, opts);
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }
}
