package io.vertx.httpproxy.impl;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CacheControl {

  private int maxAge;
  private boolean _public;

  public CacheControl parse(String header) {
    maxAge = -1;
    _public = false;
    String[] parts = header.split(","); // No regex
    for (String part : parts) {
      part = part.trim().toLowerCase();
      switch (part) {
        case "public":
          _public = true;
          break;
        default:
          if (part.startsWith("max-age=")) {
            maxAge = Integer.parseInt(part.substring(8));

          }
          break;
      }
    }
    return this;
  }

  public int maxAge() {
    return maxAge;
  }

  public boolean isPublic() {
    return _public;
  }

}
