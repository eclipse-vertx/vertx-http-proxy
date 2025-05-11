package io.vertx.httpproxy;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.impl.BodyTransformerImpl;

import java.util.function.Function;

/**
 * Range of transformers ready to be used.
 */
@VertxGen
public interface BodyTransformers {

  /**
   * The default maximum amount of bytes synchronous transformers apply
   */
  long DEFAULT_MAX_BUFFERED_SIZE = 256 * 1024;

  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  static BodyTransformer transform(MediaType consumedMediaType, MediaType producedMediaType, Function<Buffer, Buffer> transformer) {
    return transform(consumedMediaType, producedMediaType, DEFAULT_MAX_BUFFERED_SIZE, transformer);
  }

  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  static BodyTransformer transform(MediaType consumedMediaType, MediaType producedMediaType, long maxBufferedBytes, Function<Buffer, Buffer> transformer) {
    return new BodyTransformerImpl(transformer, maxBufferedBytes, consumedMediaType, producedMediaType);
  }

  /**
   * Create a transformer that transforms JSON object to JSON object, the transformer accepts and produces {@code application/json}.
   *
   * @param fn the operation to transform data
   * @return the transformer instance
   */
  static BodyTransformer jsonObject(Function<JsonObject, JsonObject> fn) {
    return BodyTransformerImpl.transformJsonObject(fn);
  }

  /**
   * Create a transformer that transforms JSON array to JSON array, the transformer accepts and produces {@code application/json}.
   *
   * @param fn the operation to transform data
   * @return the transformer instance
   */
  static BodyTransformer jsonArray(Function<JsonArray, JsonArray> fn) {
    return BodyTransformerImpl.transformJsonArray(fn);
  }

  /**
   * Create a transformer that transforms JSON value to JSON value, the transformer accepts and produces {@code application/json}.
   *
   * @param fn the operation to transform data
   * @return the transformer instance
   */
  static BodyTransformer jsonValue(Function<Object, Object> fn) {
    return BodyTransformerImpl.transformJson(fn);
  }

  /**
   * Create a transformer that transforms text to text, the transformer accepts and produces {@code text/plain}.
   *
   * @param fn the operation to transform data
   * @return the transformer instance
   */
  static BodyTransformer text(Function<String, String> fn, String encoding) {
    return BodyTransformerImpl.transformText(encoding, fn);
  }

  /**
   * Create a transformer that discards the body, the transformer accepts any media type.
   *
   * @return the transformer instance
   */
  static BodyTransformer discard() {
    return BodyTransformerImpl.discard();
  }
}
