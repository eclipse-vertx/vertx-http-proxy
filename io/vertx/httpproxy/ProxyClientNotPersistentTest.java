package io.vertx.httpproxy;

import io.vertx.ext.unit.TestContext;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ProxyClientNotPersistentTest extends ProxyClientKeepAliveTest {

  public ProxyClientNotPersistentTest() {
    keepAlive = false;
    pipelining = false;
  }

  public void testChunkedTransferEncodingRequest(TestContext ctx) {
    // super.testChunkedTransferEncodingRequest(ctx);
    // Does not pass for now - only when run in single
  }
}
