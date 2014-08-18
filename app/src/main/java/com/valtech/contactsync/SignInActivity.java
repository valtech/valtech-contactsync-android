package com.valtech.contactsync;

import android.accounts.AccountAuthenticatorActivity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import com.google.gson.Gson;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SignInActivity extends AccountAuthenticatorActivity {
  private static final Gson GSON = new Gson();

  private class IdpWebViewClient extends WebViewClient {
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
      return url.startsWith("vidp://");
    }

    @Override
    public void onPageStarted(WebView view, String url, Bitmap favicon) {
      if (!url.startsWith("vidp://")) {
        super.onPageStarted(view, url, favicon);
        return;
      }

      Uri uri = Uri.parse(url);
      final String code = uri.getQueryParameter("code");

      AsyncTask.SERIAL_EXECUTOR.execute(new Runnable() {
        @Override
        public void run() {
          exchangeCodeForToken(code);
        }
      });

    }
  }

  private void exchangeCodeForToken(String code) {
    HttpClient httpClient = new DefaultHttpClient();
    HttpPost httpPost = new HttpPost("https://stage-id.valtech.com/oauth2/token");

    try {
      List<NameValuePair> nameValuePairs = new ArrayList<>(2);
      nameValuePairs.add(new BasicNameValuePair("client_id", "valtech.contactsync.android"));
      nameValuePairs.add(new BasicNameValuePair("client_secret", "MGIxZmIwMDYtZTVlOS00ZDdiLTgxYjMtMjg2MzdhMTQwNGVh"));
      nameValuePairs.add(new BasicNameValuePair("grant_type", "authorization_code"));
      nameValuePairs.add(new BasicNameValuePair("code", code));

      httpPost.setEntity(new UrlEncodedFormEntity(nameValuePairs));


      HttpResponse response = httpClient.execute(httpPost);
      String json = EntityUtils.toString(response.getEntity());
      TokenResponse tokenResponse = GSON.fromJson(json, TokenResponse.class);

      System.out.println("Access token: " + tokenResponse.access_token);
      System.out.println("Refresh token: " + tokenResponse.refresh_token);

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    WebView webview = new WebView(this);
    WebViewClient client = new IdpWebViewClient();
    client.onPageFinished(webview, "vidp://callback");
    webview.setWebViewClient(client);
    setContentView(webview);

    webview.loadUrl("https://stage-id.valtech.com/oauth2/authorize?response_type=code&client_id=valtech.contactsync.android&scope=vidp:get_users%20vidp:offline");
  }

  public static class TokenResponse {
    public String access_token;
    public String refresh_token;
  }
}
