package io.vertx.tests.parsing;

import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.httpproxy.spi.cache.Resource;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

public class ResourceParseTest {

  public static boolean resourceEquals(Resource r1, Resource r2) {
    boolean same = r1.getStatusCode() == r2.getStatusCode()
      && r1.getTimestamp() == r2.getTimestamp()
      && r1.getMaxAge() == r2.getMaxAge()
      && Objects.equals(r1.getAbsoluteUri(), r2.getAbsoluteUri())
      && Objects.equals(r1.getStatusMessage(), r2.getStatusMessage())
      && Objects.equals(r1.getLastModified(), r2.getLastModified())
      && Objects.equals(r1.getEtag(), r2.getEtag());
    if (!same) return false;

    if (r1.getHeaders() == null ^ r2.getHeaders() == null) return false;
    if (r1.getHeaders() != null && r2.getHeaders() != null) {
      MultiMap h1 = r1.getHeaders();
      MultiMap h2 = r2.getHeaders();
      if (h1.size() != h2.size()) return false;
      for (Map.Entry<String, String> e : h1.entries()) {
        if (!Objects.equals(e.getValue(), h2.get(e.getKey()))) return false;
      }
    }

    if (r1.getContent() == null ^ r2.getContent() == null) return false;
    if (r1.getContent() != null && r2.getContent() != null) {
      if (!Arrays.equals(r1.getContent().getBytes(), r2.getContent().getBytes())) return false;
    }

    return true;
  }

  @Test
  public void testRegular() {
    Resource resource = new Resource(
      "http://www.example.com",
      200,
      "OK",
      MultiMap.caseInsensitiveMultiMap()
        .add(HttpHeaders.LAST_MODIFIED, "Fri, 12 Jul 2024 12:34:56 GMT")
        .add(HttpHeaders.ETAG, "etag0"),
      System.currentTimeMillis(),
      3600
    );
    resource.getContent().appendInt(2048);

    Buffer buffer = Buffer.buffer();
    resource.writeToBuffer(buffer);
    Resource recovered = new Resource();
    recovered.readFromBuffer(0, buffer);

    Assert.assertTrue(resourceEquals(resource, recovered));
  }

  @Test
  public void testEmpty() {
    Resource resource = new Resource(
      "http://www.example.com",
      200,
      "OK",
      MultiMap.caseInsensitiveMultiMap(),
      System.currentTimeMillis(),
      3600
    );

    Buffer buffer = Buffer.buffer(new byte[]{1, 1, 1, 1});
    resource.writeToBuffer(buffer);
    Resource recovered = new Resource();
    recovered.readFromBuffer(4, buffer);

    Assert.assertTrue(resourceEquals(resource, recovered));
  }

  @Test
  public void testNulls() {
    Resource resource = new Resource();

    Buffer buffer = Buffer.buffer();
    resource.writeToBuffer(buffer);
    Resource recovered = new Resource();
    recovered.readFromBuffer(0, buffer);

    Assert.assertTrue(resourceEquals(resource, recovered));
  }


}
