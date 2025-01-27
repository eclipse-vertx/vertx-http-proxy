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
package io.vertx.httpproxy.spi.cache;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.shareddata.ClusterSerializable;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.ProxyResponse;
import io.vertx.httpproxy.impl.ParseUtils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * The cached object.
 */
public class Resource implements ClusterSerializable {

  private static final Charset UTF_8 = StandardCharsets.UTF_8;

  private String absoluteUri;
  private int statusCode;
  private String statusMessage;
  private MultiMap headers;
  private long timestamp;
  private long maxAge;
  private Instant lastModified;
  private String etag;
  private Buffer content = Buffer.buffer();

  // For serialization purposes, do not remove.
  public Resource() {
  }

  public Resource(String absoluteUri, int statusCode, String statusMessage, MultiMap headers, long timestamp, long maxAge) {
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

  private static class Cursor {
    int i;
  }

  @Override
  public void writeToBuffer(Buffer buffer) {
    appendString(buffer, absoluteUri);
    appendInt(buffer, statusCode);
    appendString(buffer, statusMessage);
    appendMultiMap(buffer, headers);
    appendLong(buffer, timestamp);
    appendLong(buffer, maxAge);
    appendInstant(buffer, lastModified);
    appendString(buffer, etag);
    appendBuffer(buffer, content);
  }

  @Override
  public int readFromBuffer(int pos, Buffer buffer) {
    Cursor cursor = new Cursor();
    cursor.i = pos;

    setAbsoluteUri(readString(buffer, cursor));
    setStatusCode(readInt(buffer, cursor));
    setStatusMessage(readString(buffer, cursor));
    setHeaders(readMultiMap(buffer, cursor));
    setTimestamp(readLong(buffer, cursor));
    setMaxAge(readLong(buffer, cursor));
    setLastModified(readInstant(buffer, cursor));
    setEtag(readString(buffer, cursor));
    setContent(readBuffer(buffer, cursor));
    return cursor.i;
  }

  private static void appendIsNull(Buffer buffer, Object object) {
    buffer.appendByte((byte) (object == null ? 1 : 0));
  }

  private static boolean readIsNull(Buffer buffer, Cursor cursor) {
    cursor.i += 1;
    return buffer.getByte(cursor.i - 1) == (byte) 1;
  }

  private static void appendInt(Buffer buffer, int num) {
    buffer.appendInt(num);
  }

  private static int readInt(Buffer buffer, Cursor cursor) {
    cursor.i += 4;
    return buffer.getInt(cursor.i - 4);
  }

  private static void appendLong(Buffer buffer, long num) {
    buffer.appendLong(num);
  }

  private static long readLong(Buffer buffer, Cursor cursor) {
    cursor.i += 8;
    return buffer.getLong(cursor.i - 8);
  }

  private static void appendInstant(Buffer buffer, Instant instant) {
    appendIsNull(buffer, instant);
    if (instant != null) appendLong(buffer, instant.toEpochMilli());
  }

  private static Instant readInstant(Buffer buffer, Cursor cursor) {
    if (readIsNull(buffer, cursor)) return null;
    return Instant.ofEpochMilli(readLong(buffer, cursor));
  }

  private static void appendBuffer(Buffer buffer, Buffer toAppend) {
    appendIsNull(buffer, toAppend);
    if (toAppend == null) return;
    byte[] bytes = toAppend.getBytes();
    buffer.appendInt(bytes.length).appendBytes(bytes);
  }

  private static Buffer readBuffer(Buffer buffer, Cursor cursor) {
    if (readIsNull(buffer, cursor)) return null;
    int len = buffer.getInt(cursor.i);
    cursor.i += 4;
    byte[] bytes = buffer.getBytes(cursor.i, cursor.i + len);
    cursor.i += len;
    return Buffer.buffer(bytes);
  }

  private static void appendString(Buffer buffer, String string) {
    appendBuffer(buffer, string == null ? null : Buffer.buffer(string.getBytes(UTF_8)));
  }

  private static String readString(Buffer buffer, Cursor cursor) {
    Buffer result = readBuffer(buffer, cursor);
    if (result == null) return null;
    return result.toString(UTF_8);
  }

  private static void appendMultiMap(Buffer buffer, MultiMap multiMap) {
    appendIsNull(buffer, multiMap);
    if (multiMap == null) return;
    buffer.appendInt(multiMap.size());
    multiMap.forEach((key, value) -> {
      appendString(buffer, key);
      appendString(buffer, value);
    });
  }

  private static MultiMap readMultiMap(Buffer buffer, Cursor cursor) {
    if (readIsNull(buffer, cursor)) return null;
    MultiMap multiMap = MultiMap.caseInsensitiveMultiMap();
    int size = buffer.getInt(cursor.i);
    cursor.i += 4;
    for (int i = 0; i < size; i++) {
      multiMap.add(readString(buffer, cursor), readString(buffer, cursor));
    }
    return multiMap;
  }


  public String getAbsoluteUri() {
    return absoluteUri;
  }

  public int getStatusCode() {
    return statusCode;
  }

  public String getStatusMessage() {
    return statusMessage;
  }

  public MultiMap getHeaders() {
    return headers;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public long getMaxAge() {
    return maxAge;
  }

  public Instant getLastModified() {
    return lastModified;
  }

  public String getEtag() {
    return etag;
  }

  public Buffer getContent() {
    return content;
  }

  public void setAbsoluteUri(String absoluteUri) {
    this.absoluteUri = absoluteUri;
  }

  public void setStatusCode(int statusCode) {
    this.statusCode = statusCode;
  }

  public void setStatusMessage(String statusMessage) {
    this.statusMessage = statusMessage;
  }

  public void setHeaders(MultiMap headers) {
    this.headers = headers;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  public void setMaxAge(long maxAge) {
    this.maxAge = maxAge;
  }

  public void setLastModified(Instant lastModified) {
    this.lastModified = lastModified;
  }

  public void setEtag(String etag) {
    this.etag = etag;
  }

  public void setContent(Buffer content) {
    this.content = content;
  }
}
