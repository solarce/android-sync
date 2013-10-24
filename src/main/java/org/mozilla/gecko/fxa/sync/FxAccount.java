/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.fxa.sync;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.mozilla.gecko.background.common.log.Logger;
import org.mozilla.gecko.background.fxa.FxAccountClient;
import org.mozilla.gecko.browserid.BrowserIDKeyPair;
import org.mozilla.gecko.browserid.JSONWebTokenUtils;
import org.mozilla.gecko.fxa.authenticator.FxAccountAuthenticator;
import org.mozilla.gecko.sync.ExtendedJSONObject;
import org.mozilla.gecko.sync.HTTPFailureException;
import org.mozilla.gecko.sync.Utils;
import org.mozilla.gecko.sync.net.AuthHeaderProvider;
import org.mozilla.gecko.sync.net.HawkAuthHeaderProvider;
import org.mozilla.gecko.sync.net.SyncStorageResponse;
import org.mozilla.gecko.tokenserver.TokenServerClient;
import org.mozilla.gecko.tokenserver.TokenServerClientDelegate;
import org.mozilla.gecko.tokenserver.TokenServerException;
import org.mozilla.gecko.tokenserver.TokenServerToken;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;
import ch.boye.httpclientandroidlib.HttpResponse;

public class FxAccount {
  private static final String LOG_TAG = FxAccount.class.getSimpleName();

  public interface Delegate {
    public void handleSuccess(String uid, String endpoint, AuthHeaderProvider authHeaderProvider);
    public void handleError(Exception e);
  }

  protected final String email;
  protected final byte[] sessionTokenBytes;
  protected final byte[] kA;
  protected final byte[] kB;
  protected final String idpEndpoint;
  protected final String authEndpoint;
  protected final ExecutorService executor;

  public FxAccount(String email, byte[] sessionTokenBytes, byte[] kA, byte[] kB, String idpEndpoint, String authEndpoint) {
    this.email = email;
    this.sessionTokenBytes = sessionTokenBytes;
    this.kA = kA;
    this.kB = kB;
    this.idpEndpoint = idpEndpoint;
    this.authEndpoint = authEndpoint;
    this.executor = Executors.newSingleThreadExecutor();
  }

  public void login(final Context context, final BrowserIDKeyPair keyPair, final Delegate delegate) {
    ExtendedJSONObject keyPairObject;
    try {
      keyPairObject = new ExtendedJSONObject(keyPair.getPublic().serialize());
    } catch (Exception e) {
      delegate.handleError(e);
      return;
    }

    FxAccountClient fxAccountClient = new FxAccountClient(idpEndpoint, Executors.newSingleThreadExecutor());
    fxAccountClient.sign(sessionTokenBytes, keyPairObject, 24*60*60*1000, new FxAccountClient.RequestDelegate<String>() {
      @Override
      public void handleError(Exception e) {
        Logger.error(LOG_TAG, "Failed to sign.", e);
        delegate.handleError(e);
      }

      @Override
      public void handleFailure(int status, HttpResponse response) {
        HTTPFailureException e = new HTTPFailureException(new SyncStorageResponse(response));
        Logger.error(LOG_TAG, "Failed to sign.", e);
        delegate.handleError(e);
      }

      @Override
      public void handleSuccess(String certificate) {
        Logger.info(LOG_TAG, "Got certificate " + certificate);
        try {
//          ExtendedJSONObject o = JSONWebTokenUtils.extractPayload(certificate);
//          System.out.println("payl: " + o.toJSONString());
//          System.out.println("ciat: " + o.getLong("iat"));
//          System.out.println("cexp: " + o.getLong("exp"));
//          System.out.println(" now: " + System.currentTimeMillis());
//
//          long iat = Math.max(System.currentTimeMillis(), o.getLong("iat")) + 1;
          String assertion = JSONWebTokenUtils.createAssertion(keyPair.getPrivate(), certificate, authEndpoint);
              // JSONWebTokenUtils.DEFAULT_ASSERTION_ISSUER, iat, JSONWebTokenUtils.DEFAULT_ASSERTION_DURATION_IN_MILLISECONDS);
          Logger.info(LOG_TAG, "Generated assertion " + assertion);

          JSONWebTokenUtils.dumpAssertion(assertion);

          TokenServerClient tokenServerclient = new TokenServerClient(new URI(authEndpoint + "/1.0/sync/1.1"), executor); // XXX check slashes.

          tokenServerclient.getTokenFromBrowserIDAssertion(assertion, true, new TokenServerClientDelegate() {
            @Override
            public void handleSuccess(TokenServerToken token) {
              Logger.info(LOG_TAG, "token.id: " + token.id);
              Logger.info(LOG_TAG, "token.key: " + token.key);
              Logger.info(LOG_TAG, "token.uid: " + token.uid);
              Logger.info(LOG_TAG, "token.endpoint: " + token.endpoint);
              AuthHeaderProvider authHeaderProvider;
              try {
                authHeaderProvider = new HawkAuthHeaderProvider(token.id, token.key.getBytes("UTF-8"), false);
              } catch (UnsupportedEncodingException e) {
                Logger.error(LOG_TAG, "Failed to sync.", e);
                delegate.handleError(e);
                return;
              }

              delegate.handleSuccess(token.uid, token.endpoint, authHeaderProvider);
            }

            @Override
            public void handleFailure(TokenServerException e) {
              Logger.warn(LOG_TAG, "Failed fetching server token.", e);
              delegate.handleError(e);
            }

            @Override
            public void handleError(Exception e) {
              Logger.error(LOG_TAG, "Got error fetching token server token.", e);
              delegate.handleError(e);
            }
          });
        } catch (Exception e) {
          Logger.error(LOG_TAG, "Got error doing stuff.", e);
          delegate.handleError(e);
        }
      }
    });
  }

  public static FxAccount fromAndroidAccount(Context context, Account account) {
    AccountManager accountManager = AccountManager.get(context);
    // final String uid = accountManager.getUserData(account, FxAccountAuthenticator.UID);
    final byte[] sessionTokenBytes = Utils.hex2Byte(accountManager.getUserData(account, FxAccountAuthenticator.JSON_KEY_SESSION_TOKEN));
    final byte[] kA = Utils.hex2Byte(accountManager.getUserData(account, FxAccountAuthenticator.JSON_KEY_KA), 16); // XXX
    final byte[] kB = Utils.hex2Byte(accountManager.getUserData(account, FxAccountAuthenticator.JSON_KEY_KB), 16); // XXX

    final String idpEndpoint = accountManager.getUserData(account, FxAccountAuthenticator.JSON_KEY_IDP_ENDPOINT); // XXX end point check.
    final String authEndpoint = accountManager.getUserData(account, FxAccountAuthenticator.JSON_KEY_AUTH_ENDPOINT); // XXX end point check.

    Logger.info(LOG_TAG, "idpEndPoint: " + idpEndpoint);
    Logger.info(LOG_TAG, "authEndPoint: " + authEndpoint);

    return new FxAccount(account.name, sessionTokenBytes, kA, kB, idpEndpoint, authEndpoint);
  }
}
