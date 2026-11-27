package hu.cattus.hungaricus;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.net.Uri;
import android.webkit.WebResourceResponse;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;
import androidx.webkit.WebResourceErrorCompat;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.graphics.Insets;
import android.graphics.Color;
import android.widget.FrameLayout;
import android.widget.TextView;
import org.json.JSONTokener;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private WebView webView;
    private WebViewAssetLoader assetLoader;
    private TextView bootStatus;
    private volatile String lastAssetStatus = "az index.html kérés még nem érkezett meg";
    private String lastPageUrl = "még nem érkezett oldalbetöltési visszajelzés";
    private String lastProbe = "még nem futott DOM-ellenőrzés";
    private boolean booting = true;
    private boolean hasBootError = false;
    private Runnable timeout;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        root = new FrameLayout(this);
        webView = new WebView(this);
        assetLoader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
            .build();
        webView.setBackgroundColor(Color.rgb(251, 249, 250));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        bootStatus = new TextView(this);
        bootStatus.setGravity(Gravity.CENTER);
        bootStatus.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        bootStatus.setTextColor(Color.rgb(60, 56, 60));
        bootStatus.setTextSize(18);
        int pad = (int) (28 * getResources().getDisplayMetrics().density);
        bootStatus.setPadding(pad, pad, pad, pad);
        bootStatus.setBackgroundColor(Color.rgb(251, 249, 250));
        bootStatus.setText("Felis 1.43\nA tananyag betöltése…");
        bootStatus.setOnClickListener(view -> loadBundledPage());

        root.addView(webView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(bootStatus, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        webView.setWebViewClient(new WebViewClientCompat() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return interceptAsset(request.getUrl());
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return interceptAsset(Uri.parse(url));
            }
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceErrorCompat error) {
                if (request.isForMainFrame()) {
                    showBootError("A kezdőoldal betöltése sikertelen.\n" +
                        error.getDescription() + " (" + error.getErrorCode() + ")");
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!booting || url == null || url.startsWith("about:blank")) return;
                lastPageUrl = url;
                verifyRenderedPage(view, url);
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                if (booting && message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    showBootError("JavaScript-indítási hiba:\n" + message.message() +
                        "\nSor: " + message.lineNumber() +
                        "\n\nFelis 1.43 — érintsd meg az újrapróbáláshoz.");
                }
                return true;
            }
        });

        if (android.os.Build.VERSION.SDK_INT >= 35) {
            webView.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                Insets ime = insets.getInsets(WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
                return insets;
            });
        }

        loadBundledPage();
    }

    private WebResourceResponse interceptAsset(Uri uri) {
        WebResourceResponse response = assetLoader.shouldInterceptRequest(uri);
        String path = uri.getPath();
        if (path != null && path.endsWith("/index.html")) {
            lastAssetStatus = response == null ? "index.html: az APK asset-loader nem talált fájlt"
                : "index.html: kiszolgálva, MIME " + response.getMimeType();
            android.util.Log.i("Felis", lastAssetStatus + " — " + uri);
        }
        return response;
    }

    private void verifyRenderedPage(WebView view, String source) {
        if (!booting) return;
        String probe = "(function(){var b=document.body,g=document.getElementById('topicGrid');" +
            "var t=b?String(b.innerText||b.textContent||'').trim().length:0;" +
            "var shown=!!(b&&b.getBoundingClientRect().height>0);" +
            "return {text:t,topics:g?g.querySelectorAll('.topic-node').length:0,shown:shown," +
            "ready:document.readyState,html:document.documentElement?document.documentElement.outerHTML.length:0," +
            "title:document.title}})()";
        view.evaluateJavascript(probe, result -> {
            if (!booting) return;
            try {
                Object parsed = new JSONTokener(result).nextValue();
                org.json.JSONObject state;
                if (parsed instanceof org.json.JSONObject) {
                    state = (org.json.JSONObject) parsed;
                } else if (parsed instanceof String) {
                    state = new org.json.JSONObject((String) parsed);
                } else {
                    throw new IllegalStateException("Unexpected WebView probe result");
                }
                int textLength = state.optInt("text", 0);
                int topicCount = state.optInt("topics", 0);
                boolean shown = state.optBoolean("shown", false);
                lastProbe = "readyState: " + state.optString("ready", "?") +
                    ", HTML-karakter: " + state.optInt("html", 0) +
                    ", cím: " + state.optString("title", "") +
                    ", szöveg: " + textLength + ", témakör: " + topicCount + ", látható: " + shown;
                if (textLength > 0 && topicCount > 0 && shown && !hasBootError) {
                    finishBoot();
                } else if (!hasBootError) {
                    scheduleRenderCheck(view, source);
                }
            } catch (Exception error) {
                if (!hasBootError) scheduleRenderCheck(view, source);
            }
        });
    }

    private void scheduleRenderCheck(WebView view, String source) {
        if (!booting || hasBootError) return;
        handler.postDelayed(() -> verifyRenderedPage(view, source), 250);
    }

    private void loadBundledPage() {
        booting = true;
        hasBootError = false;
        bootStatus.setVisibility(TextView.VISIBLE);
        bootStatus.setText("Felis 1.43\nA tananyag betöltése…");
        if (timeout != null) handler.removeCallbacks(timeout);
        timeout = () -> {
            if (booting && !hasBootError) {
                showBootError("A tananyag 8 másodperc alatt nem indult el.\n" +
                    "Érintsd meg a képernyőt az újrapróbáláshoz.\n\n" +
                    "Oldal: " + lastPageUrl + "\nAsset: " + lastAssetStatus +
                    "\nEllenőrzés: " + lastProbe + "\n\nFelis 1.43");
            }
        };
        handler.postDelayed(timeout, 8000);

        lastPageUrl = "a WebView nem jelezte az oldal betöltését";
        lastAssetStatus = "az index.html kérése nem érkezett meg";
        lastProbe = "még nem futott DOM-ellenőrzés";
        webView.loadUrl("https://appassets.androidplatform.net/assets/www/index.html");
    }

    private void finishBoot() {
        if (hasBootError) return;
        booting = false;
        if (timeout != null) handler.removeCallbacks(timeout);
        bootStatus.setVisibility(TextView.GONE);
    }

    private void showBootError(String message) {
        if (!booting) return;
        hasBootError = true;
        bootStatus.setText(message);
        bootStatus.setVisibility(TextView.VISIBLE);
        android.util.Log.e("Felis", message);
    }

}