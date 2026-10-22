package com.pdzdh.calcutool;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebViewClient;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

/** 小游戏容器：全屏 WebView 加载内置 HTML5 游戏。 */
public class GameActivity extends Activity {

    private WebView web;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0A0A18);

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        web.setBackgroundColor(0xFF0A0A18);
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage m) {
                Log.i("GameJS", m.message() + " @" + m.lineNumber());
                return true;
            }
        });
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView v, WebResourceRequest req, WebResourceError err) {
                Log.e("GameJS", "load error: " + err.getDescription());
            }
        });
        web.setHorizontalScrollBarEnabled(false);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);   // canvas 游戏走硬件层更顺滑
        web.setVerticalScrollBarEnabled(false);
        root.addView(web, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        TextView exit = new TextView(this);
        exit.setText("退出");
        exit.setTextSize(10);
        exit.setTextColor(0x99FFFFFF);
        exit.setPadding((int) (10 * getResources().getDisplayMetrics().density),
                (int) (5 * getResources().getDisplayMetrics().density),
                (int) (10 * getResources().getDisplayMetrics().density),
                (int) (5 * getResources().getDisplayMetrics().density));
        exit.setBackgroundColor(0x33000000);
        exit.setOnClickListener(v -> finish());
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        ep.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.START;
        ep.leftMargin = (int) (8 * getResources().getDisplayMetrics().density);
        ep.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
        root.addView(exit, ep);

        setContentView(root);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        goFullscreen();

        web.loadUrl("file:///android_asset/game/zhuan-dao-gecao.html");
        Toast.makeText(this, "按返回键或左下角「退出」离开游戏", Toast.LENGTH_SHORT).show();
    }

    private void goFullscreen() {
        if (Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars());
                c.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
        getWindow().setStatusBarColor(Color.TRANSPARENT);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
