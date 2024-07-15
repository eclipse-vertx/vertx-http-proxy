package io.vertx.httpproxy.spi.cache;

import io.vertx.core.Future;


/**
 * Cache SPI.
 */
public interface Cache {

  /**
   * Being called when the cache attempts to add a new cache item.
   *
   * @param key the URI of the resource
   * @param value the cached response
   * @return a succeed void future
   */
  Future<Void> put(String key, Resource value);

  /**
   * Being called when the cache attempts to fetch a cache item.
   *
   * @param key the URI of the resource
   * @return the cached response, null if not exist
   */
  Future<Resource> get(String key);

  /**
   * Being called when the cache attempts to delete a cache item,
   * typically caused by invalidating an existing item. Do nothing
   * if not exist.
   *
   * @param key the URI of the resource
   * @return a succeed void future
   */
  Future<Void> remove(String key);
}
