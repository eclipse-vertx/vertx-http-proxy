package io.vertx.httpproxy;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ProxyClientPipelinedTest extends ProxyClientKeepAliveTest {

  public ProxyClientPipelinedTest() {
    keepAlive = true;
    pipelining = true;
  }
}
