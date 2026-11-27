package hu.cattus.hungaricus;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.Gravity;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.graphics.Insets;
import android.graphics.Color;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.json.JSONTokener;

public final class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FrameLayout root;
    private WebView webView;
    private TextView bootStatus;
    private boolean booting = true;
    private boolean hasBootError = false;
    private Runnable timeout;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        root = new FrameLayout(this);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(251, 249, 250));
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        webView.addJavascriptInterface(new FelisBridge(), "FelisAndroid");

        bootStatus = new TextView(this);
        bootStatus.setGravity(Gravity.CENTER);
        bootStatus.setTextAlignment(TextView.TEXT_ALIGNMENT_CENTER);
        bootStatus.setTextColor(Color.rgb(60, 56, 60));
        bootStatus.setTextSize(18);
        int pad = (int) (28 * getResources().getDisplayMetrics().density);
        bootStatus.setPadding(pad, pad, pad, pad);
        bootStatus.setBackgroundColor(Color.rgb(251, 249, 250));
        bootStatus.setText("Felis 1.36\nA tananyag betöltése…");
        bootStatus.setOnClickListener(view -> loadBundledPage());

        root.addView(webView, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(bootStatus, new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    showBootError("A kezdőoldal betöltése sikertelen.\n" +
                        error.getDescription() + " (" + error.getErrorCode() + ")");
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (!booting) return;
                String probe = "(function(){var b=document.body,g=document.getElementById('topicGrid');" +
                    "var t=b?String(b.innerText||b.textContent||'').trim().length:0;" +
                    "var shown=!!(b&&b.getBoundingClientRect().height>0);" +
                    "return JSON.stringify({error:String(window.__FELIS_BOOT_ERROR||''),text:t," +
                    "topics:g?g.querySelectorAll('.topic-node').length:0,shown:shown})})()";
                view.evaluateJavascript(probe, result -> {
                    if (!booting) return;
                    try {
                        org.json.JSONObject state = (org.json.JSONObject) new JSONTokener(result).nextValue();
                        String jsError = state.optString("error", "");
                        int textLength = state.optInt("text", 0);
                        int topicCount = state.optInt("topics", 0);
                        boolean shown = state.optBoolean("shown", false);
                        if (!jsError.isEmpty()) {
                            showBootError("JavaScript-indítási hiba:\n" + jsError +
                                "\n\nFelis 1.36 — érintsd meg az újrapróbáláshoz.");
                        } else if (textLength > 0 && topicCount > 0 && shown && !hasBootError) {
                            finishBoot();
                        } else if (!hasBootError) {
                            showBootError("Az oldal betöltődött, de a tananyag felülete nem rajzolódott ki." +
                                "\nSzöveg: " + textLength + ", témakörök: " + topicCount +
                                "\n\nFelis 1.36 — érintsd meg az újrapróbáláshoz.");
                        }
                    } catch (Exception error) {
                        if (!hasBootError) {
                            showBootError("Nem sikerült ellenőrizni a tananyag felületét: " +
                                error.getClass().getSimpleName() +
                                "\n\nFelis 1.36 — érintsd meg az újrapróbáláshoz.");
                        }
                    }
                });
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage message) {
                if (booting && message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    showBootError("JavaScript-indítási hiba:\n" + message.message() +
                        "\nSor: " + message.lineNumber() +
                        "\n\nFelis 1.36 — érintsd meg az újrapróbáláshoz.");
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

    private void loadBundledPage() {
        booting = true;
        hasBootError = false;
        bootStatus.setVisibility(TextView.VISIBLE);
        bootStatus.setText("Felis 1.36\nA tananyag betöltése…");
        if (timeout != null) handler.removeCallbacks(timeout);
        timeout = () -> {
            if (booting && !hasBootError) {
                showBootError("A tananyag 8 másodperc alatt nem indult el.\n" +
                    "Érintsd meg a képernyőt az újrapróbáláshoz.\n\nFelis 1.36");
            }
        };
        handler.postDelayed(timeout, 8000);

        try (InputStream input = getAssets().open("www/index.html");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            String html = new String(output.toByteArray(), StandardCharsets.UTF_8);
            String errorHook = "<script>window.onerror=function(message,source,line,column){" +
                "var e=String(message)+' (sor '+(Number(line)||0)+')';window.__FELIS_BOOT_ERROR=e;" +
                "try{if(window.FelisAndroid&&window.FelisAndroid.jsError)window.FelisAndroid.jsError(e,Number(line)||0)}" +
                "catch(ignore){};return false};</script>";
            html = html.replace("</head>", errorHook + "</head>");
            webView.loadDataWithBaseURL(
                "https://appassets.androidplatform.net/assets/www/index.html",
                html, "text/html", "UTF-8", null);
        } catch (Exception error) {
            android.util.Log.e("Felis", "Unable to read bundled lesson page", error);
            showBootError("Az APK-ba csomagolt tananyagfájl nem olvasható.\n" +
                error.getClass().getSimpleName() + ": " + error.getMessage() +
                "\n\nFelis 1.36 — érintsd meg az újrapróbáláshoz.");
        }
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

    public final class FelisBridge {
        @JavascriptInterface
        public void jsError(String message, int line) {
            runOnUiThread(() -> showBootError(
                "JavaScript-indítási hiba:\n" + message + "\nSor: " + line +
                "\n\nFelis 1.36 — érintsd meg az újrapróbáláshoz."));
        }
    }
}
