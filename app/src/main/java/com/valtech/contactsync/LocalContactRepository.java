package com.valtech.contactsync;

import android.accounts.Account;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;

import java.util.*;

import static android.provider.ContactsContract.*;
import static android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import static android.provider.ContactsContract.CommonDataKinds.StructuredName;

public class LocalContactRepository {
  private static final String TAG = LocalContactRepository.class.getSimpleName();

  // Appending this query parameter means we perform as operations as a sync adapter, not as a user.
  // http://developer.android.com/reference/android/provider/ContactsContract.html#CALLER_IS_SYNCADAPTER
  private static final Uri DATA_CONTENT_URI = Data.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();

  private final ContentResolver resolver;
  private final LocalContactReader localContactReader;

  public LocalContactRepository(Context context) {
    this.resolver = context.getContentResolver();
    this.localContactReader = new LocalContactReader(resolver);
  }

  public void syncContacts(Account account, List<ApiClient.UserInfoResponse> employees, SyncResult syncResult) {
    long groupId = ensureGroup(account);
    Map<String, LocalContactReader.LocalContact> storedContacts = localContactReader.getContacts(account);

    Set<String> activeEmails = new HashSet<>();
    for (ApiClient.UserInfoResponse employee : employees) {
      LocalContactReader.LocalContact localContact = storedContacts.get(employee.email);
      if (localContact != null) {
        updateExistingContact(localContact, employee);
        syncResult.stats.numUpdates++;
      } else {
        insertNewContact(account, groupId, employee);
        syncResult.stats.numInserts++;
      }
      syncResult.stats.numEntries++;

      activeEmails.add(employee.email);
    }

    for (LocalContactReader.LocalContact localContact : storedContacts.values()) {
      if (activeEmails.contains(localContact.sourceId)) continue;
      deleteInactiveContact(localContact);
      syncResult.stats.numDeletes++;
      syncResult.stats.numEntries++;
    }
  }

  private void updateExistingContact(LocalContactReader.LocalContact localContact, ApiClient.UserInfoResponse employee) {
    Log.i(TAG, "Updating existing contact " + employee.email);

    ArrayList<ContentProviderOperation> ops = new ArrayList<>();

    // Name
    ops.add(ContentProviderOperation.newUpdate(DATA_CONTENT_URI)
      .withSelection(
        Data.RAW_CONTACT_ID + " = ? AND " + Data.MIMETYPE + " = ?",
        new String[] { String.valueOf(localContact.rawContactId), StructuredName.CONTENT_ITEM_TYPE })
      .withValue(StructuredName.DISPLAY_NAME, employee.name)
      .build());

    // Email
    ops.add(ContentProviderOperation.newUpdate(DATA_CONTENT_URI)
      .withSelection(
        Data.RAW_CONTACT_ID + " = ? AND " + Data.MIMETYPE + " = ?",
        new String[]{String.valueOf(localContact.rawContactId), CommonDataKinds.Email.CONTENT_ITEM_TYPE})
      .withValue(CommonDataKinds.Email.DATA, employee.email)
      .withValue(CommonDataKinds.Email.TYPE, CommonDataKinds.Email.TYPE_WORK)
      .build());

    // Mobile phone
    ops.add(ContentProviderOperation.newUpdate(DATA_CONTENT_URI)
      .withSelection(
        Data.RAW_CONTACT_ID + " = ? AND " + Data.MIMETYPE + " = ? AND " + CommonDataKinds.Phone.TYPE + " = ?",
        new String[] { String.valueOf(localContact.rawContactId), CommonDataKinds.Phone.CONTENT_ITEM_TYPE, String.valueOf(CommonDataKinds.Phone.TYPE_WORK_MOBILE) })
      .withValue(CommonDataKinds.Phone.NUMBER, employee.phoneNumber)
      .build());

    // Fixed phone
    ops.add(ContentProviderOperation.newUpdate(DATA_CONTENT_URI)
      .withSelection(
        Data.RAW_CONTACT_ID + " = ? AND " + Data.MIMETYPE + " = ? AND " + CommonDataKinds.Phone.TYPE + " = ?",
        new String[] { String.valueOf(localContact.rawContactId), CommonDataKinds.Phone.CONTENT_ITEM_TYPE, String.valueOf(CommonDataKinds.Phone.TYPE_WORK) })
      .withValue(CommonDataKinds.Phone.NUMBER, employee.fixedPhoneNumber)
      .build());

    try {
      resolver.applyBatch(ContactsContract.AUTHORITY, ops);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void insertNewContact(Account account, long groupId, ApiClient.UserInfoResponse employee) {
    Log.i(TAG, "Inserting new contact " + employee.email);

    ArrayList<ContentProviderOperation> ops = new ArrayList<>();
    final int backReferenceIndex = 0;

    ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
      .withValue(RawContacts.ACCOUNT_TYPE, account.type)
      .withValue(RawContacts.ACCOUNT_NAME, account.name)
      .withValue(RawContacts.SOURCE_ID, employee.email)
      .build());

    // Name
    ops.add(ContentProviderOperation.newInsert(DATA_CONTENT_URI)
      .withValueBackReference(Data.RAW_CONTACT_ID, backReferenceIndex)
      .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
      .withValue(StructuredName.DISPLAY_NAME, employee.name)
      .build());

    // Email
    ops.add(ContentProviderOperation.newInsert(DATA_CONTENT_URI)
      .withValueBackReference(Data.RAW_CONTACT_ID, backReferenceIndex)
      .withValue(Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
      .withValue(CommonDataKinds.Email.DATA, employee.email)
      .withValue(CommonDataKinds.Email.TYPE, CommonDataKinds.Email.TYPE_WORK)
      .build());

    // Mobile phone
    ops.add(ContentProviderOperation.newInsert(DATA_CONTENT_URI)
      .withValueBackReference(Data.RAW_CONTACT_ID, backReferenceIndex)
      .withValue(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
      .withValue(CommonDataKinds.Phone.NUMBER, employee.phoneNumber)
      .withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_WORK_MOBILE)
      .build());

    // Fixed phone
    ops.add(ContentProviderOperation.newInsert(DATA_CONTENT_URI)
      .withValueBackReference(Data.RAW_CONTACT_ID, backReferenceIndex)
      .withValue(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
      .withValue(CommonDataKinds.Phone.NUMBER, employee.fixedPhoneNumber)
      .withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_WORK)
      .build());

    // It is nice if the contact is part of a group, and this makes the contact visible
    ops.add(ContentProviderOperation.newInsert(DATA_CONTENT_URI)
      .withValueBackReference(Data.RAW_CONTACT_ID, backReferenceIndex)
      .withValue(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE)
      .withValue(GroupMembership.GROUP_ROW_ID, groupId)
      .build());

    try {
      resolver.applyBatch(ContactsContract.AUTHORITY, ops);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void deleteInactiveContact(LocalContactReader.LocalContact localContact) {
    Log.i(TAG, "Deleting inactive contact " + localContact.sourceId);

    ArrayList<ContentProviderOperation> ops = new ArrayList<>();
    ops.add(ContentProviderOperation.newDelete(
      ContactsContract.RawContacts.CONTENT_URI.buildUpon()
        .appendPath(String.valueOf(localContact.rawContactId))
          // Appending this query parameter is what actually deletes the raw contact.
          // Without it, the contact would just be "hidden", treated as deleted by the user but not yet synced to the server.
          // http://developer.android.com/reference/android/provider/ContactsContract.RawContacts.html
        .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
        .build())
      .build());

    try {
      resolver.applyBatch(ContactsContract.AUTHORITY, ops);
    } catch (Exception e) {
      throw new RuntimeException(e);
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
