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
package io.vertx.httpproxy.impl;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyResponse;

import java.util.Date;

class Resource {

  final String absoluteUri;
  final int statusCode;
  final String statusMessage;
  final MultiMap headers;
  final long timestamp;
  final long maxAge;
  final Date lastModified;
  final String etag;
  final Buffer content = Buffer.buffer();

  Resource(String absoluteUri, int statusCode, String statusMessage, MultiMap headers, long timestamp, long maxAge) {
    String lastModifiedHeader = headers.get(HttpHeaders.LAST_MODIFIED);
    this.absoluteUri = absoluteUri;
    this.statusCode = statusCode;
    this.statusMessage = statusMessage;
    this.headers = headers;
    this.timestamp = timestamp;
    this.maxAge = maxAge;
    this.lastModified = lastModifiedHeader != null ? ParseUtils.parseHeaderDate(lastModifiedHeader) : null;
    this.etag = headers.get(HttpHeaders.ETAG);
  }

  void init(ProxyResponse proxyResponse) {
    proxyResponse.setStatusCode(200);
    proxyResponse.setStatusMessage(statusMessage);
    proxyResponse.headers().addAll(headers);
    proxyResponse.setBody(Body.body(content));
  }
}
