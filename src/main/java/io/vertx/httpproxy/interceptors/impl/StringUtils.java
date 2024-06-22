package io.vertx.httpproxy.interceptors.impl;

import io.vertx.httpproxy.ProxyContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class StringUtils {

  static String[] splitAndKeep(String str) {
    ArrayList<String> parts = new ArrayList<>();
    Pattern pattern = Pattern.compile("/");
    Matcher matcher = pattern.matcher(str);

    int lastIndex = 0;
    while (matcher.find()) {
      if (matcher.start() > lastIndex) {
        parts.add(str.substring(lastIndex, matcher.start()));
      }
      parts.add(matcher.group());
      lastIndex = matcher.end();
    }
    if (lastIndex < str.length()) {
      parts.add(str.substring(lastIndex));
    }
    return parts.toArray(new String[0]);
  }

  static boolean matchURL(String raw, String pattern) {
    String[] rawArr = splitAndKeep(raw);
    String[] patternArr = splitAndKeep(pattern);
    if (rawArr.length != patternArr.length) return false;

    for (int i = 0; i < rawArr.length; i++) {
      String rawPiece = rawArr[i];
      String patternPiece = patternArr[i];
      if ("/".equals(rawPiece) && !"/".equals(patternPiece)) return false;
      if (!"/".equals(rawPiece) && "/".equals(patternPiece)) return false;
      if (!patternPiece.startsWith("$") && !rawPiece.equals(patternPiece)) return false;
    }
    return true;
  }

  static void fillContextWithURL(String raw, String pattern, ProxyContext proxyContext) {
    String[] rawArr = splitAndKeep(raw);
    String[] patternArr = splitAndKeep(pattern);

    for (int i = 0; i < rawArr.length; i++) {
      String rawPiece = rawArr[i];
      String patternPiece = patternArr[i];
      if (patternPiece.startsWith("$")) {
        proxyContext.set(patternPiece.substring(1), rawPiece);
      }
    }
  }

  static String transformURL(String pattern, ProxyContext proxyContext) {
    String[] patternArr = splitAndKeep(pattern);
    for (int i = 0; i < patternArr.length; i++) {
      patternArr[i] = substitute(patternArr[i], proxyContext);
    }
    return String.join("", patternArr);
  }

  static String substitute(String pattern, ProxyContext proxyContext) {
    if (pattern.startsWith("$")) {
      Object value = proxyContext.get(pattern.substring(1), Object.class);
      if (value == null) return pattern;
      return value.toString();
    }
    return pattern;
  }
}
















