package hu.cattus.hungaricus;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.*;
import android.view.WindowInsets;\nimport android.view.WindowManager;
import android.graphics.Insets;
import androidx.webkit.WebViewAssetLoader;
import androidx.webkit.WebViewClientCompat;
public final class MainActivity extends Activity {
 @Override public void onCreate(Bundle b) {
 super.onCreate(b);\n getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
 WebView w=new WebView(this);
 WebSettings s=w.getSettings();
 s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setAllowFileAccess(false); s.setAllowContentAccess(false); s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
 WebViewAssetLoader loader=new WebViewAssetLoader.Builder().addPathHandler("/assets/",new WebViewAssetLoader.AssetsPathHandler(this)).build();
 w.setWebViewClient(new WebViewClientCompat(){
 @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){return loader.shouldInterceptRequest(r.getUrl());}
 @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
 });
 setContentView(w);
 if(android.os.Build.VERSION.SDK_INT>=35) w.setOnApplyWindowInsetsListener((v,i)->{Insets a=i.getInsets(WindowInsets.Type.systemBars());Insets ime=i.getInsets(WindowInsets.Type.ime());v.setPadding(a.left,a.top,a.right,Math.max(a.bottom,ime.bottom));return i;});
 w.loadUrl("https://appassets.androidplatform.net/assets/www/index.html");
 }
}