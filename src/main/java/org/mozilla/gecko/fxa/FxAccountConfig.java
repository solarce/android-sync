/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.fxa;

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutorService;

import org.mozilla.gecko.background.common.GlobalConstants;
import org.mozilla.gecko.background.fxa.FxAccountUtils;
import org.mozilla.gecko.sync.SyncConfiguration;
import org.mozilla.gecko.sync.Utils;
import org.mozilla.gecko.sync.crypto.KeyBundle;
import org.mozilla.gecko.sync.net.AuthHeaderProvider;
import org.mozilla.gecko.sync.setup.Constants;

import android.content.Context;
import android.content.SharedPreferences;

public class FxAccountConfig {
  public final Context context;
  public final ExecutorService executor;

  public final String serverURL;
  public final String email;
  public final byte[] kA;
  public final byte[] kB;

  protected final SharedPreferences accountSharedPreferences;
  protected final AuthHeaderProvider authHeaderProvider;

  protected final KeyBundle syncKeyBundle;

  public FxAccountConfig(Context context, ExecutorService executor, String serverURL, String email, byte[] kA, byte[] kB, AuthHeaderProvider authHeaderProvider)
      throws InvalidKeyException, NoSuchAlgorithmException, UnsupportedEncodingException {
    if (context == null) {
      throw new IllegalArgumentException("context cannot be null");
    }
    //    if (executor == null) {
    //      throw new IllegalArgumentException("executor cannot be null");
    //    }
    if (serverURL == null) {
      throw new IllegalArgumentException("serverURL cannot be null");
    }
    if (email == null) {
      throw new IllegalArgumentException("email cannot be null");
    }
    if (kA == null) {
      throw new IllegalArgumentException("kA cannot be null");
    }
    if (kB == null) {
      throw new IllegalArgumentException("kB cannot be null");
    }

    this.context = context;
    this.executor = executor;
    this.serverURL = serverURL;
    this.email = email;
    this.kA = kA;
    this.kB = kB;
    this.authHeaderProvider = authHeaderProvider;

    String product = GlobalConstants.BROWSER_INTENT_PACKAGE;
    String profile = Constants.DEFAULT_PROFILE;
    long version = SyncConfiguration.CURRENT_PREFS_VERSION;
    try {
      accountSharedPreferences = Utils.getSharedPreferences(context, product, email, serverURL, profile, version);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    this.syncKeyBundle = FxAccountUtils.generateSyncKeyBundle(kB);
  }

  public Context getAndroidContext() {
    return context;
  }

  public synchronized String getClientName() {
    String clientName = accountSharedPreferences.getString(SyncConfiguration.PREF_CLIENT_NAME, null);
    if (clientName == null) {
      clientName = GlobalConstants.MOZ_APP_DISPLAYNAME + " on " + android.os.Build.MODEL;
      accountSharedPreferences.edit().putString(SyncConfiguration.PREF_CLIENT_NAME, clientName).commit();
    }
    return clientName;
  }

  public synchronized String getClientGUID() {
    String accountGUID = accountSharedPreferences.getString(SyncConfiguration.PREF_ACCOUNT_GUID, null);
    if (accountGUID == null) {
      accountGUID = Utils.generateGuid();
      accountSharedPreferences.edit().putString(SyncConfiguration.PREF_ACCOUNT_GUID, accountGUID).commit();
    }
    return accountGUID;
  }

  public String getCollectionPath(String collection) {
    return serverURL + "/storage/" + collection;
  }

  public AuthHeaderProvider getAuthHeaderProvider() {
    return authHeaderProvider; // null; // new HawkAuthHeaderProvider(id, key, false); // XXX
  }

  /**
   * Return key bundle for given collection.
   * <p>
   * For now, always returns master Sync key bundle.
   *
   * @param collection to return key bundle for.
   * @return key bundle.
   * @throws InvalidKeyException
   * @throws NoSuchAlgorithmException
   * @throws UnsupportedEncodingException
   */
  public KeyBundle keyBundleForCollection(String collection) throws InvalidKeyException, NoSuchAlgorithmException, UnsupportedEncodingException {
    return syncKeyBundle;
  }
}
