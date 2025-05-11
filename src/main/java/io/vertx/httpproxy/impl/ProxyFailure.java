package io.vertx.httpproxy.impl;

import io.vertx.core.VertxException;

public class ProxyFailure extends VertxException {

  private final int code;

  public ProxyFailure(int code) {
    super("Proxy failure code=" + code);
    this.code = code;
  }

  public ProxyFailure(int code, Throwable cause) {
    super("Proxy failure code=" + code, cause);
    this.code = code;
  }

  public int code() {
    return code;
  }
}
