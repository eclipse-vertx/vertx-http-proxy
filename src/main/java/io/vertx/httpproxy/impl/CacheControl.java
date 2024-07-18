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

import java.math.BigInteger;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CacheControl {

  private int maxAge;
  private int maxStale;
  private int minFresh;
  private boolean noCache;
  private boolean noStore;
  private boolean noTransform;
  private boolean onlyIfCached;
  private boolean mustRevalidate;
  private boolean mustUnderstand;
  private boolean _private;
  private boolean proxyRevalidate;
  private boolean _public;
  private int sMaxage;

  public CacheControl parse(String header) {
    noCache = false;
    noStore = false;
    noTransform = false;
    onlyIfCached = false;
    mustRevalidate = false;
    mustUnderstand = false;
    _private = false;
    proxyRevalidate = false;
    _public = false;

    String[] parts = header.split(","); // No regex
    for (String part : parts) {
      part = part.trim().toLowerCase();
      switch (part) {
        case "public":
          _public = true;
          break;
        case "no-cache":
          noCache = true;
          break;
        case "no-store":
          noStore = true;
          break;
        case "no-transform":
          noTransform = true;
          break;
        case "only-if-cached":
          onlyIfCached = true;
          break;
        case "must-revalidate":
          mustRevalidate = true;
          break;
        case "must-understand":
          mustUnderstand = true;
          break;
        case "private":
          _private = true;
          break;
        case "proxy-revalidate":
          proxyRevalidate = true;
          break;
        default:
          maxAge = loadInt(part, "max-age=");
          maxStale = loadInt(part, "max-stale=");
          minFresh = loadInt(part, "min-fresh=");
          sMaxage = loadInt(part, "s-maxage=");
          break;
      }
    }
    return this;
  }

  private static int loadInt(String part, String prefix) {
    if (part.startsWith(prefix)) {
      BigInteger valueRaw = new BigInteger(part.substring(prefix.length()));
      return valueRaw
        .min(BigInteger.valueOf(Integer.MAX_VALUE))
        .max(BigInteger.ZERO).intValueExact();
    }
    return -1;
  }

  public int maxAge() {
    return maxAge;
  }

  public boolean isPublic() {
    return _public;
  }

  public int maxStale() {
    return maxStale;
  }

  public int minFresh() {
    return minFresh;
  }

  public boolean isNoCache() {
    return noCache;
  }

  public boolean isNoStore() {
    return noStore;
  }

  public boolean isNoTransform() {
    return noTransform;
  }

  public boolean isOnlyIfCached() {
    return onlyIfCached;
  }

  public boolean isMustRevalidate() {
    return mustRevalidate;
  }

  public boolean isMustUnderstand() {
    return mustUnderstand;
  }

  public boolean isPrivate() {
    return _private;
  }

  public boolean isProxyRevalidate() {
    return proxyRevalidate;
  }

  public int sMaxage() {
    return sMaxage;
  }
}
