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
package io.vertx.tests.impl;

import io.vertx.httpproxy.impl.CacheControl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class CacheControlTest {

  @Test
  public void testParseCacheControlMaxAge() {
    CacheControl control = new CacheControl();
    Assertions.assertEquals(123, control.parse("max-age=123").maxAge());
    Assertions.assertEquals(-1, control.parse("").maxAge());
  }

  @Test
  public void testParseCacheControlPublic() {
    CacheControl control = new CacheControl();
    Assertions.assertFalse(control.parse("max-age=123").isPublic());
    Assertions.assertTrue(control.parse("public").isPublic());
  }

  /*
   * Disabled since the initial import (2020-11-30). It calls ParseUtils.commaSplit, which no longer
   * exists in ParseUtils (checked with grep), so it cannot compile as is. It is kept as a reference for
   * the expected comma-splitting rules; it is not a CacheControl test and would belong in ParseUtilsTest
   * if the method came back.
   */
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
