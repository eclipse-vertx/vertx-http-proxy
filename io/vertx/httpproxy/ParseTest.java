package io.vertx.httpproxy;

import io.vertx.httpproxy.impl.CacheControl;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class ParseTest {

  @Test
  public void testParseCacheControlMaxAge() {
    CacheControl control = new CacheControl();
    Assert.assertEquals(123, control.parse("max-age=123").maxAge());
    Assert.assertEquals(-1, control.parse("").maxAge());
  }

  @Test
  public void testParseCacheControlPublic() {
    CacheControl control = new CacheControl();
    Assert.assertFalse(control.parse("max-age=123").isPublic());
    Assert.assertTrue(control.parse("public").isPublic());
  }

  /*
  @Test
  public void testCommaSplit() {
    assertCommaSplit("foo", "foo");
    assertCommaSplit(" foo", "foo");
    assertCommaSplit("foo ", "foo");
    failCommaSplit("foo,", "foo");
    failCommaSplit(",foo");
    failCommaSplit("foo bar");
//    assertCommaSplit("foo,bar", "foo", "bar");
//    assertCommaSplit("foo ,bar", "foo", "bar");
//    assertCommaSplit("foo, bar", "foo", "bar");
//    assertCommaSplit("foo,bar ", "foo", "bar");
  }

  private void assertCommaSplit(String header, String... expected) {
    LinkedList<String> list = new LinkedList<>();
    ParseUtils.commaSplit(header, list::add);
    assertEquals(Arrays.asList(expected), list);
  }

  private void failCommaSplit(String header, String... expected) {
    LinkedList<String> list = new LinkedList<>();
    try {
      ParseUtils.commaSplit(header, list::add);
    } catch (IllegalStateException e) {
      assertEquals(Arrays.asList(expected), list);
      return;
    }
    fail();
  }
*/
}
