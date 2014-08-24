package com.valtech.contactsync;

import android.accounts.Account;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.database.Cursor;
import android.net.Uri;

import java.util.HashMap;
import java.util.Map;

import static android.provider.ContactsContract.CommonDataKinds;
import static android.provider.ContactsContract.CommonDataKinds.StructuredName;
import static android.provider.ContactsContract.RawContacts;

public class ContactReader {
  private final ContentResolver resolver;

  public ContactReader(ContentResolver resolver) {
    this.resolver = resolver;
  }

  public Map<String, Contact> getStoredContacts(Account account) {
    Cursor rawContactsCursor = null;
    try {
      rawContactsCursor = resolver.query(RawContacts.CONTENT_URI, new String[] { RawContacts._ID }, RawContacts.ACCOUNT_TYPE + " = ?", new String[] { account.type }, null);
      Map<String, Contact> contacts = new HashMap<>();
      while (rawContactsCursor.moveToNext()) {
        long rawContactId = rawContactsCursor.getLong(0);
        Contact contact = getStoredContact(rawContactId);
        contacts.put(contact.sourceId, contact);
      }
      return contacts;
    } finally {
      if (rawContactsCursor != null) rawContactsCursor.close();
    }
  }

  private Contact getStoredContact(long rawContactId) {
    Uri rawContactUri = ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawContactId);
    Uri entityUri = Uri.withAppendedPath(rawContactUri, RawContacts.Entity.CONTENT_DIRECTORY);
    Cursor cursor = null;
    try {
      cursor = resolver.query(entityUri,
        new String[] { RawContacts.SOURCE_ID, RawContacts.Entity.DATA_ID, RawContacts.Entity.MIMETYPE, StructuredName.DATA1, StructuredName.DATA2 },
        null, null, null);

      Contact contact = new Contact();
      contact.rawContactId = rawContactId;
      while (cursor.moveToNext()) {
        contact.sourceId = cursor.getString(0);
        if (!cursor.isNull(1)) {
          // We will here get one row per data item for this raw contact.
          // The SOURCE_ID is duplicated for each row.
          // We can know what type of data this row contains by looking at its mime type
          String mimeType = cursor.getString(2);
          int subType = cursor.getInt(4);
          if (StructuredName.CONTENT_ITEM_TYPE.equals(mimeType)) contact.displayName = cursor.getString(3);
          if (CommonDataKinds.Email.CONTENT_ITEM_TYPE.equals(mimeType)) contact.email = cursor.getString(3);
          if (CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(mimeType) && CommonDataKinds.Phone.TYPE_WORK_MOBILE == subType) contact.phoneNumber = cursor.getString(3);
          if (CommonDataKinds.Phone.CONTENT_ITEM_TYPE.equals(mimeType) && CommonDataKinds.Phone.TYPE_WORK == subType) contact.fixedPhoneNumber = cursor.getString(3);
        }
      }
      return contact;
    } finally {
      if (cursor != null) cursor.close();
    }
  }

  public static class Contact {
    public long rawContactId;
    public String sourceId;
    public String displayName;
    public String email;
    public String phoneNumber;
    public String fixedPhoneNumber;
  }
}
