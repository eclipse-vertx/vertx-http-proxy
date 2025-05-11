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
package io.vertx.httpproxy;

import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.httpproxy.impl.BufferedReadStream;

/**
 * Handles the HTTP proxy body.
 * <p>
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
@VertxGen
public interface Body {

  /**
   * Create a new {@code Body} instance.
   *
   * @param stream the {@code ReadStream} of the body
   * @param len the determined length of the body
   * @return a reference to this, so the API can be used fluently
   */
  static Body body(ReadStream<Buffer> stream, long len) {
    return body(stream, len, MediaType.APPLICATION_OCTET_STREAM);
  }

  static Body body(ReadStream<Buffer> stream, long len, MediaType mediatype) {
    return new Body() {
      @Override
      public String mediaType() {
        return mediatype != null ? mediatype.toString() : null;
      }
      @Override
      public long length() {
        return len;
      }
      @Override
      public ReadStream<Buffer> stream() {
        return stream;
      }
    };
  }

  static Body body(ReadStream<Buffer> stream, long len, String mediatype) {
    return new Body() {
      @Override
      public String mediaType() {
        return mediatype;
      }
      @Override
      public long length() {
        return len;
      }
      @Override
      public ReadStream<Buffer> stream() {
        return stream;
      }
    };
  }

  /**
   * Create a new {@code Body} instance.
   *
   * @param stream the {@link ReadStream} of the body
   * @return a reference to this, so the API can be used fluently
   */
  static Body body(ReadStream<Buffer> stream) {
    return body(stream, -1L);
  }

  static Body body(Buffer buffer) {
    return body(buffer, MediaType.APPLICATION_OCTET_STREAM);
  }

  /**
   * Create a new {@code Body} instance.
   *
   * @param buffer the {@link Buffer} of the body
   * @param mediaType the body media type
   * @return a reference to this, so the API can be used fluently
   */
  static Body body(Buffer buffer, MediaType mediaType) {
    return new Body() {
      @Override
      public String mediaType() {
        return mediaType == null ? null : mediaType.toString();
      }
      @Override
      public long length() {
        return buffer.length();
      }
      @Override
      public ReadStream<Buffer> stream() {
        return new BufferedReadStream(buffer);
      }
    };
  }

  /**
   * @return the media type of this body
   */
  String mediaType();

  /**
   *
   * Get length of the {@code Body}.
   *
   * @return the body length or {@code -1} if that can't be determined
   */
  long length();

  /**
   *
   * Get stream of the {@code Body}.
   *
   * @return the body stream
   */
  ReadStream<Buffer> stream();

}
