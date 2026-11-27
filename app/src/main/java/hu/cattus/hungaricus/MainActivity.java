package hu.cattus.hungaricus;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.graphics.Insets;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public final class MainActivity extends Activity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        WebView webView = new WebView(this);
        webView.setBackgroundColor(android.graphics.Color.WHITE);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        setContentView(webView);

        if (android.os.Build.VERSION.SDK_INT >= 35) {
            webView.setOnApplyWindowInsetsListener((view, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                Insets ime = insets.getInsets(WindowInsets.Type.ime());
                view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
                return insets;
            });
        }

        try (InputStream input = getAssets().open("www/index.html");
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            String html = new String(output.toByteArray(), StandardCharsets.UTF_8);
            webView.loadDataWithBaseURL(
                "https://appassets.androidplatform.net/assets/www/index.html",
                html,
                "text/html",
                "UTF-8",
                null
            );
        } catch (Exception error) {
            TextView message = new TextView(this);
            message.setText("A Felis tananyaga nem olvasható be. Kérlek, telepítsd újra az alkalmazást.");
            message.setTextSize(18);
            message.setPadding(32, 32, 32, 32);
            setContentView(message);
            android.util.Log.e("Felis", "Unable to read bundled lesson page", error);
        }
    }
}
