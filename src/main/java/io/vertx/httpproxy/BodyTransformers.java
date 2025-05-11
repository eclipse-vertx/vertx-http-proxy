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

  /**
   * Like {@link #transform(MediaType, MediaType, long, Function)} with {@link #DEFAULT_MAX_BUFFERED_SIZE} maximum buffered bytes.
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  static BodyTransformer transform(MediaType consumes, MediaType produces, Function<Buffer, Buffer> transformer) {
    return transform(consumes, produces, DEFAULT_MAX_BUFFERED_SIZE, transformer);
  }

  /**
   * <p>Create a body transformer that transforms {@code consumes} media type to the {@code produces} media type, by
   * applying the {@code transformer} function.</p>
   *
   * <p>The transformer buffers up to {@code maxBufferedBytes} and then apply the {@code transformer} function. When
   * the body exceeds the limit, the transformes signal an error and fail the transformation.</p>
   *
   * @param consumes the consumed media  type
   * @param produces the produced media type
   * @param maxBufferedBytes the maximum amount of bytes buffered by the body transformer
   * @param transformer the transformer
   * @return the body transformer
   */
  @GenIgnore(GenIgnore.PERMITTED_TYPE)
  static BodyTransformer transform(MediaType consumes, MediaType produces, long maxBufferedBytes, Function<Buffer, Buffer> transformer) {
    return new BodyTransformerImpl(transformer, maxBufferedBytes, consumes, produces);
  }

  /**
   * Create a body transformer that transforms JSON object to JSON object, the transformer consumes and produces
   * {@code application/json}.
   *
   * @param maxBufferedBytes the maximum amount of bytes buffered by the body transformer
   * @param transformer the transformer function
   * @return the transformer instance
   */
  static BodyTransformer jsonObject(long maxBufferedBytes, Function<JsonObject, JsonObject> transformer) {
    return BodyTransformerImpl.transformJsonObject(maxBufferedBytes, transformer);
  }

  /**
   * Like {@link #jsonObject(long, Function)} with {@link #DEFAULT_MAX_BUFFERED_SIZE} maximum buffered bytes.
   */
  static BodyTransformer jsonObject(Function<JsonObject, JsonObject> transformer) {
    return jsonObject(DEFAULT_MAX_BUFFERED_SIZE, transformer);
  }

  /**
   * Create a body transformer that transforms JSON array to JSON array, the transformer consumes and produces
   * {@code application/json}.
   *
   * @param maxBufferedBytes the maximum amount of bytes buffered by the body transformer
   * @param transformer the transformer function
   * @return the transformer instance
   */
  static BodyTransformer jsonArray(long maxBufferedBytes, Function<JsonArray, JsonArray> transformer) {
    return BodyTransformerImpl.transformJsonArray(maxBufferedBytes, transformer);
  }

  /**
   * Like {@link #jsonArray(long, Function)} with {@link #DEFAULT_MAX_BUFFERED_SIZE} maximum buffered bytes.
   */
  static BodyTransformer jsonArray(Function<JsonArray, JsonArray> transformer) {
    return jsonArray(DEFAULT_MAX_BUFFERED_SIZE, transformer);
  }

  /**
   * Create a body transformer that transforms JSON value to JSON value, the transformer consumes and produces
   * {@code application/json}.
   *
   * @param maxBufferedBytes the maximum amount of bytes buffered by the body transformer
   * @param transformer the transformer function
   * @return the transformer instance
   */
  static BodyTransformer jsonValue(long maxBufferedBytes, Function<Object, Object> transformer) {
    return BodyTransformerImpl.transformJsonValue(maxBufferedBytes, transformer);
  }

  /**
   * Like {@link #jsonValue(long, Function)} with {@link #DEFAULT_MAX_BUFFERED_SIZE} maximum buffered bytes.
   */
  static BodyTransformer jsonValue(Function<Object, Object> transformer) {
    return jsonValue(DEFAULT_MAX_BUFFERED_SIZE, transformer);
  }

  /**
   * Create a transformer that transforms text to text, the transformer consumes and produces {@code text/plain}.
   *
   * @param maxBufferedBytes the maximum amount of bytes buffered by the body transformer
   * @param transformer the transformer function
   * @return the transformer instance
   */
  static BodyTransformer text(long maxBufferedBytes, Function<String, String> transformer, String encoding) {
    return BodyTransformerImpl.transformText(maxBufferedBytes, encoding, transformer);
  }

  /**
   * Like {@link #text(long, Function, String)} with {@link #DEFAULT_MAX_BUFFERED_SIZE} maximum buffered bytes.
   */
  static BodyTransformer text(Function<String, String> transformer, String encoding) {
    return text(DEFAULT_MAX_BUFFERED_SIZE, transformer, encoding);
  }

  /**
   * Create a transformer that discards the body, the transformer consumes any media type.
   *
   * @return the transformer instance
   */
  static BodyTransformer discard() {
    return BodyTransformerImpl.discard();
  }
}
