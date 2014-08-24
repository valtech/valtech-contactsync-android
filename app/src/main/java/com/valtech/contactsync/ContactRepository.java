package com.valtech.contactsync;

import android.accounts.Account;
import android.content.*;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.util.Log;

import java.util.*;

import static android.provider.ContactsContract.CommonDataKinds;
import static android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import static android.provider.ContactsContract.CommonDataKinds.StructuredName;
import static android.provider.ContactsContract.Data;
import static android.provider.ContactsContract.RawContacts;

public class ContactRepository {
  private static final String TAG = ContactRepository.class.getSimpleName();

  private final ContentResolver resolver;
  private final ContactReader contactReader;

  public ContactRepository(Context context) {
    this.resolver = context.getContentResolver();
    this.contactReader = new ContactReader(resolver);
  }

  public void syncContacts(Account account, List<ApiClient.UserInfoResponse> employees, SyncResult syncResult) {
    long groupId = ensureGroup(account);
    Map<String, ContactReader.Contact> storedContacts = contactReader.getStoredContacts(account);

    Set<String> activeEmails = new HashSet<>();
    for (ApiClient.UserInfoResponse employee : employees) {
      if (storedContacts.containsKey(employee.email)) {
        Log.i(TAG, "Updating existing contact " + employee.email);
        updateExistingContact(account, employee);
        syncResult.stats.numUpdates++;
      } else {
        Log.i(TAG, "Inserting new contact " + employee.email);
        insertNewContact(account, groupId, employee);
        syncResult.stats.numInserts++;
      }
      syncResult.stats.numEntries++;

      activeEmails.add(employee.email);
    }

    for (ContactReader.Contact storedContact : storedContacts.values()) {
      if (activeEmails.contains(storedContact.sourceId)) continue;

      Log.i(TAG, "Deleting inactive contact " + storedContact.sourceId);
      deleteInactiveContact(account, storedContact);
    }
  }

  private void updateExistingContact(Account account, ApiClient.UserInfoResponse employee) {
    // TODO:
  }

  private void insertNewContact(Account account, long groupId, ApiClient.UserInfoResponse employee) {
    ArrayList<ContentProviderOperation> ops = new ArrayList<>();
    final int backReferenceIndex = 0;

    ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
      .withValue(RawContacts.ACCOUNT_TYPE, account.type)
      .withValue(RawContacts.ACCOUNT_NAME, account.name)
      .withValue(RawContacts.SOURCE_ID, employee.email)
      .build());

    // Name
    ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
      .withValueBackReference(Data.RAW_CONTACT_ID, backReferenceIndex)
      .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
      .withValue(StructuredName.DISPLAY_NAME, employee.name)
      .build());

    // Email
    ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
      .withValueBackReference(Data.RAW_CONTACT_ID, backReferenceIndex)
      .withValue(Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
      .withValue(CommonDataKinds.Email.DATA, employee.email)
      .withValue(CommonDataKinds.Email.TYPE, CommonDataKinds.Email.TYPE_WORK)
      .build());

    // Mobile phone
    ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
      .withValueBackReference(Data.RAW_CONTACT_ID, backReferenceIndex)
      .withValue(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
      .withValue(CommonDataKinds.Phone.NUMBER, employee.phoneNumber)
      .withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_WORK_MOBILE)
      .build());

    // Fixed phone
    ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
      .withValueBackReference(Data.RAW_CONTACT_ID, backReferenceIndex)
      .withValue(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
      .withValue(CommonDataKinds.Phone.NUMBER, employee.fixedPhoneNumber)
      .withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_WORK)
      .build());

    // It is nice if the contact is part of a group, and this makes the contact visible
    ops.add(ContentProviderOperation.newInsert(Data.CONTENT_URI)
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

  private void deleteInactiveContact(Account account, ContactReader.Contact storedContact) {
    // TODO:
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
