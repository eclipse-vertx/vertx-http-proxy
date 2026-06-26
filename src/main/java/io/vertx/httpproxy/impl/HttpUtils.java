/*
 * Copyright (c) 2011-2026 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package io.vertx.httpproxy.impl;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpVersion;

import java.time.Instant;
import java.util.List;

import static io.vertx.core.http.HttpHeaders.*;

class HttpUtils {

  // https://datatracker.ietf.org/doc/html/rfc2616#section-13.5.1
  private static final MultiMap HOP_BY_HOP_HEADERS = MultiMap.caseInsensitiveMultiMap()
    .add(CONNECTION, "whatever")
    .add(KEEP_ALIVE, "whatever")
    .add(PROXY_AUTHENTICATE, "whatever")
    .add(PROXY_AUTHORIZATION, "whatever")
    .add("te", "whatever")
    .add("trailer", "whatever")
    .add(TRANSFER_ENCODING, "whatever")
    .add(UPGRADE, "whatever");

  static boolean isHopByHopHeader(String name) {
    return HOP_BY_HOP_HEADERS.contains(name);
  }

  static Boolean isChunked(MultiMap headers) {
    List<String> te = headers.getAll("transfer-encoding");
    if (te != null) {
      boolean chunked = false;
      for (String val : te) {
        if (val.equals("chunked")) {
          chunked = true;
        } else {
          return null;
        }
      }
      return chunked;
    } else {
      return false;
    }
  }

  static Instant dateHeader(MultiMap headers) {
    String dateHeader = headers.get(HttpHeaders.DATE);
    if (dateHeader == null) {
      List<String> warningHeaders = headers.getAll("warning");
      if (warningHeaders.size() > 0) {
        for (String warningHeader : warningHeaders) {
          Instant date = ParseUtils.parseWarningHeaderDate(warningHeader);
          if (date != null) {
            return date;
          }
        }
      }
      return null;
    } else {
      return ParseUtils.parseHeaderDate(dateHeader);
    }
  }

  static boolean isNotHttp1x(HttpServerRequest request) {
    HttpVersion httpVersion = request.connection().protocolVersion();
    return httpVersion != HttpVersion.HTTP_1_0 && httpVersion != HttpVersion.HTTP_1_1;
  }
}
