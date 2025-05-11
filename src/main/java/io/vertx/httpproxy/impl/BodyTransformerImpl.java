package io.vertx.httpproxy.impl;

import io.vertx.core.Completable;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.streams.ReadStream;
import io.vertx.httpproxy.Body;
import io.vertx.httpproxy.BodyTransformer;
import io.vertx.httpproxy.BodyTransformers;
import io.vertx.httpproxy.MediaType;

import java.util.function.Function;

public class BodyTransformerImpl implements BodyTransformer {

  private final Function<Buffer, Buffer> transformer;
  private final long maxBufferedBytes;
  private final MediaType consumes;
  private final MediaType produces;

  public BodyTransformerImpl(Function<Buffer, Buffer> transformer,
                             long maxBufferedBytes,
                             MediaType consumedMediaType,
                             MediaType produces) {
    this.transformer = transformer;
    this.maxBufferedBytes = maxBufferedBytes;
    this.consumes = consumedMediaType;
    this.produces = produces;
  }

  @Override
  public Future<Body> transform(Body body) {
    Promise<Body> p = Promise.promise();
    BodyAccumulator accumulator = new BodyAccumulator(transformer, maxBufferedBytes, (b, err) -> {
      if (err == null) {
        p.complete(Body.body(b, produces));
      } else {
        p.fail(err);
      }
    });
    ReadStream<Buffer> stream = body.stream();
    stream.handler(accumulator::handleBuffer);
    stream.endHandler(accumulator::handleEnd);
    stream.resume();
    return p.future();
  }

  private static class BodyAccumulator {

    private final long maxBufferedBytes;
    private final Function<Buffer, Buffer> transformer;
    private final Completable<Buffer> completion;

    private Buffer accumulator = Buffer.buffer();

    BodyAccumulator(Function<Buffer, Buffer> transformer, long maxBufferedBytes, Completable<Buffer> completion) {
      this.transformer = transformer;
      this.maxBufferedBytes = maxBufferedBytes;
      this.completion = completion;
    }

    void handleBuffer(Buffer buffer) {
      if (accumulator != null) {
        accumulator.appendBuffer(buffer);
        if (buffer.length() > maxBufferedBytes) {
          accumulator = null;
        }
      }
    }

    void handleEnd(Void end) {
      if (accumulator != null) {
        Buffer body = transformBody(accumulator);
        accumulator = null;
        if (body != null) {
          completion.succeed(body);
          return;
        }
      } else {
        // Overflow
      }
      completion.fail(new ProxyFailure(500));
    }

    private Buffer transformBody(Buffer body) {
      try {
        return transformer.apply(body);
      } catch (Exception e) {
        return null;
      }
    }
  }

  @Override
  public boolean consumes(MediaType mediaType) {
    return consumes == null || (mediaType != null && consumes.accepts(mediaType));
  }

  @Override
  public MediaType produces(MediaType mediaType) {
    return produces;
  }

  public static BodyTransformerImpl transformJsonObject(Function<JsonObject, JsonObject> transformer) {
    return transformJsonObject(BodyTransformers.DEFAULT_MAX_BUFFERED_SIZE, transformer);
  }

  public static BodyTransformerImpl transformJsonObject(long maxBufferedBytes, Function<JsonObject, JsonObject> transformer) {
    return new BodyTransformerImpl(buffer -> transformer.apply(buffer.toJsonObject()).toBuffer(), maxBufferedBytes, MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON);
  }

  public static BodyTransformerImpl transformJsonArray(Function<JsonArray, JsonArray> transformer) {
    return transformJsonArray(BodyTransformers.DEFAULT_MAX_BUFFERED_SIZE, transformer);
  }

  public static BodyTransformerImpl transformJsonArray(long maxBufferedBytes, Function<JsonArray, JsonArray> transformer) {
    return new BodyTransformerImpl(buffer -> transformer.apply(buffer.toJsonArray()).toBuffer(), maxBufferedBytes, MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON);
  }

  public static BodyTransformerImpl transformJson(Function<Object, Object> transformer) {
    return transformJson(BodyTransformers.DEFAULT_MAX_BUFFERED_SIZE, transformer);
  }

  public static BodyTransformerImpl transformJson(long maxBufferedBytes, Function<Object, Object> transformer) {
    return new BodyTransformerImpl(buffer -> Json.encodeToBuffer(transformer.apply(Json.decodeValue(buffer))), maxBufferedBytes, MediaType.APPLICATION_JSON, MediaType.APPLICATION_JSON);
  }

  public static BodyTransformerImpl transformText(String encoding, Function<String, String> transformer) {
    return transformText(BodyTransformers.DEFAULT_MAX_BUFFERED_SIZE, encoding, transformer);
  }

  public static BodyTransformerImpl transformText(long maxBufferedBytes, String encoding, Function<String, String> transformer) {
    return new BodyTransformerImpl(buffer -> Buffer.buffer(transformer.apply(buffer.toString(encoding))), maxBufferedBytes, MediaType.TEXT_PLAIN, MediaType.TEXT_PLAIN);
  }

  public static BodyTransformer discard() {
    return new BodyTransformer() {
      @Override
      public boolean consumes(MediaType mediaType) {
        return true;
      }
      @Override
      public MediaType produces(MediaType mediaType) {
        return null;
      }
      @Override
      public Future<Body> transform(Body body) {
        return Future.succeededFuture(Body.body(Buffer.buffer(), null));
      }
    };
  }
}
