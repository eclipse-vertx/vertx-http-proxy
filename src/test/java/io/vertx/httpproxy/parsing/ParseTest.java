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
package io.vertx.httpproxy.parsing;

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
