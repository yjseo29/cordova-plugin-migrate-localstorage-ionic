package com.cordova.plugin.android.migrate.localstorage;

import android.content.Context;
import android.util.Log;
import android.webkit.ValueCallback;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebViewClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;

import org.apache.cordova.*;

public class MigrateLocalStorage extends CordovaPlugin {
  @Override
  public void initialize(CordovaInterface cordova, CordovaWebView webView) {
    super.initialize(cordova, webView);
    Log.d("MigrateLocalStorage", "Starting");
    this.migrateLocalStorage();
  }

  static private void runWebViewJS(Context context, final String url, final String javascript,
      final ValueCallback callback) {
    final WebView web = new WebView(context);

    WebSettings settings = web.getSettings();
    settings.setJavaScriptEnabled(true);
    settings.setDomStorageEnabled(true);
    settings.setDatabaseEnabled(true);

    String databasePath = web.getContext().getApplicationContext().getDir("database", Context.MODE_PRIVATE).getPath();
    settings.setDatabasePath(databasePath);

    web.setWebViewClient(new WebViewClient() {
      public void onExceededDatabaseQuota(String url, String databaseIdentifier, long currentQuota, long estimatedSize,
          long totalUsedQuota, WebStorage.QuotaUpdater quotaUpdater) {
        quotaUpdater.updateQuota(5 * 1024 * 1024);
      }

      public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
        Log.e("MigrateLocalStorage", error.toString());
      }

      @Override
      public void onPageFinished(WebView view, String url) {
        web.evaluateJavascript(javascript, callback);
      }

    });

    web.loadDataWithBaseURL(url, "<html></html>", "text/html", "UTF-8", "about:blank");
  }

  private void migrateLocalStorage() {
    final Context context = this.cordova.getActivity().getApplicationContext();

    final String oldJS = "(function() {"
        + "if (localStorage.length === 0) return 'empty';"
        + "var storage = {};"
        + "for (var i = 0; i < localStorage.length; ++i) {"
          + "storage[localStorage.key(i)] = localStorage.getItem(localStorage.key(i));"
        + "}"
        + "return JSON.stringify(storage);"
      + "})();";

    final String clearOldJS = "(function() {"
        + "localStorage.clear();"
        + "return 'Completed';"
      + "})();";

    final String oldUrl = "file:///android_asset/www/index.html";

    final String newUrl = preferences.getString("Scheme", "http") + "://" + preferences.getString("Hostname", "localhost");
    Log.d("MigrateLocalStorage", "New webview Url: "+newUrl);
    MigrateLocalStorage.runWebViewJS(context, oldUrl, oldJS, new ValueCallback<String>() {
      @Override
      public void onReceiveValue(String s) {
        if (s.equals("\"empty\"")) {
          Log.d("MigrateLocalStorage", "LocalStorage was empty, not migrating");
          return;
        }
        Log.d("MigrateLocalStorage", "Received localStorage with length: '" + Integer.toString(s.length()) + "'");

        String newJS = "(function() {"
            + "var storage = JSON.parse(" + s + ");"
            + "Object.keys(storage).forEach(function(key) {"
              + "if (!localStorage.getItem(key)) localStorage.setItem(key, storage[key]);"
            + "});"
            + "return 'Completed';"
          + "})();";

        MigrateLocalStorage.runWebViewJS(context, newUrl, newJS, new ValueCallback<String>() {
          @Override
          public void onReceiveValue(String s) {
            if (s.equals("\"Completed\"")) {
              MigrateLocalStorage.runWebViewJS(context, oldUrl, clearOldJS, new ValueCallback<String>() {
                @Override
                public void onReceiveValue(String s) {
                  Log.d("MigrateLocalStorage", s);
                }
              });
            } else {
              Log.e("MigrateLocalStorage", "Something went wrong: " + s);
            }
          }
        });
      }
    });
  }
}
