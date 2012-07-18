/* Any copyright is dedicated to the Public Domain.
   http://creativecommons.org/publicdomain/zero/1.0/ */

package org.mozilla.gecko.sync.config.test;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.mozilla.android.sync.test.AndroidSyncTestCase;
import org.mozilla.gecko.db.BrowserContract;
import org.mozilla.gecko.sync.ExtendedJSONObject;
import org.mozilla.gecko.sync.Utils;
import org.mozilla.gecko.sync.config.AccountPickler;
import org.mozilla.gecko.sync.setup.Constants;
import org.mozilla.gecko.sync.setup.SyncAccounts;
import org.mozilla.gecko.sync.setup.SyncAccounts.SyncAccountParameters;
import org.mozilla.gecko.sync.setup.test.TestSyncAccounts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;

public class TestAccountPickler extends AndroidSyncTestCase {
  public static final String TEST_FILENAME = "test.json";
  public static final String TEST_ACCOUNTTYPE = Constants.ACCOUNTTYPE_SYNC;

  // Test account names must start with TEST_USERNAME in order to be recognized
  // as test accounts and deleted in tearDown.
  public static final String TEST_USERNAME   = "testAccount@mozilla.com";
  public static final String TEST_USERNAME2  = TEST_USERNAME + "2";

  public static final String TEST_SYNCKEY    = "testSyncKey";
  public static final String TEST_PASSWORD   = "testPassword";
  public static final String TEST_SERVER_URL = "test.server.url/";
  public static final String TEST_CLIENT_NAME = "testClientName";
  public static final String TEST_CLIENT_GUID = "testClientGuid";

  protected SyncAccountParameters params;
  protected Context context;
  protected AccountManager accountManager;
  protected int numAccounts;

  public void setUp() {
    context = getApplicationContext();
    accountManager = AccountManager.get(context);
    params = new SyncAccountParameters(context, accountManager,
        TEST_USERNAME, TEST_SYNCKEY, TEST_PASSWORD, TEST_SERVER_URL, null, TEST_CLIENT_NAME, TEST_CLIENT_GUID);

    numAccounts = accountManager.getAccountsByType(TEST_ACCOUNTTYPE).length;
  }

  public static List<Account> getTestAccounts(final AccountManager accountManager) {
    final List<Account> testAccounts = new ArrayList<Account>();

    final Account[] accounts = accountManager.getAccountsByType(TEST_ACCOUNTTYPE);
    for (Account account : accounts) {
      if (account.name.startsWith(TEST_USERNAME)) {
        testAccounts.add(account);
      }
    }

    return testAccounts;
  }

  public void deleteTestAccounts() {
    for (Account account : getTestAccounts(accountManager)) {
      TestSyncAccounts.deleteAccount(this, accountManager, account);
    }
  }

  public void tearDown() {
    deleteTestAccounts();
    assertEquals(numAccounts, accountManager.getAccountsByType(TEST_ACCOUNTTYPE).length);
  }

  public static void assertFileNotPresent(final Context context, final String filename) throws Exception {
    // Verify file is not present.
    FileInputStream fis = null;
    try {
      fis = context.openFileInput(TEST_FILENAME);
      fail("Should get FileNotFoundException.");
    } catch (FileNotFoundException e) {
      // Do nothing; file should not exist.
    } finally {
      if (fis != null) {
        fis.close();
      }
    }
  }

  public void testPersist() throws Exception {
    context.deleteFile(TEST_FILENAME);
    assertFileNotPresent(context, TEST_FILENAME);

    AccountPickler.pickle(context, TEST_FILENAME, params, true);

    final String s = Utils.readFile(context, TEST_FILENAME);
    assertNotNull(s);

    final ExtendedJSONObject o = ExtendedJSONObject.parseJSONObject(s);
    assertEquals(TEST_USERNAME,  o.getString(Constants.JSON_KEY_ACCOUNT));
    assertEquals(TEST_PASSWORD,  o.getString(Constants.JSON_KEY_PASSWORD));
    assertEquals(TEST_SERVER_URL, o.getString(Constants.JSON_KEY_SERVER));
    assertEquals(TEST_SYNCKEY,   o.getString(Constants.JSON_KEY_SYNCKEY));
    assertNull(o.getString(Constants.JSON_KEY_CLUSTER));
    assertEquals(TEST_CLIENT_NAME, o.getString(Constants.JSON_KEY_CLIENT_NAME));
    assertEquals(TEST_CLIENT_GUID, o.getString(Constants.JSON_KEY_CLIENT_GUID));
    assertEquals(new Boolean(true), o.get(Constants.JSON_KEY_SYNC_AUTOMATICALLY));
    assertEquals(new Long(AccountPickler.VERSION), o.getLong(Constants.JSON_KEY_VERSION));
    assertTrue(o.containsKey(Constants.JSON_KEY_TIMESTAMP));
  }

  public void testDeletePickle() throws Exception {
    AccountPickler.pickle(context, TEST_FILENAME, params, false);

    // Verify file is present.
    final String s = Utils.readFile(context, TEST_FILENAME);
    assertNotNull(s);
    assertTrue(s.length() > 0);

    AccountPickler.deletePickle(context, TEST_FILENAME);
    assertFileNotPresent(context, TEST_FILENAME);
  }

  public Account deleteAccountsAndUnpickle(final Context context, final AccountManager accountManager, final String filename) {
    deleteTestAccounts();
    assertEquals(0, getTestAccounts(accountManager).size());

    return AccountPickler.unpickle(context, filename);
  }

  public void testUnpickleSuccess() throws Exception {
    AccountPickler.pickle(context, TEST_FILENAME, params, true);

    // Make sure we have no accounts hanging around.
    final Account account = deleteAccountsAndUnpickle(context, accountManager, TEST_FILENAME);
    assertNotNull(account);

    try {
      assertEquals(1, getTestAccounts(accountManager).size());
      assertTrue(ContentResolver.getSyncAutomatically(account, BrowserContract.AUTHORITY));
      assertEquals(account.name, TEST_USERNAME);

      // Verify Account parameters are in place.  Not testing clusterURL since it's stored in
      // shared prefs and it's less critical.
      final String password = accountManager.getPassword(account);
      final String serverURL  = accountManager.getUserData(account, Constants.OPTION_SERVER);
      final String syncKey    = accountManager.getUserData(account, Constants.OPTION_SYNCKEY);
      final String clientName = accountManager.getUserData(account, Constants.CLIENT_NAME);
      final String clientGuid = accountManager.getUserData(account, Constants.ACCOUNT_GUID);

      assertEquals(TEST_PASSWORD, password);
      assertEquals(TEST_SERVER_URL, serverURL);
      assertEquals(TEST_SYNCKEY, syncKey);
      assertEquals(TEST_CLIENT_NAME, clientName);
      assertEquals(TEST_CLIENT_GUID, clientGuid);
    } finally {
      TestSyncAccounts.deleteAccount(this, accountManager, account);
    }
  }

  public void testUnpickleNoAutomatic() throws Exception {
    AccountPickler.pickle(context, TEST_FILENAME, params, false);

    // Make sure we have no accounts hanging around.
    final Account account = deleteAccountsAndUnpickle(context, accountManager, TEST_FILENAME);
    assertNotNull(account);

    try {
      assertEquals(1, getTestAccounts(accountManager).size());
      assertFalse(ContentResolver.getSyncAutomatically(account, BrowserContract.AUTHORITY));
    } finally {
      TestSyncAccounts.deleteAccount(this, accountManager, account);
    }
  }

  public void testUnpickleNoFile() {
    // Just in case file is hanging around.
    context.deleteFile(TEST_FILENAME);

    final Account account = deleteAccountsAndUnpickle(context, accountManager, TEST_FILENAME);
    assertNull(account);
  }

  public void testUnpickleIncompleteUserData() throws Exception {
    final FileOutputStream fos = context.openFileOutput(TEST_FILENAME, Context.MODE_PRIVATE);
    final PrintStream ps = (new PrintStream(fos));
    ps.print("{}"); // Valid JSON, just missing everything.
    ps.close();
    fos.close();

    final Account account = deleteAccountsAndUnpickle(context, accountManager, TEST_FILENAME);
    assertNull(account);
  }

  public void testUnpickleMalformedFile() throws Exception {
    final FileOutputStream fos = context.openFileOutput(TEST_FILENAME, Context.MODE_PRIVATE);
    final PrintStream ps = (new PrintStream(fos));
    ps.print("{1:!}"); // Not valid JSON.
    ps.close();
    fos.close();

    final Account account = deleteAccountsAndUnpickle(context, accountManager, TEST_FILENAME);
    assertNull(account);
  }

  public void testUnpickleAccountAlreadyExists() {
    AccountPickler.pickle(context, TEST_FILENAME, params, false);

    // Make sure we have no test accounts hanging around.
    final Account account = deleteAccountsAndUnpickle(context, accountManager, TEST_FILENAME);
    assertNotNull(account);
    assertEquals(TEST_USERNAME, account.name);

    // Now replace file with new username.
    params = new SyncAccountParameters(context, accountManager,
        TEST_USERNAME2, TEST_SYNCKEY, TEST_PASSWORD, TEST_SERVER_URL, null, TEST_CLIENT_NAME, TEST_CLIENT_GUID);
    AccountPickler.pickle(context, TEST_FILENAME, params, false);

    // Checking if sync accounts exist could try to unpickle. That unpickle
    // would load an account with a different username, so we can check that
    // nothing was unpickled by verifying that the username has not changed.
    assertTrue(SyncAccounts.syncAccountsExist(context));

    for (Account a : getTestAccounts(accountManager)) {
      assertEquals(TEST_USERNAME, a.name);
    }
  }
}