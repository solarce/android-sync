/* Any copyright is dedicated to the Public Domain.
   http://creativecommons.org/publicdomain/zero/1.0/ */

package org.mozilla.gecko.background.fxa.test;

import java.security.NoSuchAlgorithmException;

import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mozilla.android.sync.test.integration.IntegrationTestCategory;
import org.mozilla.gecko.background.testhelpers.WaitHelper;
import org.mozilla.gecko.browserid.BrowserIDKeyPair;
import org.mozilla.gecko.browserid.RSACryptoImplementation;
import org.mozilla.gecko.fxa.sync.FxAccount;
import org.mozilla.gecko.sync.Utils;
import org.mozilla.gecko.sync.net.AuthHeaderProvider;

@Category(IntegrationTestCategory.class)
public class TestLiveFxAccount {
  protected final BrowserIDKeyPair keyPair;

  public TestLiveFxAccount() throws NoSuchAlgorithmException {
    this.keyPair = RSACryptoImplementation.generateKeypair(1024);
  }

  @Test
  public void testLogin() {
    String email = "testtestd@mockmyid.com";
    byte[] sessionTokenBytes = Utils.hex2Byte("8deef2af6acf532a07ad03ec7052e91f01213767b2bee6b27705253baff3eb72");
    byte[] kA = Utils.hex2Byte("1defe49c39d697a4213ba129adcbf08e3e838be3d16d755367fd749cfaea65c9");
    byte[] kB = Utils.hex2Byte("d02d8fe39f28b601159c543f2deeb8f72bdf2043e8279aa08496fbd9ebaea361");
    String idpEndpoint = "https://idp.dev.lcip.org/v1";
    String authEndpoint = "http://auth.oldsync.dev.lcip.org";

    final FxAccount fxa = new FxAccount(email, sessionTokenBytes, kA, kB, idpEndpoint, authEndpoint);

    WaitHelper.getTestWaiter().performWait(new Runnable() {
      @Override
      public void run() {
        fxa.login(null , keyPair, new FxAccount.Delegate() {
          @Override
          public void handleSuccess(String uid, String endpoint, AuthHeaderProvider authHeaderProvider) {
            try {
              Assert.assertNotNull(uid);
              Assert.assertNotNull(endpoint);
              WaitHelper.getTestWaiter().performNotify();
            } catch (Throwable e) {
              WaitHelper.getTestWaiter().performNotify(e);
            }
          }

          @Override
          public void handleError(Exception e) {
            WaitHelper.getTestWaiter().performNotify(e);
          }
        });
      }
    });
  }
}
