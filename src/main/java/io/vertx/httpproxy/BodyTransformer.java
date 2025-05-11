package io.vertx.httpproxy;

import io.vertx.codegen.annotations.GenIgnore;
import io.vertx.codegen.annotations.Unstable;
import io.vertx.codegen.annotations.VertxGen;
import io.vertx.core.Future;

/**
 * A synchronous function that transforms an HTTP body entity.
 */
@VertxGen
@Unstable
public interface BodyTransformer {

  /**
   * @return whether this transformer consumes the {@code mediaType}, {@code mediaType} can be {@code null}
   *         when the HTTP head does not present a body, the default implementation returns {@code true}
   */
  default boolean consumes(MediaType mediaType) {
    return true;
  }

  /**
   * @return the media type produced by this transformer, the default implementation returns the same media type
   */
  default MediaType produces(MediaType mediaType) {
    return mediaType;
  }

  /**
   * Return the future body, transformed.
   *
   * The default implementation returns the same body.
   *
   * @param body the body to rewrite
   * @return a future of the transformed body
   */
  @GenIgnore
  default Future<Body> transform(Body body) {
    return Future.succeededFuture(body);
  }
}
