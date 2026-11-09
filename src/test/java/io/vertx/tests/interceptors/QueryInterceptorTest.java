/*
 * Copyright (c) 2011-2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.tests.interceptors;

import io.netty.handler.codec.http.QueryStringDecoder;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.SocketAddress;
import io.vertx.httpproxy.ProxyInterceptor;
import io.vertx.junit5.VertxTestContext;
import io.vertx.tests.TestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author <a href="mailto:wangzengyi1935@163.com">Zengyi Wang</a>
 */
public class QueryInterceptorTest extends TestBase {

  private HttpClient client;

  @Test
  public void addParamTest(VertxTestContext ctx) {
    SocketAddress backend = startHttpBackend(8081, req -> {
      QueryStringDecoder decoder = new QueryStringDecoder(req.uri());
      Map<String, List<String>> parameters = decoder.parameters();
      assertEquals(parameters.get("k1").get(0), "v1");
      assertEquals(parameters.get("k2").get(0), "v2");
      req.response().end("Hello");
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(ProxyInterceptor.builder().settingQueryParam("k1", "v1").build()));

    client = vertx.createHttpClient();
    client.request(HttpMethod.GET, 8080, "localhost", "/hello/world?k2=v2")
      .compose(HttpClientRequest::send)
      .onComplete(ctx.succeedingThenComplete());
  }

  @Test
  public void removeParamTest(VertxTestContext ctx) {
    SocketAddress backend = startHttpBackend(8081, req -> {
      QueryStringDecoder decoder = new QueryStringDecoder(req.uri());
      Map<String, List<String>> parameters = decoder.parameters();
      assertEquals(parameters.get("k1").get(0), "v1");
      assertTrue(parameters.get("k2") == null);
      req.response().end("Hello");
    });

    startProxy(proxy -> proxy.origin(backend)
      .addInterceptor(ProxyInterceptor.builder().removingQueryParam("k2").build()));

    client = vertx.createHttpClient();
    client.request(HttpMethod.GET, 8080, "localhost", "/hello/world?k1=v1&k2=v2")
      .compose(HttpClientRequest::send)
      .onComplete(ctx.succeedingThenComplete());
  }

}
