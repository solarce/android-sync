/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.fxa.sync;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.mozilla.gecko.background.common.log.Logger;
import org.mozilla.gecko.browserid.BrowserIDKeyPair;
import org.mozilla.gecko.browserid.RSACryptoImplementation;
import org.mozilla.gecko.fxa.FxAccountBookmarksServerSyncStage;
import org.mozilla.gecko.fxa.FxAccountConfig;
import org.mozilla.gecko.fxa.FxAccountHistoryServerSyncStage;
import org.mozilla.gecko.fxa.FxAccountPasswordsServerSyncStage;
import org.mozilla.gecko.fxa.FxAccountServerSyncStage;
import org.mozilla.gecko.fxa.FxAccountServerSyncStageDelegate;
import org.mozilla.gecko.fxa.FxAccountTabsServerSyncStage;
import org.mozilla.gecko.sync.net.AuthHeaderProvider;

import android.accounts.Account;
import android.content.AbstractThreadedSyncAdapter;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.SyncResult;
import android.os.Bundle;

public class FxAccountSyncAdapter extends AbstractThreadedSyncAdapter {
  private static final String LOG_TAG = FxAccountSyncAdapter.class.getSimpleName();

  protected final ExecutorService executor;

  public FxAccountSyncAdapter(Context context, boolean autoInitialize) {
    super(context, autoInitialize);
    this.executor = Executors.newSingleThreadExecutor();
  }

  @Override
  public void onPerformSync(final Account account, final Bundle extras, final String authority, ContentProviderClient provider, SyncResult syncResult) {
    Logger.info(LOG_TAG, "Syncing FxAccount" +
        " account named " + account.name +
        " for authority " + authority +
        " with instance " + this + ".");

    final CountDownLatch latch = new CountDownLatch(1);
    try {
      final BrowserIDKeyPair keyPair = RSACryptoImplementation.generateKeypair(1024);
      Logger.info(LOG_TAG, "Generated keypair. ");

      final FxAccount fxAccount = FxAccount.fromAndroidAccount(getContext(), account);
      fxAccount.login(getContext(), keyPair, new FxAccount.Delegate() {
        @Override
        public void handleSuccess(String uid, String endpoint, AuthHeaderProvider authHeaderProvider) {
          Logger.info(LOG_TAG, "Got token! uid is " + uid + " and endpoint is " + endpoint + ".");

          final FxAccountServerSyncStageDelegate delegate = new FxAccountServerSyncStageDelegate() {
            @Override
            public void handleSuccess() {
              Logger.info(LOG_TAG, "Successfully synced " + authority + ".");
              latch.countDown();
            }

            @Override
            public void handleError(Exception e) {
              Logger.error(LOG_TAG, "Failed to sync " + authority + ".", e);
              latch.countDown();
            }
          };

          try {
            FxAccountConfig config = new FxAccountConfig(getContext(), executor, endpoint,
                fxAccount.email, fxAccount.kA, fxAccount.kB, authHeaderProvider);

            FxAccountServerSyncStage stage = makeServerSyncStage(authority, config, delegate);
            Logger.info(LOG_TAG, "Syncing " + authority + ".");
            stage.execute();
          } catch (Exception e) {
            delegate.handleError(e);
            return;
          }
        }

        @Override
        public void handleError(Exception e) {
          Logger.info(LOG_TAG, "Failed to get token.", e);
          latch.countDown();
        }
      });

      latch.await();
    } catch (Exception e) {
      Logger.error(LOG_TAG, "Got error logging in.", e);
    }
  }

  protected /* abstract */ FxAccountServerSyncStage makeServerSyncStage(String authority, FxAccountConfig config, FxAccountServerSyncStageDelegate delegate) {
    if (authority.endsWith(".browser")) {
      return new FxAccountBookmarksServerSyncStage(config, delegate);
    } else if (authority.endsWith(".formhistory")) {
      return new FxAccountHistoryServerSyncStage(config, delegate);
    } else if (authority.endsWith(".passwords")) {
      return new FxAccountPasswordsServerSyncStage(config, delegate);
    } else if (authority.endsWith(".tabs")) {
      return new FxAccountTabsServerSyncStage(config, delegate);
    }
    throw new IllegalStateException("No server sync stage for authority " + authority + ".");
  }
}
