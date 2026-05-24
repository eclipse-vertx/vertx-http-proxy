package io.vertx.httpproxy.impl;

/**
 * Math calculation should avoid overflow and represents values as the greatest positive integer.
 * Refers to "RFC-9111 1.2.2 Delta Seconds".
 */
public class SafeMathUtils {

  public static int safeAdd(int a, int b) {
    if (b > 0 ? a > Integer.MAX_VALUE - b : a < Integer.MIN_VALUE - b) {
      return b > 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
    } else {
      return a + b;
    }
  }

  public static int safeSubtract(int a, int b) {
    if (b > 0 ? a < Integer.MIN_VALUE + b : a > Integer.MAX_VALUE + b) {
      return b > 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE;
    } else {
      return a - b;
    }
  }

  public static int safeMultiply(int a, int b) {
    if (a > 0 ? (b > Integer.MAX_VALUE / a || b < Integer.MIN_VALUE / a)
      : (a < Integer.MIN_VALUE / b || a > Integer.MAX_VALUE / b)) {
      return a > 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
    } else {
      return a * b;
    }
  }

  public int safeDivide(int a, int b) {
    return a / b;
  }

  public int castInt(long v) {
    if (v > 0) {
      return v > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) v;
    } else {
      return v < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) v;
    }
  }
}
