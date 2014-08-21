package com.valtech.contactsync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;

import java.io.IOException;

public class SyncAdapter extends AbstractThreadedSyncAdapter {
  private final ApiClient apiClient;

  public SyncAdapter(Context context) {
    super(context, true);
    this.apiClient = new ApiClient();
  }

  @Override
  public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
    try {
      String refreshToken = AccountManager.get(getContext()).blockingGetAuthToken(account, "refresh_token", true);
      String accessToken = apiClient.getAccessToken(refreshToken);

    } catch (OperationCanceledException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (AuthenticatorException e) {
      e.printStackTrace();
    }
  }
}
