/*
 * Kontalk Android client
 * Copyright (C) 2011 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.xmpp.ui;

import java.io.InputStream;
import java.security.GeneralSecurityException;

import org.kontalk.xmpp.R;
import org.kontalk.xmpp.authenticator.Authenticator;
import org.kontalk.xmpp.client.EndpointServer;
import org.kontalk.xmpp.client.ServerList;
import org.kontalk.xmpp.crypto.Coder;
import org.kontalk.xmpp.crypto.PassKey;
import org.kontalk.xmpp.provider.MyMessages.Messages;
import org.kontalk.xmpp.service.MessageCenterService;
import org.kontalk.xmpp.service.ServerListUpdater;
import org.kontalk.xmpp.util.MessageUtils;

import android.accounts.Account;
import android.annotation.TargetApi;
import android.app.ActionBar;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;


public final class MessagingPreferences extends PreferenceActivity {
    private static final String TAG = MessagingPreferences.class.getSimpleName();

    private static final String USERDATA_CRYPT_PREFIX = "crypt:";
    private static final int REQUEST_PICK_BACKGROUND = Activity.RESULT_FIRST_USER + 1;

    private static final float DEFAULT_DRAWER_HEIGHT = 200;

    private static Drawable customBackground;
    private static String balloonTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // no account - redirect to bootstrap preferences
        if (Authenticator.getDefaultAccount(this) == null) {
            startActivity(new Intent(this, BootstrapPreferences.class));
            finish();
            return;
        }

        addPreferencesFromResource(R.xml.preferences);

        setupActivity();

        // push notifications checkbox
        final Preference pushNotifications = findPreference("pref_push_notifications");
        pushNotifications.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                CheckBoxPreference pref = (CheckBoxPreference) preference;
                if (pref.isChecked())
                    MessageCenterService.enablePushNotifications(getApplicationContext());
                else
                    MessageCenterService.disablePushNotifications(getApplicationContext());

                return true;
            }
        });

        // message center restart
        final Preference restartMsgCenter = findPreference("pref_restart_msgcenter");
        restartMsgCenter.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Log.w(TAG, "manual message center restart requested");
                MessageCenterService.restart(getApplicationContext());
                Toast.makeText(MessagingPreferences.this, R.string.msg_msgcenter_restarted, Toast.LENGTH_SHORT).show();
                return true;
            }
        });

        // use custom background
        final Preference customBg = findPreference("pref_custom_background");
        customBg.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // discard reference to custom background drawable
                customBackground = null;
                return false;
            }
        });

        // set background
        final Preference setBackground = findPreference("pref_background_uri");
        setBackground.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                startActivityForResult(i, REQUEST_PICK_BACKGROUND);
                return true;
            }
        });

        //
        final Preference balloons = findPreference("pref_balloons");
        balloons.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                balloonTheme = (String) newValue;
                return true;
            }
        });

        // manual server address is handled in Application context

        // server list last update timestamp
        final Preference updateServerList = findPreference("pref_update_server_list");
        updateServerList.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                final ServerListUpdater updater = new ServerListUpdater(MessagingPreferences.this);

                final ProgressDialog diag = new ProgressDialog(MessagingPreferences.this);
                diag.setCancelable(true);
                diag.setMessage(getString(R.string.serverlist_updating));
                diag.setIndeterminate(true);
                diag.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        updater.cancel();
                    }
                });

                updater.setListener(new ServerListUpdater.UpdaterListener() {
                    @Override
                    public void error(Throwable e) {
                        diag.cancel();
                        MessagingPreferences.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MessagingPreferences.this, R.string.serverlist_update_error,
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }

                    @Override
                    public void nodata() {
                        diag.cancel();
                        MessagingPreferences.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MessagingPreferences.this, R.string.serverlist_update_nodata,
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }

                    @Override
                    public void updated(final ServerList list) {
                        diag.dismiss();
                        MessagingPreferences.this.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                updateServerListLastUpdate(updateServerList, list);
                                // restart message center
                                MessageCenterService.restart(getApplicationContext());
                            }
                        });
                    }
                });

                diag.show();
                updater.start();
                return true;
            }
        });

        // update 'last update' string
        ServerList list = ServerListUpdater.getCurrentList(this);
        if (list != null)
            updateServerListLastUpdate(updateServerList, list);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void setupActivity() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            ActionBar bar = getActionBar();
            bar.setDisplayShowHomeEnabled(true);
            bar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                startActivity(new Intent(this, ConversationList.class));
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_PICK_BACKGROUND) {
            if (resultCode == RESULT_OK) {
                // invalidate any previous reference
                customBackground = null;
                SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                prefs.edit()
                    .putString("pref_background_uri", data.getDataString())
                    .commit();
            }
        }
        else
            super.onActivityResult(requestCode, resultCode, data);
    }

    private static void updateServerListLastUpdate(Preference pref, ServerList list) {
        Context context = pref.getContext();
        String timestamp = MessageUtils.formatTimeStampString(context, list.getDate().getTime(), true);
        pref.setSummary(context.getString(R.string.server_list_last_update, timestamp));
    }

    private static String getString(Context context, String key, String defaultValue) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(key, defaultValue);
    }

    private static int getInt(Context context, String key, int defaultValue) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getInt(key, defaultValue);
    }

    private static long getLong(Context context, String key, long defaultValue) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getLong(key, defaultValue);
    }

    private static boolean getBoolean(Context context, String key, boolean defaultValue) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getBoolean(key, defaultValue);
    }

    /** Retrieve a boolean and if false set it to true. */
    private static boolean getBooleanOnce(Context context, String key) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean value = prefs.getBoolean(key, false);
        if (!value)
            prefs.edit().putBoolean(key, true).commit();
        return value;
    }

    private static String getServerURI(Context context) {
        return getString(context, "pref_network_uri", null);
    }

    /** Returns a random server from the cached list or the user-defined server. */
    public static EndpointServer getEndpointServer(Context context) {
        String customUri = getServerURI(context);
        if (!TextUtils.isEmpty(customUri)) {
            try {
                return new EndpointServer(customUri);
            }
            catch (Exception e) {
                // custom is not valid - take one from list
            }
        }

        ServerList list = ServerListUpdater.getCurrentList(context);
        return (list != null) ? list.random() : null;
    }

    public static boolean getEncryptionEnabled(Context context) {
        return getBoolean(context, "pref_encrypt", true);
    }

    /** Returns a {@link Coder} instance for encrypting contents. */
    public static Coder getEncryptCoder(String passphrase) {
        return new Coder(new PassKey(passphrase));
    }

    /** Returns a {@link Coder} instance for decrypting contents. */
    public static Coder getDecryptCoder(Context context, String myNumber) {
        return new Coder(new PassKey(myNumber));
    }

    public static boolean getPushNotificationsEnabled(Context context) {
        return getBoolean(context, "pref_push_notifications", true);
    }

    public static boolean getNotificationsEnabled(Context context) {
        return getBoolean(context, "pref_enable_notifications", true);
    }

    public static String getNotificationVibrate(Context context) {
        return getString(context, "pref_vibrate", "never");
    }

    public static String getNotificationRingtone(Context context) {
        return getString(context, "pref_ringtone", null);
    }

    public static boolean setLastCountryCode(Context context, int countryCode) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.edit()
            .putInt("pref_countrycode", countryCode)
            .commit();
    }

    public static int getLastCountryCode(Context context) {
        return getInt(context, "pref_countrycode", 0);
    }

    public static boolean getContactsListVisited(Context context) {
        return getBooleanOnce(context, "pref_contacts_visited");
    }

    public static long getLastSyncTimestamp(Context context) {
        return getLong(context, "pref_last_sync", -1);
    }

    public static boolean setLastSyncTimestamp(Context context, long timestamp) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.edit()
            .putLong("pref_last_sync", timestamp)
            .commit();
    }

    /** TODO cache value */
    public static String getFontSize(Context context) {
        return getString(context, "pref_font_size", "medium");
    }

    public static int getBalloonResource(Context context, int direction) {
        if (balloonTheme == null)
            balloonTheme = getString(context, "pref_balloons", "classic");

        if ("iphone".equals(balloonTheme))
            return direction == Messages.DIRECTION_IN ?
                R.drawable.balloon_iphone_incoming :
                    R.drawable.balloon_iphone_outgoing;
        else if ("old_classic".equals(balloonTheme))
            return direction == Messages.DIRECTION_IN ?
                R.drawable.balloon_old_classic_incoming :
                    R.drawable.balloon_old_classic_outgoing;

        // all other cases
        return direction == Messages.DIRECTION_IN ?
            R.drawable.balloon_classic_incoming :
                R.drawable.balloon_classic_outgoing;
    }

    public static String getStatusMessage(Context context) {
        return getString(context, "pref_status_message", null);
    }

    /**
     * Retrieves the status message for internal use (e.g. encrypting it if
     * requested).
     */
    public static String getStatusMessageInternal(Context context) {
        String status = getStatusMessage(context);

        if (status != null && getBoolean(context, "pref_encrypt_userdata", true)) {
            // retrive own number for encryption key
            Account acc = Authenticator.getDefaultAccount(context);
            Coder coder = new Coder(new PassKey(acc.name));

            try {
                byte[] statusEnc = coder.encrypt(status.getBytes());
                // encode to Base64 for safe sending
                return USERDATA_CRYPT_PREFIX + Base64.encodeToString(statusEnc, Base64.NO_WRAP);
            }
            catch (GeneralSecurityException e) {
                // encryption error, will return cleartext status message
            }
        }

        return status;
    }

    public static boolean setStatusMessage(Context context, String message) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.edit()
            .putString("pref_status_message", message)
            .commit();
    }

    public static String decryptUserdata(Context context, String data) {
        // retrive own number for decryption key
        Account acc = Authenticator.getDefaultAccount(context);
        return decryptUserdata(context, data, acc.name);
    }

    public static String decryptUserdata(Context context, String data, String key) {
        if (data != null && data.startsWith(USERDATA_CRYPT_PREFIX) && key != null) {
            Coder coder = new Coder(new PassKey(key));
            data = data.substring(USERDATA_CRYPT_PREFIX.length());

            try {
                byte[] statusEnc = Base64.decode(data, Base64.NO_WRAP);
                byte[] statusClear = coder.decrypt(statusEnc);
                return new String(statusClear);
            }
            catch (GeneralSecurityException e) {
                // decryption error, will return status as-is
                return data;
            }
        }

        else {
            return data;
        }
    }

    public static Drawable getConversationBackground(Context context) {
        InputStream in = null;
        try {
            if (getBoolean(context, "pref_custom_background", false)) {
                if (customBackground == null) {
                    String _customBg = getString(context, "pref_background_uri", null);
                    in = context.getContentResolver().openInputStream(Uri.parse(_customBg));

                    BitmapFactory.Options opt = new BitmapFactory.Options();
                    opt.inSampleSize = 4;
                    Bitmap bmap = BitmapFactory.decodeStream(in, null, opt);
                    customBackground = new BitmapDrawable(context.getResources(), bmap);
                }
                return customBackground;
            }
        }
        catch (Exception e) {
            // ignored
        }
        finally {
            try {
                in.close();
            }
            catch (Exception e) {
                // ignored
            }
        }
        return null;
    }

    public static boolean getBigUpgrade1(Context context) {
        return getBooleanOnce(context, "bigupgrade1");
    }

    /**
     * Switches offline mode on or off.
     * @return offline mode status before the switch
     */
    public static boolean switchOfflineMode(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean old = prefs.getBoolean("offline_mode", false);
        // set flag again!
        boolean offline = !old;
        prefs.edit().putBoolean("offline_mode", offline).commit();

        if (offline) {
            // stop the message center and never start it again
            MessageCenterService.stop(context);
        }
        else {
            MessageCenterService.start(context);
        }

        return old;
    }

    public static boolean getOfflineMode(Context context) {
        return getBoolean(context, "offline_mode", false);
    }

    public static boolean getOfflineModeUsed(Context context) {
        return getBoolean(context, "offline_mode_used", false);
    }

    public static boolean setOfflineModeUsed(Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.edit()
            .putBoolean("offline_mode_used", true)
            .commit();
    }

    /** Combines various settings into a group of UserStatus flags. */
    public static int getUserFlags(Context context) {
        int flags = 0;
        /*
        if (getBoolean(context, "pref_hide_presence", false))
            flags |= UserStatusFlags.FLAG_HIDE_PRESENCE_VALUE;
         */

        // TODO other flags
        return flags;
    }

    public static boolean getSendTyping(Context context) {
        return getBoolean(context, "pref_send_typing", true);
    }

    public static String getDialPrefix(Context context) {
        String pref = getString(context, "pref_remove_prefix", null);
        return (pref != null && !TextUtils.isEmpty(pref.trim())) ? pref: null;
    }

    public static String getPushSenderId(Context context) {
        return getString(context, "pref_push_sender", null);
    }

    public static boolean setPushSenderId(Context context, String senderId) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.edit()
            .putString("pref_push_sender", senderId)
            .commit();
    }

    public static int getDrawerHeight (Context context) {
        return getInt(context, "pref_drawer_height", MessageUtils.getDensityPixel(context, DEFAULT_DRAWER_HEIGHT));
    }

    public static boolean setDrawerHeight (Context context, int height) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.edit()
            .putInt("pref_drawer_height", height)
            .commit();
    }

    public static int getIdleTimeMillis(Context context, int defaultValue) {
        String val = getString(context, "pref_idle_time", null);
        int nval;
        try {
            nval = Integer.valueOf(val);
        }
        catch (Exception e) {
            nval = defaultValue;
        }
        return (nval < defaultValue) ? defaultValue : nval;
    }

    /** Recent statuses database helper. */
    private static final class RecentStatusDbHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "status.db";
        private static final int DATABASE_VERSION = 1;

        private static final String TABLE_STATUS = "status";
        private static final String SCHEMA_STATUS = "CREATE TABLE " + TABLE_STATUS + " (" +
            "_id INTEGER PRIMARY KEY," +
            "status TEXT UNIQUE," +
            "timestamp INTEGER" +
            ")";

        public RecentStatusDbHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(SCHEMA_STATUS);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // no upgrade for version 1
        }

        public Cursor query() {
            SQLiteDatabase db = getReadableDatabase();
            return db.query(TABLE_STATUS, new String[] { BaseColumns._ID, "status" },
                null, null, null, null, "timestamp DESC");
        }

        public void insert(String status) {
            SQLiteDatabase db = getWritableDatabase();
            ContentValues v = new ContentValues(2);
            v.put("status", status);
            v.put("timestamp", System.currentTimeMillis());
            db.replace(TABLE_STATUS, null, v);

            // delete old entries
            db.delete(TABLE_STATUS, "_id NOT IN (SELECT _id FROM " +
                TABLE_STATUS + " ORDER BY timestamp DESC LIMIT 10)", null);
        }
    }

    private static RecentStatusDbHelper recentStatusDb;

    private static void _recentStatusDbHelper(Context context) {
        if (recentStatusDb == null)
            recentStatusDb = new RecentStatusDbHelper(context.getApplicationContext());
    }

    /** Retrieves the list of recently used status messages. */
    public static Cursor getRecentStatusMessages(Context context) {
        _recentStatusDbHelper(context);
        return recentStatusDb.query();
    }

    public static void addRecentStatusMessage(Context context, String status) {
        _recentStatusDbHelper(context);
        recentStatusDb.insert(status);
        recentStatusDb.close();
    }

    public static void start(Activity context) {
        Intent intent = new Intent(context, MessagingPreferences.class);
        context.startActivityIfNeeded(intent, -1);
    }

}
