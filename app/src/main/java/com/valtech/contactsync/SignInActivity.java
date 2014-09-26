package com.valtech.contactsync;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import com.valtech.contactsync.api.ApiClient;
import com.valtech.contactsync.api.OAuthException;
import com.valtech.contactsync.api.UserInfoResponse;
import com.valtech.contactsync.setting.Settings;

public class SignInActivity extends AccountAuthenticatorActivity {
  private static final String TAG = SignInActivity.class.getSimpleName();

  private ApiClient apiClient;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    apiClient = new ApiClient(this);
    Intent intent = getIntent();

    if (intent != null && intent.getData() != null && getString(R.string.app_scheme).equals(intent.getData().getScheme())) {
      Log.i(TAG, "OAuth callback received.");
      String code = getIntent().getData().getQueryParameter("code");
      new SignInTask().execute(code);
    } else {
      Log.i(TAG, "Launching browser to start OAuth flow.");
      Intent browserIntent = new Intent(Intent.ACTION_VIEW, apiClient.getAuthorizeUrl());
      startActivity(browserIntent);
      finish();
    }
  }

  private class SignInTask extends AsyncTask<String, Void, Bundle> {
    private ProgressDialog progressDialog;

    @Override
    protected void onPreExecute() {
      progressDialog = new ProgressDialog(SignInActivity.this);
      progressDialog.setIndeterminate(true);
      progressDialog.setCanceledOnTouchOutside(false);
      progressDialog.setMessage(getString(R.string.finalizing_sign_in));
      progressDialog.show();
    }

    @Override
    protected Bundle doInBackground(String... params) {
      try {
        ApiClient.TokenResponse tokenResponse = apiClient.getAccessTokenAndRefreshToken(params[0]);
        UserInfoResponse userInfoResponse = apiClient.getUserInfoMeResource(tokenResponse.accessToken);

        Log.i(TAG, "Signing in as user " + userInfoResponse.email + " in country " + userInfoResponse.countryCode + ".");

        AccountManager accountManager = AccountManager.get(SignInActivity.this);
        Account account = new Account(userInfoResponse.email, getString(R.string.account_type));
        boolean added = accountManager.addAccountExplicitly(account, tokenResponse.refreshToken, null);

        if (added) {
          Log.i(TAG, "Added account " + userInfoResponse.email + ".");
          Settings.setSyncEnabled(SignInActivity.this, userInfoResponse.countryCode, true);
          Log.i(TAG, "Enabled sync for " + userInfoResponse.countryCode + ".");
        } else {
          Log.i(TAG, "Updated refresh token for account " + userInfoResponse.email + ".");
          accountManager.setPassword(account, tokenResponse.refreshToken);

          // need to test getting an access token from the new refresh token to clear the
          // "sign in error" notification
          try {
            String accessToken = accountManager.blockingGetAuthToken(account, "access_token", true);
            accountManager.invalidateAuthToken(account.type, accessToken);
          } catch (Exception e) {
            Log.e(TAG, "Could not get access token from new refresh token.", e);
          }
        }

        ContentResolver.setIsSyncable(account, ContactsContract.AUTHORITY, 1);
        ContentResolver.setSyncAutomatically(account, ContactsContract.AUTHORITY, true);

        Bundle result = new Bundle();
        result.putString(AccountManager.KEY_ACCOUNT_NAME, userInfoResponse.email);
        result.putString(AccountManager.KEY_ACCOUNT_TYPE, getString(R.string.account_type));
        return result;
      } catch (OAuthException e) {
        Log.e(TAG, "OAuth exception during sign-in", e);
        Bundle result = new Bundle();
        result.putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_INVALID_RESPONSE);
        return result;
      }
    }

    @Override
    protected void onPostExecute(final Bundle result) {
      progressDialog.dismiss();

      SignInActivity.this.setAccountAuthenticatorResult(result);

      Intent intent = new Intent(SignInActivity.this, SignInCompleteActivity.class);
      intent.putExtra("error", result.containsKey(AccountManager.KEY_ERROR_CODE));
      intent.putExtra("email", result.getString(AccountManager.KEY_ACCOUNT_NAME));
      startActivity(intent);
      SignInActivity.this.finish();
    }
  }
}
