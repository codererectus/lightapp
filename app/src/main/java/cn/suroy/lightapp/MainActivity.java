package cn.suroy.lightapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.DialogInterface;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends Activity {

    private WebView mWebView;
    private ValueCallback<Uri[]> mFilePathCallback;
    private final static int FILE_CHOOSER_RESULT_CODE = 1;
    private final static int PERM_CODE = 100;

    private String backMode = "default";

    @Override
    public void onBackPressed() {
        if ("game".equals(backMode)) {
            mWebView.evaluateJavascript("window.dispatchEvent(new Event('androidBack'))", null);
            return;
        }
        if (mWebView.canGoBack()) {
            mWebView.goBack();
            return;
        }
        if ("ask".equals(backMode)) {
            new AlertDialog.Builder(this)
                    .setMessage("Exit the app?")
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) { finish(); }
                    })
                    .setNegativeButton("No", null)
                    .show();
            return;
        }
        super.onBackPressed();
    }

    private final java.util.HashSet<String> wanted = new java.util.HashSet<String>();

    // Request only the permissions chosen in the builder (config.json "perms")
    private void askPermissions() {
        if (Build.VERSION.SDK_INT < 23) return;
        ArrayList<String> p = new ArrayList<String>();
        if (wanted.contains("camera")) p.add("android.permission.CAMERA");
        if (wanted.contains("mic")) p.add("android.permission.RECORD_AUDIO");
        if (wanted.contains("loc")) {
            p.add("android.permission.ACCESS_FINE_LOCATION");
            p.add("android.permission.ACCESS_COARSE_LOCATION");
        }
        if (wanted.contains("phone")) p.add("android.permission.READ_PHONE_STATE");
        if (wanted.contains("files")) {
            if (Build.VERSION.SDK_INT >= 33) {
                p.add("android.permission.READ_MEDIA_IMAGES");
                p.add("android.permission.READ_MEDIA_VIDEO");
                p.add("android.permission.READ_MEDIA_AUDIO");
            } else {
                p.add("android.permission.READ_EXTERNAL_STORAGE");
                p.add("android.permission.WRITE_EXTERNAL_STORAGE");
            }
        }
        if (wanted.contains("notif") && Build.VERSION.SDK_INT >= 33) p.add("android.permission.POST_NOTIFICATIONS");
        if (wanted.contains("act") && Build.VERSION.SDK_INT >= 29) p.add("android.permission.ACTIVITY_RECOGNITION");
        ArrayList<String> need = new ArrayList<String>();
        for (String s : p) {
            if (checkSelfPermission(s) != PackageManager.PERMISSION_GRANTED) need.add(s);
        }
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), PERM_CODE);
        }
    }

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        decorView.setBackgroundColor(android.graphics.Color.BLACK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }

        setContentView(R.layout.activity_main);
        mWebView = findViewById(R.id.activity_main_webview);

        WebSettings webSettings = mWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setLoadWithOverviewMode(true);
        webSettings.setUseWideViewPort(true);
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAppCacheEnabled(true);
        webSettings.setDatabaseEnabled(true);
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webSettings.setGeolocationEnabled(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setMediaPlaybackRequiresUserGesture(false);

        mWebView.setWebViewClient(new MyWebViewClient());

        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (mFilePathCallback != null) {
                    mFilePathCallback.onReceiveValue(null);
                }
                mFilePathCallback = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILE_CHOOSER_RESULT_CODE);
                } catch (Exception e) {
                    mFilePathCallback = null;
                    return false;
                }
                return true;
            }

            // камера и микрофон для страницы
            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                runOnUiThread(new Runnable() {
                    public void run() {
                        request.grant(request.getResources());
                    }
                });
            }

            // геолокация для страницы
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, true, false);
            }
        });

        mWebView.loadUrl("file:///android_asset/index.html");
        try {
            java.io.InputStream is = getAssets().open("config.json");
            byte[] b = new byte[is.available()];
            is.read(b);
            is.close();
            org.json.JSONObject c = new org.json.JSONObject(new String(b, "UTF-8"));
            String o = c.optString("orientation", "auto");
            backMode = c.optString("back", "default");
            if (c.optBoolean("keepOn", false)) {
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            }
            org.json.JSONArray pa = c.optJSONArray("perms");
            if (pa != null) {
                for (int i = 0; i < pa.length(); i++) wanted.add(pa.getString(i));
            }
            if (o.equals("landscape")) {
                setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
            } else if (o.equals("portrait")) {
                setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
            }
            if (c.optBoolean("fullscreen", false)) {
                getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            }
        } catch (Exception e) {
        }
        mWebView.addJavascriptInterface(new SaveBridge(), "AndroidSave");
        mWebView.addJavascriptInterface(new AppBridge(), "AndroidApp");

        askPermissions();
    }

    public WebView getWebView() {
        return mWebView;
    }

    // JS API: window.AndroidApp.*
    public class AppBridge {
        @android.webkit.JavascriptInterface
        public void notify(String title, String text) {
            AlarmReceiver.show(MainActivity.this, (int) (System.currentTimeMillis() & 0x7fffffff), title, text);
        }

        @android.webkit.JavascriptInterface
        public void scheduleAlarm(int id, long whenMillis, String title, String text) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(MainActivity.this, AlarmReceiver.class);
            i.putExtra("id", id);
            i.putExtra("title", title);
            i.putExtra("text", text);
            int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
            PendingIntent pi = PendingIntent.getBroadcast(MainActivity.this, id, i, fl);
            try {
                if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pi);
                else am.setExact(AlarmManager.RTC_WAKEUP, whenMillis, pi);
            } catch (SecurityException e) {
                if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, whenMillis, pi);
                else am.set(AlarmManager.RTC_WAKEUP, whenMillis, pi);
            }
        }

        @android.webkit.JavascriptInterface
        public void cancelAlarm(int id) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            Intent i = new Intent(MainActivity.this, AlarmReceiver.class);
            int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
            am.cancel(PendingIntent.getBroadcast(MainActivity.this, id, i, fl));
        }

        @android.webkit.JavascriptInterface
        public void share(final String text) {
            runOnUiThread(new Runnable() {
                public void run() {
                    Intent i = new Intent(Intent.ACTION_SEND);
                    i.setType("text/plain");
                    i.putExtra(Intent.EXTRA_TEXT, text);
                    startActivity(Intent.createChooser(i, "Share"));
                }
            });
        }

        // vibrate("200") or pattern "0,100,50,100"
        @android.webkit.JavascriptInterface
        public void vibrate(String pattern) {
            try {
                Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                String[] parts = pattern.split(",");
                long[] p = new long[parts.length];
                for (int k = 0; k < parts.length; k++) p[k] = Long.parseLong(parts[k].trim());
                if (p.length == 1) {
                    if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(p[0], VibrationEffect.DEFAULT_AMPLITUDE));
                    else v.vibrate(p[0]);
                } else {
                    if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(p, -1));
                    else v.vibrate(p, -1);
                }
            } catch (Exception e) {}
        }

        @android.webkit.JavascriptInterface
        public void copy(final String text) {
            runOnUiThread(new Runnable() {
                public void run() {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    cm.setPrimaryClip(ClipData.newPlainText("text", text));
                }
            });
        }

        @android.webkit.JavascriptInterface
        public String paste() {
            try {
                ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData d = cm.getPrimaryClip();
                if (d != null && d.getItemCount() > 0) return String.valueOf(d.getItemAt(0).coerceToText(MainActivity.this));
            } catch (Exception e) {}
            return "";
        }

        @android.webkit.JavascriptInterface
        public void exit() {
            runOnUiThread(new Runnable() {
                public void run() { finish(); }
            });
        }
    }

    public class SaveBridge {
        @android.webkit.JavascriptInterface
        public void save(String name, String mime, String b64) {
            String msg;
            try {
                byte[] data = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                java.io.OutputStream os;
                if (android.os.Build.VERSION.SDK_INT >= 29) {
                    android.content.ContentValues v = new android.content.ContentValues();
                    v.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, name);
                    v.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime);
                    v.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Download");
                    android.net.Uri u = getContentResolver().insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    os = getContentResolver().openOutputStream(u);
                } else {
                    java.io.File d = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
                    os = new java.io.FileOutputStream(new java.io.File(d, name));
                }
                os.write(data);
                os.close();
                msg = "Saved to Downloads: " + name;
            } catch (Exception e) {
                msg = "Error: " + e;
            }
            final String m = msg;
            runOnUiThread(new Runnable() {
                public void run() {
                    android.widget.Toast.makeText(MainActivity.this, m, android.widget.Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_CHOOSER_RESULT_CODE) {
            if (mFilePathCallback != null) {
                Uri[] results = null;
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        String dataString = data.getDataString();
                        if (dataString != null) {
                            results = new Uri[]{Uri.parse(dataString)};
                        }
                    }
                }
                mFilePathCallback.onReceiveValue(results);
                mFilePathCallback = null;
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private class MyWebViewClient extends WebViewClient {
        @Override
        public void onPageFinished(WebView view, String url) {
            view.evaluateJavascript(
                "(function(){var c=getComputedStyle(document.body).backgroundColor;"
                + "if(c==='rgba(0, 0, 0, 0)')c=getComputedStyle(document.documentElement).backgroundColor;"
                + "return c;})()",
                new ValueCallback<String>() {
                    public void onReceiveValue(String v) {
                        try {
                            java.util.regex.Matcher m = java.util.regex.Pattern
                                .compile("(\\d+)[, ]+(\\d+)[, ]+(\\d+)(?:[, /]+([\\d.]+))?").matcher(v);
                            if (m.find()) {
                                if (m.group(4) != null && Float.parseFloat(m.group(4)) == 0f) return;
                                getWindow().getDecorView().setBackgroundColor(android.graphics.Color.rgb(
                                    Integer.parseInt(m.group(1)),
                                    Integer.parseInt(m.group(2)),
                                    Integer.parseInt(m.group(3))));
                            }
                        } catch (Exception e) {}
                    }
                });
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, final String url) {
            if (url.endsWith(".pdf") || url.endsWith(".zip") || url.endsWith(".doc")
                    || url.endsWith(".docx") || url.endsWith(".7z")) {
                final WebView wv = view;
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Download")
                        .setMessage("Download this file?")
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                                String cookies = CookieManager.getInstance().getCookie(url);
                                String fileName = URLUtil.guessFileName(url, null, null);
                                request.addRequestHeader("cookie", cookies);
                                request.addRequestHeader("User-Agent", wv.getSettings().getUserAgentString());
                                request.setDescription("Downloading file...");
                                request.setTitle(fileName);
                                request.allowScanningByMediaScanner();
                                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
                                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                                dm.enqueue(request);
                                Toast.makeText(getApplicationContext(), "Downloading file...", Toast.LENGTH_LONG).show();
                            }
                        })
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.dismiss();
                            }
                        })
                        .setIcon(android.R.drawable.ic_menu_save)
                        .show();
                return true;
            }
            if (url.startsWith("http://") || url.startsWith("https://")
                    || url.startsWith("mailto:") || url.startsWith("tel:")) {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                } catch (Exception e) {}
                return true;
            }
            return false;
        }
    }
}
