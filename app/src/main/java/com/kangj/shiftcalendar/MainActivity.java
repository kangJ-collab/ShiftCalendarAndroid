package com.kangj.shiftcalendar;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.Window;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int FILE_CHOOSER_REQUEST = 3011;
    private static final String ONLINE_URL = "https://kangj-collab.github.io/shiftcalendar/";
    private static final String OFFLINE_URL = "file:///android_asset/www/index.html";

    private WebView webView;
    private ValueCallback<Uri[]> fileChooserCallback;
    private boolean fallbackLoaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            configureSystemBars();
            setContentView(R.layout.activity_main);
            webView = findViewById(R.id.webView);
            configureWebView();
            webView.addJavascriptInterface(new AndroidAlarmBridge(this), "AndroidAlarm");
            webView.loadUrl(ONLINE_URL);
        } catch (Throwable error) {
            Toast.makeText(this, "교대달력을 시작하지 못했습니다.", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @SuppressWarnings("deprecation")
    private void configureSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(Color.WHITE);
        window.setNavigationBarColor(Color.WHITE);
        int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    @SuppressWarnings("deprecation")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        WebView.setWebContentsDebuggingEnabled(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                injectNativeControls();
                syncScheduleFromWeb();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (request.isForMainFrame() && !fallbackLoaded) {
                    fallbackLoaded = true;
                    view.loadUrl(OFFLINE_URL);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return openExternalIfNeeded(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return openExternalIfNeeded(Uri.parse(url));
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(
                WebView webView,
                ValueCallback<Uri[]> filePathCallback,
                FileChooserParams fileChooserParams
            ) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = filePathCallback;
                try {
                    startActivityForResult(fileChooserParams.createIntent(), FILE_CHOOSER_REQUEST);
                    return true;
                } catch (Exception error) {
                    fileChooserCallback = null;
                    Toast.makeText(MainActivity.this, "파일 선택기를 열 수 없습니다.", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
        });
    }

    private void injectNativeControls() {
        if (webView == null) return;
        String script = "(function(){" +
            "if(document.getElementById('androidNativeAlarmFab'))return;" +
            "var b=document.createElement('button');" +
            "b.id='androidNativeAlarmFab';b.type='button';b.textContent='알람';" +
            "b.setAttribute('aria-label','근무 알람 설정');" +
            "b.style.cssText='position:fixed;right:14px;bottom:calc(82px + env(safe-area-inset-bottom));z-index:20000;border:0;border-radius:999px;padding:11px 15px;background:#315d73;color:#fff;font-weight:800;box-shadow:0 4px 14px rgba(0,0,0,.24)';" +
            "b.onclick=function(){try{AndroidAlarm.openAlarmSettings()}catch(e){alert(\'알람 설정을 열 수 없습니다.\')}};" +
            "document.body.appendChild(b);" +
            "})();";
        webView.evaluateJavascript(script, null);
    }

    void syncScheduleFromWeb() {
        if (webView == null) return;
        String script = "(function(){try{" +
            "if(typeof getMyDisplayShift!=='function')return 'not_ready';" +
            "var out=[];var now=new Date();now=new Date(now.getFullYear(),now.getMonth(),now.getDate());" +
            "for(var i=0;i<150;i++){var d=new Date(now);d.setDate(now.getDate()+i);" +
            "var k=(typeof dateKey==='function'?dateKey(d):(d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0')));" +
            "out.push({date:k,shift:String(getMyDisplayShift(d)||'')});}" +
            "AndroidAlarm.syncSchedule(JSON.stringify(out));return 'ok';" +
            "}catch(e){return 'error:'+e.message}})();";
        webView.postDelayed(() -> webView.evaluateJavascript(script, null), 900);
    }

    private boolean openExternalIfNeeded(Uri uri) {
        if (uri == null) return false;
        String scheme = uri.getScheme();
        if (scheme == null || scheme.equals("file") || scheme.equals("data") || scheme.equals("blob")) return false;
        String host = uri.getHost();
        if ((scheme.equals("http") || scheme.equals("https")) &&
            ("kangj-collab.github.io".equals(host) || "raw.githubusercontent.com".equals(host))) {
            return false;
        }
        if (scheme.equals("http") || scheme.equals("https") || scheme.equals("mailto") || scheme.equals("tel")) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, uri));
            } catch (ActivityNotFoundException error) {
                Toast.makeText(this, "연결할 앱을 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return false;
    }

    void openAlarmSettings() {
        startActivity(new Intent(this, AlarmSettingsActivity.class));
    }

    void notifyPermissionStateChanged() {
        if (webView == null) return;
        webView.evaluateJavascript("window.onAndroidAlarmPermissionChanged&&window.onAndroidAlarmPermissionChanged();", null);
    }

    void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + getPackageName()));
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.postDelayed(this::syncScheduleFromWeb, 400);
            webView.postDelayed(this::notifyPermissionStateChanged, 600);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != FILE_CHOOSER_REQUEST || fileChooserCallback == null) return;
        Uri[] result = WebChromeClient.FileChooserParams.parseResult(resultCode, data);
        fileChooserCallback.onReceiveValue(result);
        fileChooserCallback = null;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        notifyPermissionStateChanged();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("AndroidAlarm");
            webView.destroy();
        }
        super.onDestroy();
    }
}
