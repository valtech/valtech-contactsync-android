package com.valtech.contactsync;

import android.accounts.*;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import com.valtech.contactsync.api.ApiClient;
import com.valtech.contactsync.api.InvalidGrantException;

public class Authenticator extends AbstractAccountAuthenticator {
  private static final String TAG = Authenticator.class.getSimpleName();

  private final Context context;
  private final ApiClient apiClient;

  public Authenticator(Context context) {
    super(context);
    this.context = context;
    this.apiClient = new ApiClient(context);
  }

  @Override
  public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
    return null;
  }

  @Override
  public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType, String[] requiredFeatures, Bundle options) throws NetworkErrorException {
    return startSignIn(response);
  }

  @Override
  public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options) throws NetworkErrorException {
    return null;
  }

  @Override
  public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) throws NetworkErrorException {
    Log.i(TAG, "Fetching new access token.");
    Bundle bundle = new Bundle();

    try {
      String refreshToken = AccountManager.get(context).getPassword(account);
      String accessToken = apiClient.getAccessToken(refreshToken);
      Log.i(TAG, "Got new access token.");

      bundle.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
      bundle.putString(AccountManager.KEY_ACCOUNT_TYPE, context.getString(R.string.account_type));
      bundle.putString(AccountManager.KEY_AUTHTOKEN, accessToken);

      return bundle;
    } catch (InvalidGrantException e) {
      Log.i(TAG, "Refresh token invalid.");
      return startSignIn(response);
    } catch (Exception e) {
      Log.e(TAG, "Unknown error when fetching access token.", e);
      bundle.putInt(AccountManager.KEY_ERROR_CODE, AccountManager.ERROR_CODE_BAD_AUTHENTICATION);
      return bundle;
    }
  }

  @Override
  public String getAuthTokenLabel(String authTokenType) {
    return null;
  }

  @Override
  public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) throws NetworkErrorException {
    return null;
  }

  @Override
  public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features) throws NetworkErrorException {
    return null;
  }

  private Bundle startSignIn(AccountAuthenticatorResponse response) {
    Bundle result = new Bundle();
    Intent i = new Intent(context, SignInActivity.class);
    i.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
    result.putParcelable(AccountManager.KEY_INTENT, i);
    return result;
  }
}
