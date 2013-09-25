/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.gecko.fxa;

import org.mozilla.gecko.sync.repositories.RecordFactory;
import org.mozilla.gecko.sync.repositories.Repository;
import org.mozilla.gecko.sync.repositories.Server11Repository;
import org.mozilla.gecko.sync.repositories.android.PasswordsRepositorySession.PasswordsRepository;
import org.mozilla.gecko.sync.repositories.domain.PasswordRecordFactory;

public class FxAccountPasswordsServerSyncStage extends FxAccountServerSyncStage {
  private static final String COLLECTION = "passwords";

  public FxAccountPasswordsServerSyncStage(FxAccountConfig config, FxAccountServerSyncStageDelegate delegate) {
    super(config, delegate);
  }

  @Override
  protected Repository makeRemoteRepository() throws Exception {
    return new Server11Repository(COLLECTION, config.getCollectionPath(COLLECTION), config.getAuthHeaderProvider());
  }

  @Override
  protected Repository makeLocalRepository() {
    return new PasswordsRepository();
  }

  @Override
  public String name() {
    return COLLECTION;
  }

  @Override
  protected RecordFactory getRecordFactory() {
    return new PasswordRecordFactory();
  }
}
