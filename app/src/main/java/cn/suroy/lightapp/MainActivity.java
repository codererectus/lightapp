package cn.suroy.lightapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.DialogInterface;
import android.content.Intent;
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

    @Override
    public void onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    // Запрос опасных разрешений при запуске
    private void askPermissions() {
        if (Build.VERSION.SDK_INT < 23) return;
        ArrayList<String> p = new ArrayList<String>();
        p.add("android.permission.CAMERA");
        p.add("android.permission.RECORD_AUDIO");
        p.add("android.permission.ACCESS_FINE_LOCATION");
        p.add("android.permission.ACCESS_COARSE_LOCATION");
        p.add("android.permission.READ_PHONE_STATE");
        if (Build.VERSION.SDK_INT >= 33) {
            p.add("android.permission.READ_MEDIA_IMAGES");
            p.add("android.permission.READ_MEDIA_VIDEO");
            p.add("android.permission.READ_MEDIA_AUDIO");
            p.add("android.permission.POST_NOTIFICATIONS");
        } else {
            p.add("android.permission.READ_EXTERNAL_STORAGE");
            p.add("android.permission.WRITE_EXTERNAL_STORAGE");
        }
        if (Build.VERSION.SDK_INT >= 29) {
            p.add("android.permission.ACTIVITY_RECOGNITION");
        }
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
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
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

        askPermissions();
    }

    public WebView getWebView() {
        return mWebView;
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
            return false;
        }
    }
}
