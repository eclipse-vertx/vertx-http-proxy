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

public class BufferedReadStream implements ReadStream<Buffer> {

  private long demand = 0L;
  private Handler<Void> endHandler;
  private Handler<Buffer> handler;
  private boolean ended = false;
  private final Buffer content;

  public BufferedReadStream() {
    this.content = Buffer.buffer();
  }

  public BufferedReadStream(Buffer content) {
    this.content = content;
  }

  @Override
  public ReadStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
    return this;
  }

  @Override
  public ReadStream<Buffer> handler(Handler<Buffer> handler) {
    this.handler = handler;
    return this;
  }

  @Override
  public ReadStream<Buffer> pause() {
    demand = 0L;
    return this;
  }

  @Override
  public ReadStream<Buffer> resume() {
    fetch(Long.MAX_VALUE);
    return this;
  }

  @Override
  public ReadStream<Buffer> fetch(long amount) {
    if (!ended && amount > 0) {
      ended = true;
      demand += amount;
      if (demand < 0L) {
        demand = Long.MAX_VALUE;
      }
      if (demand != Long.MAX_VALUE) {
        demand--;
      }
      if (handler != null && content.length() > 0) {
        handler.handle(content);
      }
      if (endHandler != null) {
        endHandler.handle(null);
      }
    }
    return this;
  }

  @Override
  public ReadStream<Buffer> endHandler(Handler<Void> endHandler) {
    this.endHandler = endHandler;
    return this;
  }
}
