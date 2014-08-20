package com.valtech.contactsync;


import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class SyncAdapterService extends Service {
  private SyncAdapter syncAdapter;

  @Override
  public void onCreate() {
    syncAdapter = new SyncAdapter(this);
  }

  @Override
  public IBinder onBind(Intent intent) {
    return syncAdapter.getSyncAdapterBinder();
  }
}
