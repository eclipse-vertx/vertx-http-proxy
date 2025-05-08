package io.vertx.httpproxy;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.httpproxy.impl.BodyTransformerImpl;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * A synchronous function that transforms an HTTP body entity.
 */
@VertxGen
@Unstable
public interface BodyTransformer {

  /**
   * @return whether this transformer consumes the {@code mediaType}, {@code mediaType} can be {@code null}
   *         when the HTTP head does not present a body, the default implementation returns {@code false}
   */
  default boolean consumes(MediaType mediaType) {
    return false;
  }

  /**
   * @return the media type produced by this transformer, the default implementation returns {@code application/octet-stream}
   */
  default MediaType produces(MediaType mediaType) {
    return mediaType;
  }

  @GenIgnore
  Function<Buffer, Buffer> transformer(MediaType mediaType);

  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  static BodyTransformer transformer(MediaType consumedMediaType, MediaType producedMediaType, Function<Buffer, Buffer> transformer) {
    return new BodyTransformerImpl(transformer, consumedMediaType, producedMediaType);
  }

  /**
   * Create a transformer that transforms JSON object to JSON object, the transformer accepts and produces {@code application/json}.
   *
   * @param fn the operation to transform data
   * @return the transformer instance
   */
  static BodyTransformer transformJsonObject(Function<JsonObject, JsonObject> fn) {
    return BodyTransformerImpl.transformJsonObject(fn);
  }

  /**
   * Create a transformer that transforms JSON array to JSON array, the transformer accepts and produces {@code application/json}.
   *
   * @param fn the operation to transform data
   * @return the transformer instance
   */
  static BodyTransformer transformJsonArray(Function<JsonArray, JsonArray> fn) {
    return BodyTransformerImpl.transformJsonArray(fn);
  }

  /**
   * Create a transformer that transforms JSON value to JSON value, the transformer accepts and produces {@code application/json}.
   *
   * @param fn the operation to transform data
   * @return the transformer instance
   */
  static BodyTransformer transformJson(Function<Object, Object> fn) {
    return BodyTransformerImpl.transformJson(fn);
  }

  /**
   * Create a transformer that transforms text to text, the transformer accepts and produces {@code text/plain}.
   *
   * @param fn the operation to transform data
   * @return the transformer instance
   */
  static BodyTransformer transformText(Function<String, String> fn, String encoding) {
    return BodyTransformerImpl.transformText(fn, encoding);
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
