package com.valtech.contactsync;

import android.accounts.*;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class Authenticator extends AbstractAccountAuthenticator {
  private final Context context;

  public Authenticator(Context context) {
    super(context);
    this.context = context;
  }

  @Override
  public Bundle editProperties(AccountAuthenticatorResponse response, String accountType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType, String[] requiredFeatures, Bundle options) throws NetworkErrorException {
    Bundle result = new Bundle();
    Intent i = new Intent(context, SignInActivity.class);
    i.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
    result.putParcelable(AccountManager.KEY_INTENT, i);
    return result;
  }

  @Override
  public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options) throws NetworkErrorException {
    return null;
  }

  @Override
  public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) throws NetworkErrorException {
    Bundle bundle = new Bundle();
    bundle.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
    bundle.putString(AccountManager.KEY_ACCOUNT_TYPE, context.getString(R.string.account_type));
    bundle.putString(AccountManager.KEY_AUTHTOKEN, AccountManager.get(context).getPassword(account));
    return bundle;
  }

  @Override
  public String getAuthTokenLabel(String authTokenType) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType, Bundle options) throws NetworkErrorException {
    throw new UnsupportedOperationException();
  }

  @Override
  public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features) throws NetworkErrorException {
    throw new UnsupportedOperationException();
  }
}
