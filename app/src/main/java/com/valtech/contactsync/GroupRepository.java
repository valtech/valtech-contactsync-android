package com.valtech.contactsync;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.provider.ContactsContract;

import java.util.NoSuchElementException;

public class GroupRepository {
  private final ContentResolver resolver;

  public GroupRepository(ContentResolver resolver) {
    this.resolver = resolver;
  }

  public long ensureGroup(Account account, String groupTitle) {
    try {
      return getGroupId(account, groupTitle);
    } catch (NoSuchElementException e) {
      createGroup(account, groupTitle);
      return getGroupId(account, groupTitle);
    }
  }

  private long getGroupId(Account account, String groupTitle) {
    Cursor cursor = null;

    try {
      cursor = resolver.query(ContactsContract.Groups.CONTENT_URI,
        new String[]{ContactsContract.Groups._ID},
        ContactsContract.Groups.ACCOUNT_TYPE + " = ? AND " + ContactsContract.Groups.TITLE + " = ?",
        new String[]{account.type, groupTitle},
        null);

      if (cursor.moveToNext()) return cursor.getLong(0);
    } finally {
      if (cursor != null) cursor.close();
    }

    throw new NoSuchElementException();
  }

  private void createGroup(Account account, String groupTitle) {
    ContentValues values = new ContentValues();

    values.put(ContactsContract.Groups.TITLE, groupTitle);
    values.put(ContactsContract.Groups.GROUP_VISIBLE, 1);
    values.put(ContactsContract.Groups.SHOULD_SYNC, 0);

    values.put(ContactsContract.Groups.ACCOUNT_NAME, account.name);
    values.put(ContactsContract.Groups.ACCOUNT_TYPE, account.type);

    resolver.insert(ContactsContract.Groups.CONTENT_URI, values);
  }
}