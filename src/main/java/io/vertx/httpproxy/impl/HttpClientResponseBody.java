package io.vertx.httpproxy.impl;

import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.streams.Pipe;
import io.vertx.core.streams.ReadStream;
import io.vertx.core.streams.WriteStream;
import io.vertx.httpproxy.Body;


/**
 * created by wang007 on 2025/9/15
 */
public class HttpClientResponseBody implements Body, ReadStream<Buffer> {

  private final HttpClientResponse response;
  private final String mediaType;
  private final long length;

  private volatile Handler<Void> endHandler;

  private volatile HttpServerResponse dst;

  public HttpClientResponseBody(HttpClientResponse response, long length, String mediaType) {
    this.response = response;
    this.mediaType = mediaType;
    this.length = length;
  }

  @Override
  public String mediaType() {
    return mediaType;
  }

  @Override
  public long length() {
    return length;
  }

  @Override
  public ReadStream<Buffer> stream() {
    return this;
  }


  @Override
  public ReadStream<Buffer> exceptionHandler(@Nullable Handler<Throwable> handler) {
    response.exceptionHandler(handler);
    return this;
  }

  @Override
  public ReadStream<Buffer> handler(@Nullable Handler<Buffer> handler) {
    response.handler(handler);
    return this;
  }

  @Override
  public ReadStream<Buffer> pause() {
    response.pause();
    return this;
  }

  @Override
  public ReadStream<Buffer> resume() {
    response.resume();
    return this;
  }

  @Override
  public ReadStream<Buffer> fetch(long amount) {
    response.fetch(amount);
    return this;
  }

  @Override
  public ReadStream<Buffer> endHandler(@Nullable Handler<Void> endHandler) {
    if (endHandler == null) {
      response.endHandler(null);
      return this;
    }
    Handler<Void> current = this.endHandler;
    this.endHandler = endHandler;
    if (current != null) {
      return this;
    }

    response.endHandler(v -> {
      try {
        MultiMap trailers = response.trailers();
        if (trailers.isEmpty()) {
          return;
        }
        HttpServerResponse dst = this.dst;
        if (dst == null) {
          return;
        }
        MultiMap dstTrailers = dst.trailers();
        dstTrailers.addAll(trailers);
      } finally {
        Handler<Void> h = this.endHandler;
        if (h != null) {
          h.handle(null);
        }
      }
    });

    return this;
  }


  @Override
  public Future<Void> pipeTo(WriteStream<Buffer> dst) {
    if (dst instanceof HttpServerResponse) {
      this.dst = (HttpServerResponse) dst;
    }
    return ReadStream.super.pipeTo(dst);
  }

  @Override
  public Pipe<Buffer> pipe() {
    Pipe<Buffer> pipe = ReadStream.super.pipe();
    return new Pipe<>() {
      @Override
      public Pipe<Buffer> endOnFailure(boolean end) {
        pipe.endOnFailure(end);
        return this;
      }

      @Override
      public Pipe<Buffer> endOnSuccess(boolean end) {
        pipe.endOnSuccess(end);
        return this;
      }

      @Override
      public Pipe<Buffer> endOnComplete(boolean end) {
        pipe.endOnComplete(end);
        return this;
      }

      @Override
      public Future<Void> to(WriteStream<Buffer> dst) {
        if (dst instanceof HttpServerResponse) {
          HttpClientResponseBody.this.dst = (HttpServerResponse) dst;
        }
        return pipe.to(dst);
      }

      @Override
      public void close() {
        pipe.close();
      }
    };

  }
}
