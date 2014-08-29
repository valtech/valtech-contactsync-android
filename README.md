# Valtech Contact Sync Android

This android app syncs contact details for Valtech employees to your phone.
It is connected to Valtech's identity provider (Valtech IDP), https://id.valtech.com/.

This app also serves to demonstrate how easy it is to integrate with Valtech IDP and its OAuth 2 interface.
It also serves as a public, free and open source (MIT) example of how to write a Contact Provider for Android.


## How to use

1. Download and install the app from Google Play (soon).
2. Open **Android Settings** -> **Accounts**.
3. Click **Add account** and choose **Valtech**.
4. Sign in to IDP using your email and password.
5. Done! Contacts for your Valtech country will now be synced to your phone.
6. Configure:
  1. Go to **Android Settings** -> **Accounts** -> **Valtech**.
  2. Click **Countries to sync**.
  3. Select which countries to sync contacts from (your own country will be preselected).

If you unselect a previously selected country to sync from, contacts synced from that country will be removed. If you uninstall the app all contacts synced will be removed.

The app will sync contacts automatically once per day (when internet is available). You can revoke access for the app at any time at https://id.valtech.com/.


## Local development

1. Download the [Android SDK](http://developer.android.com/sdk/index.html).
2. Install IntelliJ IDEA.
3. Create the file `<repo-root>/local.properties` with the contents `sdk.dir=/path/to/android/sdk`.
4. Import the project in IntelliJ.
  1. Open IntelliJ.
  2. Import project.
  3. Select the valtech-contactsync-android folder.
  4. Import project from external Model: Gradle.
  5. Select "User gradle default wrapper (recommended)".
  6. Finish.
5. Add the Android SDK.
	1. File -> Project structure -> SDKs.
	2. Click +, choose Android SDK.
	3. Select path to the Android SDK.
	4. Click OK.
	5. Go to Project in Project Structure.
	6. Choose Android as the Project SDK.
6. Do `cp idp.xml.template app/src/main/res/values/idp.xml` and fill in the client secret.


## Release

1. Get the key for signing the APK from IT (initially created using `keytool -genkey -v -keystore valtech.keystore -alias valtech -keyalg RSA -keysize 2048 -validity 10000`).
2. Put the `valtech.keystore` at repository root.
3. Make sure you have the correct credentials in `app/src/main/res/values/idp.xml`.
4. Update `versionCode` and `versionName` in `app/build.gradle`.
4. Run ` SIGNING_KEY=<putsecretkeyhere> ./gradlew assembleRelease` (add a space before `SIGNING_KEY` as that will hide the command from history if you [have `$HISTCONTROL` set](http://stackoverflow.com/questions/8473121/execute-command-without-keeping-it-in-history)).
5. Upload `app/build/apk/app-release.apk` to [Google Play Developer Console](https://play.google.com/apps/publish/).


## Credits

This project is heavily inspired by [jimlar/valtechdroid](https://github.com/jimlar/valtechdroid).
