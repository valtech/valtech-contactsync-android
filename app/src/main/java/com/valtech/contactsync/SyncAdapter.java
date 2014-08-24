package com.valtech.contactsync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class SyncAdapter extends AbstractThreadedSyncAdapter {
  private static final String TAG = SyncAdapter.class.getSimpleName();
  private final ApiClient apiClient;
  private final ContactRepository contactRepository;

  public SyncAdapter(Context context) {
    super(context, true);
    this.apiClient = new ApiClient();
    this.contactRepository = new ContactRepository(context);
  }

  @Override
  public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
    try {
      Log.i(TAG, "Starting contact sync.");
      String refreshToken = AccountManager.get(getContext()).blockingGetAuthToken(account, "refresh_token", true);
      Log.d(TAG, "Got the user refresh token.");
      String accessToken = apiClient.getAccessToken(refreshToken);
      Log.i(TAG, "Received new access token from refresh token.");

      Log.i(TAG, "Fetching employees from resource server.");
      List<ApiClient.UserInfoResponse> employees = apiClient.getUserInfoResources(accessToken);
      Log.i(TAG, String.format("Got %d employees.", employees.size()));

      // Temporary when developing
      List<ApiClient.UserInfoResponse> seEmployees = new ArrayList<>();
      for (ApiClient.UserInfoResponse employee : employees) {
        if ("se".equals(employee.countryCode)) seEmployees.add(employee);
        if (seEmployees.size() >= 10) break;
      }

      contactRepository.syncContacts(account, seEmployees, syncResult);

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
