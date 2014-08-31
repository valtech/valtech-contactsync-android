package com.valtech.contactsync;

import android.accounts.Account;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.util.Log;
import com.valtech.contactsync.api.ApiClient;
import com.valtech.contactsync.api.BinaryResponse;
import com.valtech.contactsync.api.UserInfoResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

import static android.provider.ContactsContract.*;
import static android.provider.ContactsContract.CommonDataKinds.GroupMembership;
import static android.provider.ContactsContract.CommonDataKinds.StructuredName;

public class LocalContactRepository {
  private static final String TAG = LocalContactRepository.class.getSimpleName();

  // Appending this query parameter means we perform as operations as a sync adapter, not as a user.
  // http://developer.android.com/reference/android/provider/ContactsContract.html#CALLER_IS_SYNCADAPTER
  private static final Uri DATA_CONTENT_URI = Data.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();
  private static final Uri RAW_CONTACT_CONTENT_URI = RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build();

  private final ContentResolver resolver;
  private final LocalContactReader localContactReader;
  private final GroupRepository groupRepository;
  private final String groupTitleFormat;
  private final ApiClient apiClient;
  private int maxPhotoSize;

  public LocalContactRepository(Context context, ApiClient apiClient) {
    this.resolver = context.getContentResolver();
    this.groupTitleFormat = context.getString(R.string.group_title_format);
    this.localContactReader = new LocalContactReader(resolver);
    this.groupRepository = new GroupRepository(resolver);
    this.apiClient = apiClient;
  }

  public void syncContacts(Account account, List<UserInfoResponse> remoteContacts, SyncResult syncResult) {
    Map<String, LocalContact> storedContacts = localContactReader.getContacts(account);
    Set<String> activeEmails = new HashSet<>();

    maxPhotoSize = getMaxPhotoSize();

    for (UserInfoResponse remoteContact : remoteContacts) {
      LocalContact localContact = storedContacts.get(remoteContact.email);

      if (localContact != null) {
        boolean updated = updateExistingContact(localContact, remoteContact);
        if (updated) syncResult.stats.numUpdates++;
      } else {
        String groupTitle = String.format(groupTitleFormat, remoteContact.countryCode.toUpperCase());
        long groupId = groupRepository.ensureGroup(account, groupTitle);
        insertNewContact(account, groupId, remoteContact);
        syncResult.stats.numInserts++;
      }

      syncResult.stats.numEntries++;
      activeEmails.add(remoteContact.email);
    }

    for (LocalContact localContact : storedContacts.values()) {
      if (activeEmails.contains(localContact.sourceId)) continue;
      deleteInactiveContact(localContact);
      syncResult.stats.numDeletes++;
      syncResult.stats.numEntries++;
    }
  }

  private boolean updateExistingContact(LocalContact localContact, UserInfoResponse remoteContact) {
    Log.i(TAG, "Updating existing contact " + remoteContact.email + ".");

    ArrayList<ContentProviderOperation> ops = new ArrayList<>();

    syncName(localContact, remoteContact, ops);
    syncMobilePhoneNumber(localContact, remoteContact, ops);
    syncFixedPhoneNumber(localContact, remoteContact, ops);
    syncPhoto(localContact, remoteContact, ops);

    if (ops.size() == 0) return false;

    try {
      resolver.applyBatch(ContactsContract.AUTHORITY, ops);
      return true;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void syncName(LocalContact localContact, UserInfoResponse remoteContact, ArrayList<ContentProviderOperation> ops) {
    if (nullOrEmpty(localContact.displayName) && !nullOrEmpty(remoteContact.name)) {
      // missing on local contact, insert it
      Log.i(TAG, "Contact " + remoteContact.email + " now has a name, inserting.");
      ops.add(buildDisplayNameInsert(remoteContact.name).withValue(Data.RAW_CONTACT_ID, localContact.rawContactId).build());
    } else if (!nullOrEmpty(localContact.displayName) && nullOrEmpty(remoteContact.name)) {
      // exists on local contact, but not on remote - delete it on local
      Log.i(TAG, "Contact " + remoteContact.email + " does not have a name any longer, deleting from local contact.");
      ops.add(ContentProviderOperation.newDelete(DATA_CONTENT_URI)
        .withSelection(
          Data.RAW_CONTACT_ID + " = ? AND " + Data.MIMETYPE + " = ?",
          new String[] { localContact.rawContactId, StructuredName.CONTENT_ITEM_TYPE })
        .build());
    } else if (!nullOrEmpty(localContact.displayName) && !nullOrEmpty(remoteContact.name) && !localContact.displayName.equals(remoteContact.name)) {
      // exists on both local and remote contact but not equal, update it
      ops.add(ContentProviderOperation.newUpdate(DATA_CONTENT_URI)
        .withSelection(
          Data.RAW_CONTACT_ID + " = ? AND " + Data.MIMETYPE + " = ?",
          new String[] { localContact.rawContactId, StructuredName.CONTENT_ITEM_TYPE })
        .withValue(StructuredName.DISPLAY_NAME, remoteContact.name)
        .build());
    }
  }

  private void syncMobilePhoneNumber(LocalContact localContact, UserInfoResponse remoteContact, ArrayList<ContentProviderOperation> ops) {
    if (nullOrEmpty(localContact.phoneNumber) && !nullOrEmpty(remoteContact.phoneNumber)) {
      // missing on local contact, insert it
      Log.i(TAG, "Contact " + remoteContact.email + " now has a mobile phone number, inserting.");
      ops.add(buildPhoneNumberInsert(remoteContact.phoneNumber).withValue(Data.RAW_CONTACT_ID, localContact.rawContactId).build());
    } else if (!nullOrEmpty(localContact.phoneNumber) && nullOrEmpty(remoteContact.phoneNumber)) {
      // exists on local contact, but not on remote - delete it on local
      Log.i(TAG, "Contact " + remoteContact.email + " does not have a mobile phone number any longer, deleting from local contact.");
      ops.add(ContentProviderOperation.newDelete(DATA_CONTENT_URI)
        .withSelection(
          Data.RAW_CONTACT_ID + " = ? AND " + Data.MIMETYPE + " = ? AND " + CommonDataKinds.Phone.TYPE + " = ?",
          new String[] { localContact.rawContactId, CommonDataKinds.Phone.CONTENT_ITEM_TYPE, String.valueOf(CommonDataKinds.Phone.TYPE_WORK_MOBILE) })
        .build());
    } else if (!nullOrEmpty(localContact.phoneNumber) && !nullOrEmpty(remoteContact.phoneNumber) && !localContact.phoneNumber.equals(remoteContact.phoneNumber)) {
      // exists on both local and remote contact, update it
      ops.add(ContentProviderOperation.newUpdate(DATA_CONTENT_URI)
        .withSelection(
          Data.RAW_CONTACT_ID + " = ? AND " + Data.MIMETYPE + " = ? AND " + CommonDataKinds.Phone.TYPE + " = ?",
          new String[] { localContact.rawContactId, CommonDataKinds.Phone.CONTENT_ITEM_TYPE, String.valueOf(CommonDataKinds.Phone.TYPE_WORK_MOBILE) })
        .withValue(CommonDataKinds.Phone.NUMBER, remoteContact.phoneNumber)
        .build());
    }
  }

  private void syncFixedPhoneNumber(LocalContact localContact, UserInfoResponse remoteContact, ArrayList<ContentProviderOperation> ops) {
    if (nullOrEmpty(localContact.fixedPhoneNumber) && !nullOrEmpty(remoteContact.fixedPhoneNumber)) {
      // missing on local contact, insert it
      Log.i(TAG, "Contact " + remoteContact.email + " now has a fixed phone number, inserting.");
      ops.add(buildFixedPhoneNumberInsert(remoteContact.fixedPhoneNumber).withValue(Data.RAW_CONTACT_ID, localContact.rawContactId).build());
    } else if (!nullOrEmpty(localContact.fixedPhoneNumber) && nullOrEmpty(remoteContact.fixedPhoneNumber)) {
      // exists on local contact, but not on remote - delete it on local
      Log.i(TAG, "Contact " + remoteContact.email + " does not have a fixed phone number any longer, deleting from local contact.");
      ops.add(ContentProviderOperation.newDelete(DATA_CONTENT_URI)
        .withSelection(
          Data.RAW_CONTACT_ID + " = ? AND " + Data.MIMETYPE + " = ? AND " + CommonDataKinds.Phone.TYPE + " = ?",
          new String[] { localContact.rawContactId, CommonDataKinds.Phone.CONTENT_ITEM_TYPE, String.valueOf(CommonDataKinds.Phone.TYPE_WORK) })
        .build());
    } else if (!nullOrEmpty(localContact.fixedPhoneNumber) && !nullOrEmpty(remoteContact.fixedPhoneNumber) && !localContact.fixedPhoneNumber.equals(remoteContact.fixedPhoneNumber)) {
      // exists on both local and remote contact, update it
      ops.add(ContentProviderOperation.newUpdate(DATA_CONTENT_URI)
        .withSelection(
          Data.RAW_CONTACT_ID + " = ? AND " + Data.MIMETYPE + " = ? AND " + CommonDataKinds.Phone.TYPE + " = ?",
          new String[] { localContact.rawContactId, CommonDataKinds.Phone.CONTENT_ITEM_TYPE, String.valueOf(CommonDataKinds.Phone.TYPE_WORK) })
        .withValue(CommonDataKinds.Phone.NUMBER, remoteContact.fixedPhoneNumber)
        .build());
    }
  }

  private void syncPhoto(LocalContact localContact, UserInfoResponse remoteContact, ArrayList<ContentProviderOperation> ops) {
    try {
      BinaryResponse response = apiClient.downloadGravatarImage(remoteContact.picture, maxPhotoSize, localContact.photoLastModified);

      if (localContact.photoLastModified == null) {
        // missing on local contact, insert it
        Log.i(TAG, "Contact " + remoteContact.email + " now has a profile image, inserting.");
        ops.add(buildPhotoInsert(response.data).withValue(Data.RAW_CONTACT_ID, localContact.rawContactId).build());
        ops.add(buildPhotoLastModifiedUpdate(localContact.rawContactId, response.lastModified));
      } else if (!localContact.photoLastModified.equals(response.lastModified)) {
        // newer version exist on remote contact, update local
        Log.i(TAG, "Contact " + remoteContact.email + " has a new profile image, updating.");
        ops.add(ContentProviderOperation.newUpdate(DATA_CONTENT_URI)
          .withSelection(
            Data.RAW_CONTACT_ID + " = ? AND " + Data.MIMETYPE + " = ?",
            new String[] { localContact.rawContactId, CommonDataKinds.Photo.CONTENT_ITEM_TYPE })
          .withValue(CommonDataKinds.Photo.PHOTO, response.data.toByteArray())
          .build());
        ops.add(buildPhotoLastModifiedUpdate(localContact.rawContactId, response.lastModified));
      }
    } catch (NoSuchElementException e) {
      if (nullOrEmpty(localContact.photoLastModified)) return; // contact has no image and has never had one
      Log.i(TAG, "Contact " + remoteContact.email + " does not have a profile image any longer, deleting from local contact.");

      // exists on local contact, but not on remote - delete on local
      ops.add(ContentProviderOperation.newDelete(DATA_CONTENT_URI)
        .withSelection(
          Data.RAW_CONTACT_ID + " = ? AND " + Data.MIMETYPE + " = ?",
          new String[] { localContact.rawContactId, CommonDataKinds.Photo.CONTENT_ITEM_TYPE })
        .build());
      ops.add(buildPhotoLastModifiedUpdate(localContact.rawContactId, null));
    } catch (IOException e) {
      // network error during download - don't rethrow, let's not fail the whole sync for this
      Log.e(TAG, "Failed to download profile image for " + remoteContact.email + ".", e);
    }
  }

  private void insertNewContact(Account account, long groupId, UserInfoResponse remoteContact) {
    Log.i(TAG, "Inserting new contact " + remoteContact.email + ".");

    String photoLastModified = null;
    ByteArrayOutputStream photo = null;

    try {
      BinaryResponse response = apiClient.downloadGravatarImage(remoteContact.picture, maxPhotoSize, null);
      photoLastModified = response.lastModified;
      photo = response.data;
    } catch (NoSuchElementException ignore) {
    } catch (IOException e) {
      // network error during download - don't rethrow, let's not fail the whole sync for this
      Log.e(TAG, "Failed to download profile image for " + remoteContact.email + ".", e);
    }

    ArrayList<ContentProviderOperation> ops = new ArrayList<>();
    int backReferenceIndex = 0;

    ops.add(ContentProviderOperation.newInsert(RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build())
      .withValue(RawContacts.ACCOUNT_TYPE, account.type)
      .withValue(RawContacts.ACCOUNT_NAME, account.name)
      .withValue(RawContacts.SOURCE_ID, remoteContact.email)
      .withValue(RawContacts.SYNC1, photoLastModified)
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

    // Photo
    if (photo != null) {
      ops.add(buildPhotoInsert(photo)
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

  private ContentProviderOperation buildPhotoLastModifiedUpdate(String rawContactId, String lastModified) {
    return ContentProviderOperation.newUpdate(RAW_CONTACT_CONTENT_URI)
      .withSelection(RawContacts._ID + " = ?", new String[] { rawContactId })
      .withValue(RawContacts.SYNC1, lastModified)
      .build();
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

  private ContentProviderOperation.Builder buildPhotoInsert(ByteArrayOutputStream photo) {
    return ContentProviderOperation.newInsert(DATA_CONTENT_URI)
      .withValue(Data.MIMETYPE, CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
      .withValue(CommonDataKinds.Photo.PHOTO, photo.toByteArray());
  }

  private boolean nullOrEmpty(String s) {
    return s == null || s.isEmpty();
  }

  private void deleteInactiveContact(LocalContact localContact) {
    Log.i(TAG, "Deleting contact " + localContact.sourceId + ".");

    ArrayList<ContentProviderOperation> ops = new ArrayList<>();
    ops.add(ContentProviderOperation.newDelete(
      ContactsContract.RawContacts.CONTENT_URI.buildUpon()
        .appendPath(localContact.rawContactId)
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

  private int getMaxPhotoSize() {
    Uri uri = ContactsContract.DisplayPhoto.CONTENT_MAX_DIMENSIONS_URI;
    String[] projection = new String[] { ContactsContract.DisplayPhoto.DISPLAY_MAX_DIM };
    Cursor cursor = resolver.query(uri, projection, null, null, null);

    try {
      cursor.moveToFirst();
      return cursor.getInt(0);
    } finally {
      cursor.close();
    }
  }
}
