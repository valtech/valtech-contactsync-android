package com.valtech.contactsync;

import android.accounts.Account;
import android.content.*;
import android.database.Cursor;
import android.provider.ContactsContract;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import static android.provider.ContactsContract.CommonDataKinds.StructuredName;
import static android.provider.ContactsContract.Data;
import static android.provider.ContactsContract.RawContacts;

public class ContactRepository {
  private final Context context;
  private final ContentResolver resolver;

  public ContactRepository(Context context) {
    this.context = context;
    this.resolver = context.getContentResolver();
  }

  public void syncContacts(Account account, List<ApiClient.UserInfoResponse> employees, SyncResult syncResult) {
    long groupId = ensureGroup(account);

    for (ApiClient.UserInfoResponse employee : employees) {
      ArrayList<ContentProviderOperation> ops = new ArrayList<>();
      final int backReferenceIndex = 0;

      ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
        .withValue(RawContacts.ACCOUNT_TYPE, account.type)
        .withValue(RawContacts.ACCOUNT_NAME, account.name)
        .withValue(RawContacts.SOURCE_ID, employee.email)
        .build());

      ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
        .withValueBackReference(Data.RAW_CONTACT_ID, backReferenceIndex)
        .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
        .withValue(StructuredName.DISPLAY_NAME, employee.name)
        .build());

      ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
        .withValueBackReference(Data.RAW_CONTACT_ID, backReferenceIndex)
        .withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
        .withValue(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID, groupId)
        .build());

      try {
        resolver.applyBatch(ContactsContract.AUTHORITY, ops);
        syncResult.stats.numInserts++;
        syncResult.stats.numEntries++;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }
  }

  private long ensureGroup(Account account) {
    try {
      return getGroupId(account);
    } catch (NoSuchElementException e) {
      createGroup(account);
      return getGroupId(account);
    }
  }

  private long getGroupId(Account account) {
    Cursor cursor = null;
    try {
      cursor = resolver.query(ContactsContract.Groups.CONTENT_URI, new String[] { ContactsContract.Groups._ID }, ContactsContract.Groups.ACCOUNT_TYPE + " = ?", new String[] { account.type }, null);
      if (cursor.moveToNext()) return cursor.getLong(0);
    } finally {
      if (cursor != null) cursor.close();
    }
    throw new NoSuchElementException();
  }

  private void createGroup(Account account) {
    ContentValues values = new ContentValues();
    values.put(ContactsContract.Groups.TITLE, "Valtech ID");
    values.put(ContactsContract.Groups.GROUP_VISIBLE, 1);
    values.put(ContactsContract.Groups.SHOULD_SYNC, 0);

    values.put(ContactsContract.Groups.ACCOUNT_NAME, account.name);
    values.put(ContactsContract.Groups.ACCOUNT_TYPE, account.type);

    resolver.insert(ContactsContract.Groups.CONTENT_URI, values);
  }
}
