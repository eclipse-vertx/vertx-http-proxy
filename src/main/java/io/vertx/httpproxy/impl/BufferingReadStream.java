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

import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;

class BufferingReadStream implements ReadStream<Buffer> {

  private final ReadStream<Buffer> stream;
  private final Buffer content;
  private Handler<Void> endHandler;

  public BufferingReadStream(ReadStream<Buffer> stream, Buffer content) {
    this.stream = stream;
    this.content = content;
  }

  @Override
  public ReadStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
    stream.exceptionHandler(handler);
    return this;
  }

  @Override
  public ReadStream<Buffer> handler(Handler<Buffer> handler) {
    if (handler != null) {
      stream.handler(buff -> {
        content.appendBuffer(buff);
        handler.handle(buff);
      });
    } else {
      stream.handler(null);
    }
    return this;
  }

  @Override
  public ReadStream<Buffer> pause() {
    stream.pause();
    return this;
  }

  @Override
  public ReadStream<Buffer> resume() {
    stream.resume();
    return this;
  }

  @Override
  public ReadStream<Buffer> fetch(long amount) {
    stream.fetch(amount);
    return this;
  }

  @Override
  public ReadStream<Buffer> endHandler(Handler<Void> endHandler) {
    if (endHandler != null) {
      stream.endHandler(v -> {
        endHandler.handle(null);
      });
    } else {
      stream.endHandler(null);
    }
    return this;
  }
}
