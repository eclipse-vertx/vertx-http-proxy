/*
 * Copyright (c) 2011-2020 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.tests.cache;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.unit.TestContext;
import io.vertx.httpproxy.ProxyOptions;
import io.vertx.httpproxy.cache.CacheOptions;
import io.vertx.httpproxy.impl.ParseUtils;
import io.vertx.tests.TestBase;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public abstract class CacheTestBase extends TestBase {

  protected static final String ETAG_0 = "\"etag0\"";
  protected static final String ETAG_1 = "\"etag1\"";
  protected static final int INIT = -1;
  protected static final int NOT_CALLED = 0; // second req not reached to backend
  protected static final int NORMAL = 1; // second req reached, but is not revalidate request
  protected static final int REVALIDATE_SUCCESS = 2;
  protected static final int REVALIDATE_FAIL = 3;

  public CacheTestBase() {
    super(new ProxyOptions().setCacheOptions(new CacheOptions()));
  }

  protected SocketAddress etagBackend(TestContext ctx, AtomicInteger backendResult, MultiMap additionalHeaders) {
    return startHttpBackend(ctx, 8081, req -> {
      Instant now = Instant.now();
      String ifNoneMatch = req.headers().get(HttpHeaders.IF_NONE_MATCH);
      if (ifNoneMatch != null && ifNoneMatch.equals(ETAG_0)) {
        backendResult.set(REVALIDATE_SUCCESS);
        req.response().setStatusCode(304);
      } else {
        if (ifNoneMatch != null) {
          backendResult.set(REVALIDATE_FAIL);
        } else {
          if (backendResult.get() == INIT) {
            backendResult.set(NOT_CALLED);
          } else {
            backendResult.set(NORMAL);
          }
        }
      }
      req.response().headers().setAll(additionalHeaders);
      req.response()
        .putHeader(HttpHeaders.ETAG, ETAG_0)
        .putHeader(HttpHeaders.DATE, ParseUtils.formatHttpDate(now))
        .end();
    });
  }

  protected Future<HttpClientResponse> call(HttpClient client, MultiMap additionalHeaders) {
    return client.request(HttpMethod.GET, 8080, "localhost", "/")
      .compose(req -> {
        req.headers().setAll(additionalHeaders);
        return req.send();
      });
  }

  protected Future<HttpClientResponse> call(HttpClient client) {
    return call(client, MultiMap.caseInsensitiveMultiMap());
  }
}
