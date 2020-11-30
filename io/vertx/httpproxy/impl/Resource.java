package io.vertx.httpproxy.impl;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyResponse;

import java.util.Date;

class Resource {

  final String absoluteUri;
  final int statusCode;
  final MultiMap headers;
  final long timestamp;
  final long maxAge;
  final Date lastModified;
  final String etag;
  final Buffer content = Buffer.buffer();

  Resource(String absoluteUri, int statusCode, MultiMap headers, long timestamp, long maxAge) {
    String lastModifiedHeader = headers.get(HttpHeaders.LAST_MODIFIED);
    this.absoluteUri = absoluteUri;
    this.statusCode = statusCode;
    this.headers = headers;
    this.timestamp = timestamp;
    this.maxAge = maxAge;
    this.lastModified = lastModifiedHeader != null ? ParseUtils.parseHeaderDate(lastModifiedHeader) : null;
    this.etag = headers.get(HttpHeaders.ETAG);
  }

  void sendTo(ProxyResponse proxyResponse) {
    proxyResponse.setStatusCode(200);
    proxyResponse.headers().addAll(headers);
    proxyResponse.setBody(Body.body(content));
    proxyResponse.send(ar -> {
      
    });
  }
}
