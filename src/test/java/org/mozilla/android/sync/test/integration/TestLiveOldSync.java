/* Any copyright is dedicated to the Public Domain.
   http://creativecommons.org/publicdomain/zero/1.0/ */

package org.mozilla.android.sync.test.integration;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Executors;

import org.json.simple.JSONArray;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mozilla.android.sync.test.helpers.MockGlobalSessionCallback;
import org.mozilla.android.sync.test.integration.TestBasicFetch.LiveDelegate;
import org.mozilla.gecko.background.testhelpers.MockPrefsGlobalSession;
import org.mozilla.gecko.background.testhelpers.WaitHelper;
import org.mozilla.gecko.browserid.BrowserIDKeyPair;
import org.mozilla.gecko.browserid.MockMyIDTokenFactory;
import org.mozilla.gecko.browserid.RSACryptoImplementation;
import org.mozilla.gecko.sync.CryptoRecord;
import org.mozilla.gecko.sync.ExtendedJSONObject;
import org.mozilla.gecko.sync.GlobalSession;
import org.mozilla.gecko.sync.crypto.KeyBundle;
import org.mozilla.gecko.sync.delegates.GlobalSessionCallback;
import org.mozilla.gecko.sync.net.AuthHeaderProvider;
import org.mozilla.gecko.sync.net.HawkAuthHeaderProvider;
import org.mozilla.gecko.sync.repositories.domain.FormHistoryRecord;
import org.mozilla.gecko.sync.stage.FormHistoryServerSyncStage;
import org.mozilla.gecko.sync.stage.ServerSyncStage;
import org.mozilla.gecko.tokenserver.TokenServerClient;
import org.mozilla.gecko.tokenserver.TokenServerClientDelegate;
import org.mozilla.gecko.tokenserver.TokenServerException;
import org.mozilla.gecko.tokenserver.TokenServerToken;

@Category(IntegrationTestCategory.class)
public class TestLiveOldSync {
  static final String TEST_USERNAME     = "6gnkjphdltbntwnrgvu46ey6mu7ncjdl";
  static final String TEST_PASSWORD     = "test0425";
  static final String TEST_SYNC_KEY     = "fuyx96ea8rkfazvjdfuqumupye"; // Weave.Identity.syncKey

  protected BrowserIDKeyPair keyPair;
  protected MockMyIDTokenFactory mockMyIDTokenFactory;
  protected TokenServerToken token;
  protected AuthHeaderProvider authHeaderProvider;

  protected TokenServerToken doGetToken(final String assertion) throws URISyntaxException {
    final TokenServerToken[] tokens = new TokenServerToken[1];
    final TokenServerClient client = new TokenServerClient(new URI("http://auth.oldsync.dev.lcip.org/1.0/sync/1.1"), Executors.newSingleThreadExecutor());
    WaitHelper.getTestWaiter().performWait(new Runnable() {
      @Override
      public void run() {
        client.getTokenFromBrowserIDAssertion(assertion, true, new TokenServerClientDelegate() {
          @Override
          public void handleSuccess(TokenServerToken token) {
            tokens[0] = token;
            WaitHelper.getTestWaiter().performNotify();
          }

          @Override
          public void handleFailure(TokenServerException e) {
            WaitHelper.getTestWaiter().performNotify(e);
          }

          @Override
          public void handleError(Exception e) {
            WaitHelper.getTestWaiter().performNotify(e);
          }
        });
      }
    });
    return tokens[0];
  }

  @Before
  public void setUp() throws Exception {
    this.keyPair = RSACryptoImplementation.generateKeypair(1024);
    this.mockMyIDTokenFactory = new MockMyIDTokenFactory();
    String assertion = mockMyIDTokenFactory.createMockMyIDAssertion(keyPair, "test", "http://auth.oldsync.dev.lcip.org");
    this.token = doGetToken(assertion);
    authHeaderProvider = new HawkAuthHeaderProvider(token.id, token.key.getBytes("UTF-8"), false);
  }

  // @Test
  public void test() throws Throwable {
    final String url = token.endpoint + "/info/collections";
    LiveDelegate ld = TestBasicFetch.realLiveFetch(authHeaderProvider, url);
    System.out.println(ld.body());

    // ld = TestBasicFetch.realLivePut(token.uid, authHeaderProvider, url, );
  }

  @Test
  public void testWipeEngineOnServer() throws Exception {
    KeyBundle syncKeyBundle = KeyBundle.withRandomKeys();
    GlobalSessionCallback callback = new MockGlobalSessionCallback();
    GlobalSession session = new MockPrefsGlobalSession(token.uid, authHeaderProvider, null,
        syncKeyBundle, callback, null, null, null);
    session.config.clusterURL = new URI("http://db1.oldsync.dev.lcip.org/");

    final String COLLECTION = "forms";
    final String COLLECTION_URL = token.endpoint + "/storage/" + COLLECTION;
    final String RECORD_URL = COLLECTION_URL + "/testGuid";

    // Put record.
    FormHistoryRecord record = new FormHistoryRecord("testGuid", COLLECTION);
    record.fieldName  = "testFieldName";
    record.fieldValue = "testFieldValue";
    CryptoRecord rec = record.getEnvelope();
    rec.setKeyBundle(syncKeyBundle);
    rec.encrypt();
    LiveDelegate ld = TestBasicFetch.realLivePut(authHeaderProvider, RECORD_URL, rec);
    assertNotNull(ld.body());

    // Make sure record appears in collection guids.
    LiveDelegate fetched = TestBasicFetch.realLiveFetch(authHeaderProvider, COLLECTION_URL);
    JSONArray a = ExtendedJSONObject.parseJSONArray(fetched.body());
    assertTrue(a.contains(record.guid));

    // Make sure record is really there.
    fetched = TestBasicFetch.realLiveFetch(authHeaderProvider, RECORD_URL);
    ExtendedJSONObject o = fetched.decrypt(syncKeyBundle);
    Assert.assertEquals(record.fieldName,  o.getString("name"));
    Assert.assertEquals(record.fieldValue, o.getString("value"));

    // Wipe server engine only.
    ServerSyncStage stage = new FormHistoryServerSyncStage();
    stage.wipeServer(session); // Synchronous!

    // Make sure record does not appear in collection guids.
    a = ExtendedJSONObject.parseJSONArray(TestBasicFetch.realLiveFetch(authHeaderProvider, COLLECTION_URL).body());
    assertTrue(a.isEmpty());
  }
}
