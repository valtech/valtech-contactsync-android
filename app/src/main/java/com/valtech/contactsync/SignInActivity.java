package com.valtech.contactsync;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class SignInActivity extends AccountAuthenticatorActivity {
  public static final String ACCOUNT_TYPE = "com.valtech.contactsync.account";
  private ApiClient apiClient = new ApiClient();

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    WebView webview = new WebView(this);
    WebViewClient client = new IdpWebViewClient();
    client.onPageFinished(webview, "vidp://callback");
    webview.setWebViewClient(client);
    setContentView(webview);

    webview.loadUrl(apiClient.getAuthorizeUrl());
  }

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

      new SignInTask().execute(code);
    }
  }

  private class SignInTask extends AsyncTask<String, Void, Bundle> {
    @Override
    protected Bundle doInBackground(String... params) {
      ApiClient.TokenResponse tokenResponse = apiClient.getAccessTokenAndRefreshToken(params[0]);
      ApiClient.UserInfoResponse userInfoResponse = apiClient.getUserInfoMeResource(tokenResponse.accessToken);

      AccountManager accountManager = AccountManager.get(SignInActivity.this);
      Account account = new Account(userInfoResponse.email, ACCOUNT_TYPE);
      accountManager.addAccountExplicitly(account, tokenResponse.refreshToken, null);

      Bundle result = new Bundle();
      result.putString(AccountManager.KEY_ACCOUNT_NAME, userInfoResponse.email);
      result.putString(AccountManager.KEY_ACCOUNT_TYPE, ACCOUNT_TYPE);
      //result.putString(AccountManager.KEY_AUTHTOKEN, tokenResponse.refreshToken);
      return result;
    }

    @Override
    protected void onPostExecute(Bundle result) {
      SignInActivity.this.setAccountAuthenticatorResult(result);
      SignInActivity.this.finish();
    }
  }
}
