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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RedditWallpapers {
    private static final String PREF_NAME = "reddit_wallpapers";
    private static final String KEY_INDEX = "fetch_index";
    private static final String KEY_SAVED_TITLE = "current_wall_title";
    private static final String KEY_CACHED_RSS = "cached_rss_xml";
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    public static final LruCache<String, Bitmap> BITMAP_CACHE = new LruCache<>(40);

    private static List<WallpaperItem> sCached4KItems = null;
    private static long sLastFetchMs = 0L;

    public static class WallpaperItem {
        public final String title;
        public final String imageUrl;
        public final String previewUrl;
        public final String thumbUrl;
        public final int width;
        public final int height;

        public WallpaperItem(String title, String imageUrl, String previewUrl, String thumbUrl, int width, int height) {
            this.title = title;
            this.imageUrl = imageUrl;
            this.previewUrl = (previewUrl != null && !previewUrl.isEmpty()) ? previewUrl : imageUrl;
            this.thumbUrl = (thumbUrl != null && !thumbUrl.isEmpty()) ? thumbUrl : this.previewUrl;
            this.width = width;
            this.height = height;
        }

        public WallpaperItem(String title, String imageUrl, String previewUrl) {
            this(title, imageUrl, previewUrl, previewUrl, 3840, 2160);
        }

        public WallpaperItem(String title, String imageUrl) {
            this(title, imageUrl, imageUrl, imageUrl, 3840, 2160);
        }

        public String resolutionLabel() {
            if (width >= 7680 || height >= 4320) {
                return "8K UHD (" + width + "×" + height + ")";
            } else if (width >= 5120 || height >= 2880) {
                return "5K UHD (" + width + "×" + height + ")";
            } else if (width > 0 && height > 0) {
                return "4K UHD (" + width + "×" + height + ")";
            }
            return "4K UHD";
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
                .getString(KEY_SAVED_TITLE, "r/wallpapers 4K Wallpaper");
    }

    public static void setCurrentWallpaperTitle(Context c, String title) {
        c.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit().putString(KEY_SAVED_TITLE, title).apply();
    }

    public static void fetchWallpaperList(Context context, ListCallback callback) {
        new Thread(() -> {
            try {
                List<WallpaperItem> items = loadSubmissions(context);
                if (items.isEmpty()) {
                    MAIN_HANDLER.post(() -> callback.onError("No 4K wallpapers found on r/wallpapers"));
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
                List<WallpaperItem> items = loadSubmissions(context);
                if (items.isEmpty()) {
                    MAIN_HANDLER.post(() -> callback.onError("No 4K wallpapers found on r/wallpapers"));
                    return;
                }
                SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                int idx = prefs.getInt(KEY_INDEX, 0);
                if (idx >= items.size()) idx = 0;
                WallpaperItem chosen = items.get(idx);
                prefs.edit().putInt(KEY_INDEX, (idx + 1) % items.size()).apply();

                Bitmap bmp = downloadBitmap(chosen.imageUrl, 3840, 2160);
                if (bmp == null) {
                    MAIN_HANDLER.post(() -> callback.onError("Failed to download 4K image: " + chosen.imageUrl));
                    return;
                }
                MAIN_HANDLER.post(() -> callback.onSuccess(chosen, bmp));
            } catch (Throwable t) {
                MAIN_HANDLER.post(() -> callback.onError("Fetch error: " + t.getMessage()));
            }
        }).start();
    }

    private static synchronized List<WallpaperItem> loadSubmissions(Context context) {
        long now = System.currentTimeMillis();
        if (sCached4KItems != null && !sCached4KItems.isEmpty() && (now - sLastFetchMs) < 90_000L) {
            return new ArrayList<>(sCached4KItems);
        }

        List<WallpaperItem> results = new ArrayList<>();
        String[] endpoints = {
            "https://www.reddit.com/r/wallpapers/new/.rss?limit=100",
            "https://www.reddit.com/r/wallpapers/new/.rss",
            "https://www.reddit.com/r/wallpapers/search.rss?q=3840x2160+OR+4k+OR+5120&restrict_sr=on&sort=new&limit=100"
        };

        SharedPreferences prefs = context != null
                ? context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                : null;

        for (String ep : endpoints) {
            try {
                URL url = new URL(ep);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(12000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; Feedly/1.0; +http://www.feedly.com/fetcher.html)");
                conn.setRequestProperty("Accept", "application/atom+xml, application/xml, text/xml, */*");

                int respCode = conn.getResponseCode();
                if (respCode == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();

                    String body = sb.toString().trim();
                    if (body.contains("<entry>")) {
                        if (prefs != null) {
                            prefs.edit().putString(KEY_CACHED_RSS, body).apply();
                        }
                        parseRss4K(body, results);
                    }
                }
                conn.disconnect();
                if (!results.isEmpty()) break;
            } catch (Throwable ignored) {
            }
        }

        // If rate-limited (HTTP 429) or offline, fall back to cached RSS or memory cache
        if (results.isEmpty() && sCached4KItems != null && !sCached4KItems.isEmpty()) {
            return new ArrayList<>(sCached4KItems);
        }
        if (results.isEmpty() && prefs != null) {
            String cachedXml = prefs.getString(KEY_CACHED_RSS, null);
            if (cachedXml != null && !cachedXml.isEmpty()) {
                parseRss4K(cachedXml, results);
            }
        }

        if (!results.isEmpty()) {
            sCached4KItems = new ArrayList<>(results);
            sLastFetchMs = System.currentTimeMillis();
        }
        return results;
    }

    private static class RawCandidate {
        final String title;
        final String fullImageUrl;
        final String thumbUrl;
        final int order;
        int width;
        int height;
        boolean needProbe;

        RawCandidate(String title, String fullImageUrl, String thumbUrl, int order, int width, int height, boolean needProbe) {
            this.title = title;
            this.fullImageUrl = fullImageUrl;
            this.thumbUrl = thumbUrl;
            this.order = order;
            this.width = width;
            this.height = height;
            this.needProbe = needProbe;
        }
    }

    private static void parseRss4K(String xml, List<WallpaperItem> results) {
        try {
            Pattern entryPattern = Pattern.compile("<entry>([\\s\\S]*?)</entry>");
            Matcher entryMatcher = entryPattern.matcher(xml);
            Pattern titlePattern = Pattern.compile("<title>([\\s\\S]*?)</title>");
            Pattern thumbPattern = Pattern.compile("<media:thumbnail\\s+url=\"([^\"]+)\"");
            Pattern reddImgPattern = Pattern.compile("https://(?:i|preview)\\.redd\\.it/([a-zA-Z0-9_-]+\\.(?:jpg|jpeg|png|webp))");
            Pattern dimPattern = Pattern.compile("(\\d{3,5})\\s*[xX×]\\s*(\\d{3,5})");
            Pattern lowResTagPattern = Pattern.compile("(?i)\\b(1080p|1440p|720p|2k|1920x1080|2560x1440)\\b");

            List<RawCandidate> candidates = new ArrayList<>();
            Set<String> seenUrls = new HashSet<>();
            int order = 0;
            int probeCount = 0;

            while (entryMatcher.find()) {
                String rawEntry = entryMatcher.group(1);
                String entry = rawEntry.replace("&quot;", "\"").replace("&amp;", "&");

                String title = "Wallpaper";
                Matcher tm = titlePattern.matcher(rawEntry);
                if (tm.find()) {
                    title = cleanTitle(tm.group(1));
                }

                // Skip explicitly tagged lower-res titles
                if (lowResTagPattern.matcher(title).find()) {
                    continue;
                }

                Matcher imgMatcher = reddImgPattern.matcher(entry);
                if (!imgMatcher.find()) {
                    continue;
                }
                // Always construct the direct uncompressed i.redd.it URL
                String filename = imgMatcher.group(1);
                String fullImageUrl = "https://i.redd.it/" + filename;
                if (!seenUrls.add(fullImageUrl)) {
                    continue;
                }

                String thumbUrl = fullImageUrl;
                Matcher thm = thumbPattern.matcher(rawEntry);
                if (thm.find()) {
                    String candidateThumb = thm.group(1).replace("&amp;", "&");
                    // Only use media:thumbnail for the tiny list icon if it is 640px; never use 140px
                    if (candidateThumb.contains("width=640")) {
                        thumbUrl = candidateThumb;
                    }
                }

                Matcher dm = dimPattern.matcher(title);
                if (dm.find()) {
                    try {
                        int w = Integer.parseInt(dm.group(1));
                        int h = Integer.parseInt(dm.group(2));
                        if (w >= 3840 && h >= 2160) {
                            candidates.add(new RawCandidate(title, fullImageUrl, thumbUrl, order++, w, h, false));
                        }
                        // If dimensions in title are < 3840x2160, strictly exclude it!
                    } catch (NumberFormatException ignored) { }
                } else {
                    // No dimensions in title: probe actual image header (up to 16 items so it stays fast)
                    if (probeCount < 16) {
                        probeCount++;
                        candidates.add(new RawCandidate(title, fullImageUrl, thumbUrl, order++, 0, 0, true));
                    }
                }
            }

            // Probe actual pixel dimensions in parallel for candidates without dimensions in title
            List<RawCandidate> toProbe = new ArrayList<>();
            for (RawCandidate c : candidates) {
                if (c.needProbe) toProbe.add(c);
            }

            if (!toProbe.isEmpty()) {
                ExecutorService pool = Executors.newFixedThreadPool(Math.min(6, toProbe.size()));
                List<Callable<Void>> tasks = new ArrayList<>();
                for (RawCandidate c : toProbe) {
                    tasks.add(() -> {
                        int[] dims = probeImageDimensions(c.fullImageUrl);
                        if (dims != null) {
                            c.width = dims[0];
                            c.height = dims[1];
                        }
                        return null;
                    });
                }
                try {
                    pool.invokeAll(tasks, 4, TimeUnit.SECONDS);
                } catch (Throwable ignored) {
                } finally {
                    pool.shutdownNow();
                }
            }

            for (RawCandidate c : candidates) {
                if (c.width >= 3840 && c.height >= 2160) {
                    String displayTitle = c.title;
                    if (!dimPattern.matcher(displayTitle).find()) {
                        displayTitle = displayTitle + " [" + c.width + "×" + c.height + "]";
                    }
                    results.add(new WallpaperItem(displayTitle, c.fullImageUrl, c.fullImageUrl, c.thumbUrl, c.width, c.height));
                }
            }
        } catch (Throwable ignored) { }
    }

    /**
     * Fetches only the first 32 KB of an image from i.redd.it using HTTP Range header
     * and decodes its header bounds (width & height) without downloading the full file.
     */
    private static int[] probeImageDimensions(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Google TV Streamer)");
            conn.setRequestProperty("Range", "bytes=0-32767");
            int code = conn.getResponseCode();
            if (code == 200 || code == 206) {
                try (InputStream in = conn.getInputStream()) {
                    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int total = 0;
                    int r;
                    while ((r = in.read(buf)) != -1) {
                        out.write(buf, 0, r);
                        total += r;
                        if (total >= 32768) break;
                    }
                    byte[] headerBytes = out.toByteArray();
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeByteArray(headerBytes, 0, headerBytes.length, opts);
                    if (opts.outWidth > 0 && opts.outHeight > 0) {
                        return new int[]{opts.outWidth, opts.outHeight};
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static String cleanTitle(String raw) {
        if (raw == null) return "Wallpaper";
        return raw.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .trim();
    }

    public static Bitmap downloadBitmap(String urlStr) {
        return downloadBitmap(urlStr, 3840, 2160);
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
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(15000);
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
            byte[] buf = new byte[16384];
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
