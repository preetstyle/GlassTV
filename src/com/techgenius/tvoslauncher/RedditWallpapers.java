package com.techgenius.tvoslauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RedditWallpapers {
    private static final String PREF_NAME = "reddit_wallpapers";
    private static final String KEY_INDEX = "fetch_index";
    private static final String KEY_SAVED_TITLE = "current_wall_title";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    public static final LruCache<String, Bitmap> BITMAP_CACHE = new LruCache<>(50);

    public static class WallpaperItem {
        public final String title;
        public final String imageUrl;
        public final String previewUrl;

        public WallpaperItem(String title, String imageUrl, String previewUrl) {
            this.title = title;
            this.imageUrl = imageUrl;
            this.previewUrl = (previewUrl != null && !previewUrl.isEmpty()) ? previewUrl : imageUrl;
        }

        public WallpaperItem(String title, String imageUrl) {
            this(title, imageUrl, imageUrl);
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
                .getString(KEY_SAVED_TITLE, "r/wallpapers Wallpaper");
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
                    MAIN_HANDLER.post(() -> callback.onError("No wallpapers found on r/wallpapers"));
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
                    MAIN_HANDLER.post(() -> callback.onError("No wallpapers found on r/wallpapers"));
                    return;
                }
                SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                int idx = prefs.getInt(KEY_INDEX, 0);
                if (idx >= items.size()) idx = 0;
                WallpaperItem chosen = items.get(idx);
                prefs.edit().putInt(KEY_INDEX, (idx + 1) % items.size()).apply();

                Bitmap bmp = downloadBitmap(chosen.imageUrl, 1920, 1080);
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
            "https://www.reddit.com/r/wallpapers/new/.rss",
            "https://www.reddit.com/r/wallpapers/new.json?limit=50",
            "https://old.reddit.com/r/wallpapers/new.json?limit=50",
            "https://api.pullpush.io/reddit/submission/search/?subreddit=wallpapers&size=50"
        };

        for (String ep : endpoints) {
            try {
                android.util.Log.d("RedditWallpapers", "Trying endpoint: " + ep);
                URL url = new URL(ep);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(12000);
                if (ep.endsWith(".rss")) {
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; Feedly/1.0; +http://www.feedly.com/fetcher.html)");
                    conn.setRequestProperty("Accept", "application/atom+xml, application/xml, text/xml, */*");
                } else {
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
                    conn.setRequestProperty("Accept", "application/json");
                }
                int respCode = conn.getResponseCode();
                android.util.Log.d("RedditWallpapers", "Endpoint " + ep + " -> respCode=" + respCode);
                if (respCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    String body = sb.toString().trim();
                    android.util.Log.d("RedditWallpapers", "Endpoint " + ep + " -> bodyLen=" + body.length() + ", start=" + body.substring(0, Math.min(60, body.length())));
                    if (body.contains("<feed") || body.contains("<rss") || body.contains("<entry>")) {
                        parseRss(body, results);
                    } else if (body.startsWith("{")) {
                        parseJson(body, results);
                    }
                    android.util.Log.d("RedditWallpapers", "Endpoint " + ep + " -> parsed results=" + results.size());
                }
                conn.disconnect();
                if (!results.isEmpty()) break;
            } catch (Throwable t) {
                android.util.Log.e("RedditWallpapers", "Endpoint " + ep + " failed: " + t.getMessage(), t);
            }
        }
        return results;
    }

    private static void parseJson(String jsonStr, List<WallpaperItem> results) {
        try {
            JSONObject root = new JSONObject(jsonStr);
            if (!root.has("data")) return;
            Object dataObj = root.get("data");

            if (dataObj instanceof JSONArray) {
                JSONArray arr = (JSONArray) dataObj;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject post = arr.getJSONObject(i);
                    extractPost(post, results);
                }
            } else if (dataObj instanceof JSONObject) {
                JSONObject data = (JSONObject) dataObj;
                JSONArray children = data.optJSONArray("children");
                if (children != null) {
                    for (int i = 0; i < children.length(); i++) {
                        JSONObject child = children.getJSONObject(i);
                        JSONObject postData = child.optJSONObject("data");
                        if (postData != null) {
                            extractPost(postData, results);
                        }
                    }
                }
            }
        } catch (Throwable ignored) { }
    }

    private static void extractPost(JSONObject post, List<WallpaperItem> results) {
        String postUrl = post.optString("url", "").replace("&amp;", "&");
        String title = cleanTitle(post.optString("title", "Reddit Wallpaper"));
        String thumbUrl = post.optString("thumbnail", "").replace("&amp;", "&");
        String previewUrl = null;

        // Try extracting best resolution preview
        JSONObject previewObj = post.optJSONObject("preview");
        if (previewObj != null) {
            JSONArray imagesArr = previewObj.optJSONArray("images");
            if (imagesArr != null && imagesArr.length() > 0) {
                JSONObject firstImg = imagesArr.optJSONObject(0);
                if (firstImg != null) {
                    JSONArray resolutions = firstImg.optJSONArray("resolutions");
                    if (resolutions != null && resolutions.length() > 0) {
                        // Pick resolution around index 2 or 3 (~320px - 640px)
                        int pickIdx = Math.min(resolutions.length() - 1, Math.max(0, resolutions.length() / 2));
                        JSONObject resObj = resolutions.optJSONObject(pickIdx);
                        if (resObj != null) {
                            previewUrl = resObj.optString("url", "").replace("&amp;", "&");
                        }
                    }
                    if (previewUrl == null || previewUrl.isEmpty()) {
                        JSONObject sourceObj = firstImg.optJSONObject("source");
                        if (sourceObj != null) {
                            previewUrl = sourceObj.optString("url", "").replace("&amp;", "&");
                        }
                    }
                }
            }
        }

        if (previewUrl == null || previewUrl.isEmpty()) {
            if (thumbUrl.startsWith("http")) {
                previewUrl = thumbUrl;
            } else {
                previewUrl = postUrl;
            }
        }

        if (isImageUrl(postUrl)) {
            results.add(new WallpaperItem(title, postUrl, previewUrl));
        } else if (isImageUrl(previewUrl)) {
            results.add(new WallpaperItem(title, previewUrl, previewUrl));
        }
    }

    private static void parseRss(String xml, List<WallpaperItem> results) {
        try {
            Pattern entryPattern = Pattern.compile("<entry>([\\s\\S]*?)</entry>");
            Matcher entryMatcher = entryPattern.matcher(xml);
            Pattern titlePattern = Pattern.compile("<title>([\\s\\S]*?)</title>");
            Pattern thumbPattern = Pattern.compile("<media:thumbnail\\s+url=\"([^\"]+)\"");
            Pattern iReddPattern = Pattern.compile("href=\"(https://i\\.redd\\.it/[^\"]+)\"");
            Pattern previewPattern = Pattern.compile("src=\"(https://preview\\.redd\\.it/[^\"]+)\"|href=\"(https://preview\\.redd\\.it/[^\"]+)\"");

            while (entryMatcher.find()) {
                String entry = entryMatcher.group(1);
                String title = "Wallpaper";
                Matcher tm = titlePattern.matcher(entry);
                if (tm.find()) {
                    title = cleanTitle(tm.group(1));
                }

                String thumbUrl = null;
                Matcher thm = thumbPattern.matcher(entry);
                if (thm.find()) {
                    thumbUrl = thm.group(1).replace("&amp;", "&");
                }

                String imgUrl = null;
                Matcher irm = iReddPattern.matcher(entry);
                if (irm.find()) {
                    imgUrl = irm.group(1).replace("&amp;", "&");
                }

                if (imgUrl == null) {
                    Matcher prm = previewPattern.matcher(entry);
                    if (prm.find()) {
                        String g = prm.group(1) != null ? prm.group(1) : prm.group(2);
                        if (g != null) imgUrl = g.replace("&amp;", "&");
                    }
                }

                if (imgUrl == null && thumbUrl != null) {
                    imgUrl = thumbUrl;
                }

                if (imgUrl != null) {
                    String preview = thumbUrl != null ? thumbUrl : imgUrl;
                    results.add(new WallpaperItem(title, imgUrl, preview));
                }
            }
        } catch (Throwable ignored) { }
    }

    private static String cleanTitle(String raw) {
        if (raw == null) return "Wallpaper";
        return raw.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .trim();
    }

    private static boolean isImageUrl(String u) {
        if (u == null || u.isEmpty()) return false;
        String lower = u.toLowerCase();
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".webp")
                || lower.contains("i.redd.it") || lower.contains("preview.redd.it") || lower.contains("thumbs.redditmedia.com");
    }

    public static Bitmap downloadBitmap(String urlStr) {
        return downloadBitmap(urlStr, 1920, 1080);
    }

    public static Bitmap downloadBitmap(String urlStr, int maxW, int maxH) {
        if (urlStr == null || urlStr.isEmpty()) return null;
        String cacheKey = urlStr + "_" + maxW + "x" + maxH;
        Bitmap cached = BITMAP_CACHE.get(cacheKey);
        if (cached != null && !cached.isRecycled()) {
            return cached;
        }

        try {
            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Google TV Streamer)");
            if (conn.getResponseCode() == 200) {
                try (InputStream in = conn.getInputStream()) {
                    byte[] data = readAllBytes(in);
                    if (data == null || data.length == 0) return null;

                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    if (maxW > 0 && maxH > 0) {
                        opts.inJustDecodeBounds = true;
                        BitmapFactory.decodeByteArray(data, 0, data.length, opts);
                        opts.inSampleSize = calculateInSampleSize(opts, maxW, maxH);
                        opts.inJustDecodeBounds = false;
                    }
                    opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    Bitmap bmp = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
                    if (bmp != null) {
                        BITMAP_CACHE.put(cacheKey, bmp);
                    }
                    return bmp;
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private static byte[] readAllBytes(InputStream in) {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) {
                out.write(buf, 0, r);
            }
            return out.toByteArray();
        } catch (Throwable t) {
            return null;
        }
    }
}
