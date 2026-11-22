package com.swen3.paperless.mobile;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.util.Objects;

public class MainActivity extends Activity {
    private static final int PICK_PDF = 1;
    private final Uri website = Uri.parse(BuildConfig.PAPERLESS_URL);
    private WebView webView;
    private ValueCallback<Uri[]> pendingFile;

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        webView = new WebView(this);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(false);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri target = request.getUrl();
                return !Objects.equals(website.getScheme(), target.getScheme())
                        || !Objects.equals(website.getHost(), target.getHost())
                        || website.getPort() != target.getPort();
            }
        });
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                return choosePdf(callback);
            }
        });
        setContentView(webView);
        if (savedState == null || webView.restoreState(savedState) == null) {
            webView.loadUrl(website.toString());
        }
    }

    private boolean choosePdf(ValueCallback<Uri[]> callback) {
        finishChoosing(null);
        pendingFile = callback;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/pdf");
        try {
            startActivityForResult(intent, PICK_PDF);
        } catch (ActivityNotFoundException exception) {
            finishChoosing(null);
        }
        return true;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_PDF) {
            finishChoosing(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
        }
    }

    private void finishChoosing(Uri[] files) {
        if (pendingFile != null) {
            pendingFile.onReceiveValue(files);
            pendingFile = null;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        webView.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        finishChoosing(null);
        webView.destroy();
        super.onDestroy();
    }
}
