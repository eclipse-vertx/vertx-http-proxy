package io.vertx.httpproxy.impl;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;

class BufferingWriteStream implements WriteStream<Buffer> {

  private final Buffer content;

  public BufferingWriteStream() {
    this.content = Buffer.buffer();
  }

  public Buffer content() {
    return content;
  }

  @Override
  public WriteStream<Buffer> exceptionHandler(Handler<Throwable> handler) {
    return this;
  }

  @Override
  public Future<Void> write(Buffer data) {
    content.appendBuffer(data);
    return Future.succeededFuture();
  }

  @Override
  public void write(Buffer data, Handler<AsyncResult<Void>> handler) {
    content.appendBuffer(data);
    handler.handle(Future.succeededFuture());
  }

  @Override
  public void end(Handler<AsyncResult<Void>> handler) {
    handler.handle(Future.succeededFuture());
  }

  @Override
  public WriteStream<Buffer> setWriteQueueMaxSize(int maxSize) {
    return this;
  }

  @Override
  public boolean writeQueueFull() {
    return false;
  }

  @Override
  public WriteStream<Buffer> drainHandler(@Nullable Handler<Void> handler) {
    return this;
  }
}
