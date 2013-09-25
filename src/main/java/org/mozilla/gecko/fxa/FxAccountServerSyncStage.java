/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.fxa;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import org.mozilla.gecko.sync.NoCollectionKeysSetException;
import org.mozilla.gecko.sync.crypto.KeyBundle;
import org.mozilla.gecko.sync.middleware.Crypto5MiddlewareRepository;
import org.mozilla.gecko.sync.repositories.RecordFactory;
import org.mozilla.gecko.sync.repositories.Repository;
import org.mozilla.gecko.sync.synchronizer.ServerLocalSynchronizer;
import org.mozilla.gecko.sync.synchronizer.Synchronizer;
import org.mozilla.gecko.sync.synchronizer.SynchronizerDelegate;

import android.accounts.Account;
import android.content.Context;

public abstract class FxAccountServerSyncStage implements SynchronizerDelegate {
  public static final String LOG_TAG = FxAccountServerSyncStage.class.getSimpleName();

  public final FxAccountConfig config;

  protected final FxAccountServerSyncStageDelegate delegate;

  protected Synchronizer synchronizer;

  public FxAccountServerSyncStage(FxAccountConfig config, FxAccountServerSyncStageDelegate delegate) {
    this.config = config;
    this.delegate = delegate;
  }

  /**
   * Create the local <code>Repository</code> instance.
   *
   * @return <code>Repository</code> instance.
   */
  protected abstract Repository makeLocalRepository() throws URISyntaxException;

  /**
   * Create the remote <code>Repository</code> instance.
   *
   * @return <code>Repository</code> instance.
   * @throws Exception
   */
  protected abstract Repository makeRemoteRepository() throws URISyntaxException, Exception;

  /**
   * Return the name of this stage.
   *
   * @return <code>String</code> name.
   */
  public abstract String name();

  protected abstract RecordFactory getRecordFactory();

  protected Repository wrapRemoteRepository(Repository repository) throws NoCollectionKeysSetException, URISyntaxException, InvalidKeyException, NoSuchAlgorithmException, UnsupportedEncodingException {
    KeyBundle collectionKey = config.keyBundleForCollection(name());
    Crypto5MiddlewareRepository cryptoRepo = new Crypto5MiddlewareRepository(repository, collectionKey);
    cryptoRepo.recordFactory = getRecordFactory();
    return cryptoRepo;
  }

  protected Synchronizer makeSynchronizer() throws Exception {
    Synchronizer synchronizer = new ServerLocalSynchronizer();
    synchronizer.repositoryA = wrapRemoteRepository(makeRemoteRepository());
    synchronizer.repositoryB = makeLocalRepository();
    return synchronizer;
  }

  public void execute() {
    try {
      this.synchronizer = makeSynchronizer();
    } catch (Exception e) {
      delegate.handleError(e);
      return;
    }
    synchronizer.synchronize(config.getAndroidContext(), this);
  }

  @Override
  public void onSynchronized(Synchronizer synchronizer) {
    delegate.handleSuccess();
  }

  @Override
  public void onSynchronizeFailed(Synchronizer synchronizer, Exception lastException, String reason) {
    delegate.handleError(lastException);
  }

  /**
   * Extract a PICL sync configuration from an Android Account object.
   * <p>
   * This should get auth tokens, keys, server URLs, as appropriate. This is the
   * last time a PICL sync should see the Android Account object.
   *
   * @param account
   *          to extract from.
   * @return a <code>PICLConfig</code> instance.
   */
  protected FxAccountConfig configFromAccount(Context context, Account account) throws FxAccountException {
//    AccountManager accountManager = AccountManager.get(context);
//    String kA = AccountManager.get(getContext()).getUserData(account, "kA");
//    ExecutorService executor = Executors.newSingleThreadExecutor();
    return null; // new FxAccountConfig(context, executor, , account.name, kA); // XXX
  }
}
