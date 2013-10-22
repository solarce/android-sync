/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.sync.setup.activities;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.ParseException;
import org.mozilla.gecko.background.common.log.Logger;
import org.mozilla.gecko.fxa.authenticator.FxAccountAuthenticator;
import org.mozilla.gecko.sync.ExtendedJSONObject;
import org.mozilla.gecko.sync.NonArrayJSONException;
import org.mozilla.gecko.sync.Utils;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Activity that creates Firefox Accounts for testing.
 * <p>
 * Reads a JSON array of test accounts and instantiates them.
 */
public class CreateTestFxAccountsActivity extends SyncActivity {
  public static final String LOG_TAG = CreateTestFxAccountsActivity.class.getSimpleName();

  public static final String TEST_FIREFOX_ACCOUNTS_JSON = "test.firefox.accounts.json";

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public void onResume() {
    Logger.info(LOG_TAG, "onResume");
    super.onResume();

    try {
      JSONArray testUsers = loadTestUsers();

      if (testUsers.isEmpty()) {
        throw new RuntimeException("No test users to create!");
      }

      Logger.info(LOG_TAG, "Got " + testUsers.size() + " test users.");

      ArrayList<String> names = new ArrayList<String>();

      for (Object testUser : testUsers) {
        String name = createTestUser((JSONObject) testUser);
        if (name == null) {
          throw new RuntimeException("Can't make account with null name!");
        }
        names.add(name);
      }

      setResult(RESULT_OK);

      String message = null;
      if (names.isEmpty()) {
        message = "No accounts created.";
      } else if (names.size() == 1) {
        message = "Created account " + Utils.toCommaSeparatedString(names) + ".";
      } else {
        message = "Created accounts " + Utils.toCommaSeparatedString(names) + ".";
      }

      Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    } catch (RuntimeException e) {
      setResult(RESULT_CANCELED);
      Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
    } catch (Exception e) {
      setResult(RESULT_CANCELED);
      Toast.makeText(getApplicationContext(), "Got exception: " + e.toString(), Toast.LENGTH_LONG).show();
    } finally {
      finish();
    }
  }

  /**
   * Helper to create account from single test account datum.
   *
   * @param testUser test account data.
   * @return Android Account name, or null on failure.
   * @throws Exception
   */
  protected String createTestUser(JSONObject testUser) throws Exception {
    ExtendedJSONObject o = new ExtendedJSONObject(testUser);
    final String email = o.getString("email");

    // This is inefficient, but not worth improving.
    Account[] accounts = AccountManager.get(this).getAccounts();
    for (Account account : accounts) {
      if (email.equals(account.name)) {
        // White lie: it wasn't created, but it exists.
        return email;
      }
    }

    Account createdAccount = FxAccountAuthenticator.addAccount(this,
        email,
        o.getString("uid"),
        o.getString("sessionToken"),
        o.getString("kA"),
        o.getString("kB"));
    if (createdAccount == null) {
      return null;
    }
    return createdAccount.name;
  }

  /**
   * Helper to load test accounts from JSON file.
   *
   * @return array of test accounts.
   * @throws IOException
   * @throws ParseException
   * @throws NonArrayJSONException
   */
  private JSONArray loadTestUsers() throws IOException, ParseException, NonArrayJSONException {
    AssetManager assetManager = getAssets();
    InputStream is = assetManager.open(TEST_FIREFOX_ACCOUNTS_JSON);
    try {
      return ExtendedJSONObject.parseJSONArray(new InputStreamReader(is));
    } finally {
      is.close();
    }
  }
}
