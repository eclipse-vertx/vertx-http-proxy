package io.vertx.httpproxy.spi.cache;

import io.vertx.core.Future;
import io.vertx.core.dns.SrvRecord;


/**
 * Cache SPI.
 */
public interface Cache {

  /**
   * Being called when the proxy attempts to add a new cache item.
   * The cache can only store up to maxSize of the latest items based
   * on CacheOptions.
   *
   * @param key the URI of the resource
   * @param value the cached response
   * @return a succeed void future
   */
  Future<Void> put(String key, Resource value);

  /**
   * Being called when the proxy attempts to fetch a cache item.
   *
   * @param key the URI of the resource
   * @return the cached response, null if not exist, should all wrap with future
   */
  Future<Resource> get(String key);

  /**
   * Being called when the proxy attempts to delete a cache item,
   * typically caused by invalidating an existing item. Do nothing
   * if not exist.
   *
   * @param key the URI of the resource
   * @return a succeed void future
   */
  Future<Void> remove(String key);

  /**
   * Being called when need to close the cache.
   *
   * @return a succeed void future
   */
  Future<Void> close();
}
