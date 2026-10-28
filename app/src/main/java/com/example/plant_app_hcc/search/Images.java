package com.example.plant_app_hcc.search;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;
import com.example.plant_app_hcc.R;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Background image loading with bounded memory cache, disk cache, and recycled-view protection. */
public final class Images {
  private final Context context;
  private final ExecutorService executor = Executors.newFixedThreadPool(3);
  private final Handler main = new Handler(Looper.getMainLooper());
  private final LruCache<String, Bitmap> memory =
      new LruCache<String, Bitmap>(12 * 1024 * 1024) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
          return value.getByteCount();
        }
      };

  public Images(Context context) {
    this.context = context.getApplicationContext();
  }

  public void load(ImageView view, String address) {
    String key = address == null ? "" : address;
    view.setTag(key);
    view.setImageResource(R.drawable.wp_search_leaf);
    view.setScaleType(ImageView.ScaleType.CENTER_CROP);
    if (key.isEmpty()) return;
    Bitmap cached = memory.get(key);
    if (cached != null) {
      view.setImageBitmap(cached);
      return;
    }
    executor.execute(
        () -> {
          Bitmap bitmap = null;
          try {
            if (key.startsWith("asset:")) {
              try (InputStream in = context.getAssets().open(key.substring(6))) {
                bitmap = decode(in);
              }
            } else if (key.startsWith("https://")) {
              String digest =
                  android.util.Base64.encodeToString(
                      MessageDigest.getInstance("SHA-256")
                          .digest(key.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                      android.util.Base64.URL_SAFE | android.util.Base64.NO_WRAP);
              File dir = new File(context.getCacheDir(), "plant-images");
              dir.mkdirs();
              File file = new File(dir, digest + ".jpg");
              if (file.exists())
                try (InputStream in = new FileInputStream(file)) {
                  bitmap = decode(in);
                }
              if (bitmap == null) {
                HttpURLConnection connection = PlantCatalog.open(key);
                try (InputStream in = connection.getInputStream()) {
                  bitmap = decode(in);
                } finally {
                  connection.disconnect();
                }
                if (bitmap != null) {
                  try (FileOutputStream out = new FileOutputStream(file)) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
                  }
                  File[] files = dir.listFiles();
                  if (files != null && files.length > 70) {
                    java.util.Arrays.sort(
                        files, java.util.Comparator.comparingLong(File::lastModified));
                    for (int i = 0; i < files.length - 60; i++) files[i].delete();
                  }
                }
              }
            }
          } catch (Exception ignored) {
            /* The plant icon is an intentional offline/missing-image fallback. */
          }
          if (bitmap != null) {
            memory.put(key, bitmap);
            Bitmap ready = bitmap;
            main.post(
                () -> {
                  if (key.equals(view.getTag())) view.setImageBitmap(ready);
                });
          }
        });
  }

  private Bitmap decode(InputStream in) throws java.io.IOException {
    // API photographs can be full-resolution originals. Bound both downloads and decoded memory.
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    int read;
    while ((read = in.read(chunk)) != -1) {
      if (out.size() + read > 8 * 1024 * 1024) throw new java.io.IOException("Photo is too large");
      out.write(chunk, 0, read);
    }
    byte[] bytes = out.toByteArray();
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inJustDecodeBounds = true;
    BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
    if (options.outWidth < 1 || options.outHeight < 1) return null;
    options.inSampleSize = 1;
    while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 800)
      options.inSampleSize *= 2;
    options.inJustDecodeBounds = false;
    options.inPreferredConfig = Bitmap.Config.RGB_565;
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
  }

  public void close() {
    executor.shutdownNow();
  }
}
