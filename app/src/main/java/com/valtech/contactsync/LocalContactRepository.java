package com.valtech.contactsync;

import android.accounts.Account;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;
import com.valtech.contactsync.api.UserInfoResponse;

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
  private final String groupPrefix;

  public LocalContactRepository(Context context) {
    this.resolver = context.getContentResolver();
    this.groupPrefix = context.getString(R.string.group_prefix);
    this.localContactReader = new LocalContactReader(resolver);
  }

  public void syncContacts(Account account, List<UserInfoResponse> remoteContacts, SyncResult syncResult) {
    Map<String, LocalContactReader.LocalContact> storedContacts = localContactReader.getContacts(account);

    Set<String> activeEmails = new HashSet<>();
    for (UserInfoResponse remoteContact : remoteContacts) {
      LocalContactReader.LocalContact localContact = storedContacts.get(remoteContact.email);
      if (localContact != null) {
        updateExistingContact(localContact, remoteContact);
        syncResult.stats.numUpdates++;
      } else {
        long groupId = ensureGroup(account, groupPrefix + " " + remoteContact.countryCode.toUpperCase());
        insertNewContact(account, groupId, remoteContact);
        syncResult.stats.numInserts++;
      }
      syncResult.stats.numEntries++;

      activeEmails.add(remoteContact.email);
    }

    for (LocalContactReader.LocalContact localContact : storedContacts.values()) {
      if (activeEmails.contains(localContact.sourceId)) continue;
      deleteInactiveContact(localContact);
      syncResult.stats.numDeletes++;
      syncResult.stats.numEntries++;
    }
  }

  private void updateExistingContact(LocalContactReader.LocalContact localContact, UserInfoResponse remoteContact) {
    Log.i(TAG, "Updating existing contact " + remoteContact.email + ".");

    ArrayList<ContentProviderOperation> ops = new ArrayList<>();

    // Email, can never be missing, always update
    ops.add(ContentProviderOperation.newUpdate(DATA_CONTENT_URI)
      .withSelection(
        Data.RAW_CONTACT_ID + " = ? AND " + Data.MIMETYPE + " = ?",
        new String[]{String.valueOf(localContact.rawContactId), CommonDataKinds.Email.CONTENT_ITEM_TYPE})
      .withValue(CommonDataKinds.Email.DATA, remoteContact.email)
      .withValue(CommonDataKinds.Email.TYPE, CommonDataKinds.Email.TYPE_WORK)
      .build());

    syncName(localContact, remoteContact, ops);
    syncMobilePhoneNumber(localContact, remoteContact, ops);
    syncFixedPhoneNumber(localContact, remoteContact, ops);

    try {
      resolver.applyBatch(ContactsContract.AUTHORITY, ops);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void syncName(LocalContactReader.LocalContact localContact, UserInfoResponse remoteContact, ArrayList<ContentProviderOperation> ops) {
    if (nullOrEmpty(localContact.displayName) && !nullOrEmpty(remoteContact.name)) {
      // missing on local contact, insert it
      ops.add(buildDisplayNameInsert(remoteContact.name).withValue(Data.RAW_CONTACT_ID, localContact.rawContactId).build());
    } else if (!nullOrEmpty(localContact.displayName) && nullOrEmpty(remoteContact.name)) {
      // exists on local contact, but not on remote - delete it on local
      ops.add(ContentProviderOperation.newDelete(DATA_CONTENT_URI)
        .withSelection(
          Data.RAW_CONTACT_ID + " = ? AND " + Data.MIMETYPE + " = ?",
          new String[]{String.valueOf(localContact.rawContactId), StructuredName.CONTENT_ITEM_TYPE})
        .build());
    } else if (!nullOrEmpty(localContact.displayName) && !nullOrEmpty(remoteContact.name)) {
      // exists on both local and remote contact, update it
      ops.add(ContentProviderOperation.newUpdate(DATA_CONTENT_URI)
        .withSelection(
          Data.RAW_CONTACT_ID + " = ? AND " + Data.MIMETYPE + " = ?",
          new String[]{String.valueOf(localContact.rawContactId), StructuredName.CONTENT_ITEM_TYPE})
        .withValue(StructuredName.DISPLAY_NAME, remoteContact.name)
        .build());
    }
  }

  private void syncMobilePhoneNumber(LocalContactReader.LocalContact localContact, UserInfoResponse remoteContact, ArrayList<ContentProviderOperation> ops) {
    if (nullOrEmpty(localContact.phoneNumber) && !nullOrEmpty(remoteContact.phoneNumber)) {
      // missing on local contact, insert it
      ops.add(buildPhoneNumberInsert(remoteContact.phoneNumber).withValue(Data.RAW_CONTACT_ID, localContact.rawContactId).build());
    } else if (!nullOrEmpty(localContact.phoneNumber) && nullOrEmpty(remoteContact.phoneNumber)) {
      // exists on local contact, but not on remote - delete it on local
      ops.add(ContentProviderOperation.newDelete(DATA_CONTENT_URI)
        .withSelection(
          Data.RAW_CONTACT_ID + " = ? AND " + Data.MIMETYPE + " = ? AND " + CommonDataKinds.Phone.TYPE + " = ?",
          new String[]{String.valueOf(localContact.rawContactId), CommonDataKinds.Phone.CONTENT_ITEM_TYPE, String.valueOf(CommonDataKinds.Phone.TYPE_WORK_MOBILE)})
        .build());
    } else if (!nullOrEmpty(localContact.phoneNumber) && !nullOrEmpty(remoteContact.phoneNumber)) {
      // exists on both local and remote contact, update it
      ops.add(ContentProviderOperation.newUpdate(DATA_CONTENT_URI)
        .withSelection(
          Data.RAW_CONTACT_ID + " = ? AND " + Data.MIMETYPE + " = ? AND " + CommonDataKinds.Phone.TYPE + " = ?",
          new String[]{String.valueOf(localContact.rawContactId), CommonDataKinds.Phone.CONTENT_ITEM_TYPE, String.valueOf(CommonDataKinds.Phone.TYPE_WORK_MOBILE)})
        .withValue(CommonDataKinds.Phone.NUMBER, remoteContact.phoneNumber)
        .build());
    }
  }

  private void syncFixedPhoneNumber(LocalContactReader.LocalContact localContact, UserInfoResponse remoteContact, ArrayList<ContentProviderOperation> ops) {
    if (nullOrEmpty(localContact.fixedPhoneNumber) && !nullOrEmpty(remoteContact.fixedPhoneNumber)) {
      // missing on local contact, insert it
      ops.add(buildFixedPhoneNumberInsert(remoteContact.fixedPhoneNumber).withValue(Data.RAW_CONTACT_ID, localContact.rawContactId).build());
    } else if (!nullOrEmpty(localContact.fixedPhoneNumber) && nullOrEmpty(remoteContact.fixedPhoneNumber)) {
      // exists on local contact, but not on remote - delete it on local
      ops.add(ContentProviderOperation.newDelete(DATA_CONTENT_URI)
        .withSelection(
          Data.RAW_CONTACT_ID + " = ? AND " + Data.MIMETYPE + " = ? AND " + CommonDataKinds.Phone.TYPE + " = ?",
          new String[]{String.valueOf(localContact.rawContactId), CommonDataKinds.Phone.CONTENT_ITEM_TYPE, String.valueOf(CommonDataKinds.Phone.TYPE_WORK)})
        .build());
    } else if (!nullOrEmpty(localContact.fixedPhoneNumber) && !nullOrEmpty(remoteContact.fixedPhoneNumber)) {
      // exists on both local and remote contact, update it
      ops.add(ContentProviderOperation.newUpdate(DATA_CONTENT_URI)
        .withSelection(
          Data.RAW_CONTACT_ID + " = ? AND " + Data.MIMETYPE + " = ? AND " + CommonDataKinds.Phone.TYPE + " = ?",
          new String[]{String.valueOf(localContact.rawContactId), CommonDataKinds.Phone.CONTENT_ITEM_TYPE, String.valueOf(CommonDataKinds.Phone.TYPE_WORK)})
        .withValue(CommonDataKinds.Phone.NUMBER, remoteContact.fixedPhoneNumber)
        .build());
    }
  }

  private void insertNewContact(Account account, long groupId, UserInfoResponse remoteContact) {
    Log.i(TAG, "Inserting new contact " + remoteContact.email + ".");

    ArrayList<ContentProviderOperation> ops = new ArrayList<>();
    final int backReferenceIndex = 0;

    ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
      .withValue(RawContacts.ACCOUNT_TYPE, account.type)
      .withValue(RawContacts.ACCOUNT_NAME, account.name)
      .withValue(RawContacts.SOURCE_ID, remoteContact.email)
      .build());

    // Email, always available from IDP
    ops.add(ContentProviderOperation.newInsert(DATA_CONTENT_URI)
      .withValueBackReference(Data.RAW_CONTACT_ID, backReferenceIndex)
      .withValue(Data.MIMETYPE, CommonDataKinds.Email.CONTENT_ITEM_TYPE)
      .withValue(CommonDataKinds.Email.DATA, remoteContact.email)
      .withValue(CommonDataKinds.Email.TYPE, CommonDataKinds.Email.TYPE_WORK)
      .build());

    // Name
    if (!nullOrEmpty(remoteContact.name)) {
      ops.add(buildDisplayNameInsert(remoteContact.name)
        .withValueBackReference(Data.RAW_CONTACT_ID, backReferenceIndex)
        .build());
    }

    // Mobile phone
    if (!nullOrEmpty(remoteContact.phoneNumber)) {
      ops.add(buildPhoneNumberInsert(remoteContact.phoneNumber)
        .withValueBackReference(Data.RAW_CONTACT_ID, backReferenceIndex)
        .build());
    }

    // Fixed phone
    if (!nullOrEmpty(remoteContact.fixedPhoneNumber)) {
      ops.add(buildFixedPhoneNumberInsert(remoteContact.fixedPhoneNumber)
        .withValueBackReference(Data.RAW_CONTACT_ID, backReferenceIndex)
        .build());
    }

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

  private ContentProviderOperation.Builder buildDisplayNameInsert(String displayName) {
    return ContentProviderOperation.newInsert(DATA_CONTENT_URI)
      .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
      .withValue(StructuredName.DISPLAY_NAME, displayName);
  }

  private ContentProviderOperation.Builder buildPhoneNumberInsert(String phoneNumber) {
    return ContentProviderOperation.newInsert(DATA_CONTENT_URI)
      .withValue(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
      .withValue(CommonDataKinds.Phone.NUMBER, phoneNumber)
      .withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_WORK_MOBILE);
  }

  private ContentProviderOperation.Builder buildFixedPhoneNumberInsert(String fixedPhoneNumber) {
    return ContentProviderOperation.newInsert(DATA_CONTENT_URI)
      .withValue(Data.MIMETYPE, CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
      .withValue(CommonDataKinds.Phone.NUMBER, fixedPhoneNumber)
      .withValue(CommonDataKinds.Phone.TYPE, CommonDataKinds.Phone.TYPE_WORK);
  }

  private boolean nullOrEmpty(String s) {
    return s == null || s.isEmpty();
  }

  private void deleteInactiveContact(LocalContactReader.LocalContact localContact) {
    Log.i(TAG, "Deleting contact " + localContact.sourceId + ".");

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

  private long ensureGroup(Account account, String groupTitle) {
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
