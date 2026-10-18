package cn.suroy.lightapp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ListView;
import android.widget.Toast;
import androidx.drawerlayout.widget.DrawerLayout;


public class MainActivity extends Activity {

    private WebView mWebView;
    private ValueCallback<Uri[]> mFilePathCallback;
    private final static int FILE_CHOOSER_RESULT_CODE = 1;

    /**
     * 重写 Android 系统的返回按钮按下事件处理方法。
     * 当按下返回按钮时，会检查 WebView 是否有历史记录。
     * 如果有历史记录，则让 WebView 返回到上一个页面；
     * 如果没有历史记录，则调用父类的返回按钮处理方法。
     */
    @Override
    public void onBackPressed() {
        // 检查 WebView 是否可以返回上一个页面
        if (mWebView.canGoBack()) {
            // 若 WebView 有历史记录，返回上一个页面
            mWebView.goBack();
        } else {
            // 若 WebView 没有历史记录，调用父类的返回按钮处理方法
            super.onBackPressed();
        }
    }

    /**
     * 重写 onCreate 方法，用于在 Activity 创建时进行初始化操作。
     *
     * @param savedInstanceState 如果 Activity 是重新创建的，则包含之前保存的状态；否则为 null。
     */
    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        // 调用父类的 onCreate 方法，确保 Activity 正确初始化
        super.onCreate(savedInstanceState);

        // 设置全屏显示
        // 请求窗口特性，去除标题栏，使应用界面不显示标题栏，增加显示区域
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        // 获取当前窗口的 DecorView，DecorView 是窗口的顶级视图，包含了状态栏、标题栏等
        View decorView = getWindow().getDecorView();
        // 设置 UI 选项为全屏模式，该标志会隐藏状态栏，使应用占据整个屏幕空间
        int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
        // 将设置好的 UI 选项应用到 DecorView 上，从而实现全屏显示效果
        decorView.setSystemUiVisibility(uiOptions);
        // 检查当前 Android 系统版本是否大于等于 Android 9.0（API 级别 28）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // 获取当前窗口的布局参数对象，用于修改窗口的各种属性
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            // 设置布局在刘海屏等异形屏幕上的显示模式为短边模式
            // 短边模式会让内容显示在异形区域的两侧，避免内容被遮挡
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            // 将修改后的布局参数应用到当前窗口上，使设置生效
            getWindow().setAttributes(lp);
        }

        // 设置当前 Activity 的布局为 activity_main.xml
        setContentView(R.layout.activity_main);
        // 通过 ID 查找布局中的 WebView 控件，并将其赋值给成员变量 mWebView
        mWebView = findViewById(R.id.activity_main_webview);
        DrawerLayout mDrawerLayout = findViewById(R.id.drawer_layout);
        ListView mDrawerList = findViewById(R.id.left_drawer);

        // 初始化 DrawerManager
        new DrawerManager(this, mDrawerLayout, mDrawerList);

        // 获取 WebView 的设置对象
        WebSettings webSettings = mWebView.getSettings();
        // 启用 JavaScript 支持，使得网页中的 JavaScript 代码可以正常运行
        webSettings.setJavaScriptEnabled(true);
        // 设置 WebView 以概览模式加载页面，根据页面宽度自动缩放
        webSettings.setLoadWithOverviewMode(true);
        // 使用宽视口，让页面可以自适应 WebView 的宽度
        webSettings.setUseWideViewPort(true);
        // 启用 WebView 的内置缩放控件
        webSettings.setBuiltInZoomControls(true);
        // 隐藏缩放控件的显示
        webSettings.setDisplayZoomControls(false);
        // 启用 DOM 存储，允许网页使用 HTML5 的 DOM Storage 功能
        webSettings.setDomStorageEnabled(true);
        // 启用应用缓存，提高网页加载速度
        webSettings.setAppCacheEnabled(true);
        // 启用数据库支持，允许网页使用 HTML5 的数据库功能
        webSettings.setDatabaseEnabled(true);
        // 设置缓存模式为默认模式，根据缓存策略加载页面
        webSettings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // 设置 WebViewClient，用于处理网页加载过程中的各种事件，确保链接在 WebView 内打开
        mWebView.setWebViewClient(new MyWebViewClient());

        // 设置 WebChromeClient，用于处理 WebView 中的一些特殊事件，如文件上传
        mWebView.setWebChromeClient(new WebChromeClient() {
            /**
             * 处理 Android 5.0 及以上版本的文件选择器事件。
             *
             * @param webView          触发文件选择器的 WebView 实例。
             * @param filePathCallback 用于接收用户选择的文件 URI 数组的回调函数。
             * @param fileChooserParams 文件选择器的参数对象，包含选择文件的相关信息。
             * @return 如果成功处理事件，则返回 true；否则返回 false。
             */
            // Para Android 5.0+
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                // 如果之前已经有文件选择器的回调，先将其置为 null
                if (mFilePathCallback != null) {
                    mFilePathCallback.onReceiveValue(null);
                }
                // 将新的文件选择器回调赋值给成员变量
                mFilePathCallback = filePathCallback;
                // 创建文件选择器的 Intent
                Intent intent = fileChooserParams.createIntent();
                try {
                    // 启动文件选择器的 Activity，并等待结果
                    startActivityForResult(intent, FILE_CHOOSER_RESULT_CODE);
                } catch (Exception e) {
                    // 如果启动过程中出现异常，将回调置为 null 并返回 false
                    mFilePathCallback = null;
                    return false;
                }
                // 成功处理事件，返回 true
                return true;
            }
        });

        // 加载远程资源，这里是一个示例 URL
      //  mWebView.loadUrl("https://suroy.cn");
mWebView.loadUrl("file:///android_asset/index.html");
        // 加载本地资源，这里是 Android 资产目录下的 index.html 文件
        mWebView.getSettings().setDomStorageEnabled(true);
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
            android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
            | android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
} catch (Exception e) {
    }
mWebView.addJavascriptInterface(new SaveBridge(), "AndroidSave");
        // mWebView.loadUrl("file:///android_asset/index.html");
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
            msg = "Сохранено в Загрузки: " + name;
        } catch (Exception e) {
            msg = "Ошибка: " + e;
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

    /**
     * 自定义的 WebViewClient 类，用于处理 WebView 中的 URL 加载事件。
     */
    private class MyWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // 如果 URL 是一个下载文件，手动处理下载
            if (url.endsWith(".pdf") || url.endsWith(".zip") || url.endsWith(".doc")
                    || url.endsWith(".docx") || url.endsWith(".7z") ) {
                // 显示一个确认对话框，询问用户是否要下载文件
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("下载")
                        .setMessage("您要下载此文件吗？")
                        // 设置确认按钮的点击事件
                        .setPositiveButton("是", new DialogInterface.OnClickListener() {
                            /**
                             * 确认按钮的点击事件处理方法。
                             *
                             * @param dialog 对话框实例。
                             * @param which  被点击的按钮的索引。
                             */
                            public void onClick(DialogInterface dialog, int which) {
                                // 创建一个下载请求
                                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                                // 获取 URL 的 cookie 信息
                                String cookies = CookieManager.getInstance().getCookie(url);
                                // 猜测文件名
                                String fileName = URLUtil.guessFileName(url, null, null);
                                // 添加 cookie 到请求头
                                request.addRequestHeader("cookie", cookies);
                                // 添加用户代理到请求头
                                request.addRequestHeader("User-Agent", view.getSettings().getUserAgentString());
                                // 设置下载描述
                                request.setDescription("正在下载文件...");
                                // 设置下载标题
                                request.setTitle(fileName);
                                // 允许媒体扫描器扫描下载的文件
                                request.allowScanningByMediaScanner();
                                // 设置下载通知的可见性
                                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                                // 设置下载文件的保存位置
                                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);

                                // 获取下载管理器实例
                                DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
                                // 将下载请求加入队列
                                dm.enqueue(request);
                                // 显示一个提示消息，告知用户正在下载文件
                                Toast.makeText(getApplicationContext(), "正在下载文件...", Toast.LENGTH_LONG).show();
                            }
                        })
                        // 设置取消按钮的点击事件
                        .setNegativeButton("否", new DialogInterface.OnClickListener() {
                            /**
                             * 取消按钮的点击事件处理方法。
                             *
                             * @param dialog 对话框实例。
                             * @param which  被点击的按钮的索引。
                             */
                            public void onClick(DialogInterface dialog, int which) {
                                // 关闭对话框
                                dialog.dismiss();
                            }
                        })
                        // 设置对话框的图标
                        .setIcon(android.R.drawable.ic_menu_save)
                        // 显示对话框
                        .show();
                return true; // 阻止 WebView 直接加载文件 URL
            }

            // 允许其他页面的 URL 在 WebView 中加载
            return false;
        }
    }


}
