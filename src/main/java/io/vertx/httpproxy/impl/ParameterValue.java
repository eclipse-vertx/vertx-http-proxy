package io.vertx.httpproxy.impl;

class ParameterValue {
  final String value;
  final boolean quoted;

  ParameterValue(String value, boolean quoted) {
    this.value = value;
    this.quoted = quoted;
  }
}
