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
  private final LocalContactRepository contactRepository;

  public SyncAdapter(Context context) {
    super(context, true);
    this.apiClient = new ApiClient();
    this.contactRepository = new LocalContactRepository(context);
  }

  @Override
  public void onPerformSync(Account account, Bundle extras, String authority, ContentProviderClient provider, SyncResult syncResult) {
    try {
      Log.i(TAG, "Starting contact sync.");

      AccountManager accountManager = AccountManager.get(getContext());

      String accessToken = accountManager.blockingGetAuthToken(account, "access_token", true);
      if (accessToken == null) throw new NoAccessTokenException();
      accountManager.invalidateAuthToken(account.type, accessToken); // only use access token once

      Log.i(TAG, "Fetching remote contacts from resource server.");
      List<ApiClient.UserInfoResponse> remoteContacts = apiClient.getUserInfoResources(accessToken);
      Log.i(TAG, String.format("Got %d remote contacts.", remoteContacts.size()));

      List<ApiClient.UserInfoResponse> filteredRemoteContacts = filter(remoteContacts);

      contactRepository.syncContacts(account, filteredRemoteContacts, syncResult);

      Log.i(TAG, "Sync complete: " + syncResult.stats + ".");
    } catch (NoAccessTokenException e) {
      Log.i(TAG, "No access token.");
      syncResult.stats.numAuthExceptions = 1;
    } catch (ApiClient.OAuthException e) {
      Log.e(TAG, "OAuth exception during sync.", e);
      syncResult.stats.numAuthExceptions = 1;
    } catch (Exception e) {
      Log.e(TAG, "Unknown error during sync.", e);
      syncResult.databaseError = true;
    }
  }

  private List<ApiClient.UserInfoResponse> filter(List<ApiClient.UserInfoResponse> contacts) {
    List<ApiClient.UserInfoResponse> list = new ArrayList<>();

    for (ApiClient.UserInfoResponse c : contacts) {
      if (Settings.isSyncEnabled(getContext(), c.countryCode)) list.add(c);
      
      // enable row below to limit the accounts to sync (for development)
      //if (list.size() > 20) break;
    }

    return list;
  }

  public static class NoAccessTokenException extends RuntimeException {
    public NoAccessTokenException() {
      super("No access token.");
    }
  }
}
