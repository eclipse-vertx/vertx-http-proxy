package io.vertx.httpproxy.impl;

import io.vertx.httpproxy.MediaType;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * Implements MediaType
 *
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
public class MediaTypeImpl implements MediaType, BiConsumer<String, ParameterValue> {

  public static List<MediaType> parseCommaSeparatedList(String s, int pos) {
    // #rule (https://datatracker.ietf.org/doc/html/rfc7230#section-7)
    List<MediaType> list = new ArrayList<>();
    while (true) {
      MediaTypeImpl mediaType = parseMediaType(s, pos);
      if (mediaType == null) {
        throw new IllegalArgumentException("Invalid value: " + s);
      }
      list.add(mediaType);
      pos = mediaType.pos;
      mediaType.pos = 0;
      pos = parseOWS(s, pos);
      if (pos == s.length() || s.charAt(pos) != ',') {
        break;
      }
      pos = parseOWS(s, pos + 1);
    }
    return list;
  }

  // Parsing, trailer chars are ignored
  public static MediaTypeImpl parseMediaType(String s, int pos) {
    int idx1 = parseToken(s, pos);
    if (idx1 > pos) {
      if (idx1 < s.length() && s.charAt(idx1) == '/') {
        int idx2 = parseToken(s, idx1 + 1);
        if (idx2 > idx1 + 1) {
          String type = s.substring(pos, idx1);
          String subType = s.substring(idx1 + 1, idx2);
          MediaTypeImpl mt = new MediaTypeImpl(type, subType);
          int idx3 = idx2;
          while (true) {
            idx3 = parseOWS(s, idx3);
            if (idx3 < s.length() && s.charAt(idx3) == ';') {
              int idx4 = parseOWS(s, idx3 + 1);
              idx3 = parseParameter(s, idx4, mt);
            } else {
              mt.pos = idx3;
              break;
            }
          }
          return mt;
        }
      }
    }
    return null;
  }

  public static int parseParameter(String s, int pos) {
    return parseParameter(s, pos, (name, value) -> {});
  }

  private static int parseParameter(String s, int pos, BiConsumer<String, ParameterValue> handler) {
    int idx1 = parseToken(s, pos);
    if (idx1 > pos) {
      if (idx1 < s.length() && s.charAt(idx1) == '=') {
        int idx2 = parseToken(s, idx1 + 1);
        if (idx2 > idx1 + 1) {
          handler.accept(s.substring(pos, idx1), new ParameterValue(s.substring(idx1 + 1, idx2), false));
          return idx2;
        }
        idx2 = parseQuotedString(s, idx1 + 1);
        if (idx2 > idx1 + 1) {
          handler.accept(s.substring(pos, idx1), new ParameterValue(s.substring(idx1 + 2, idx2 - 1), true));
          return idx2;
        }
      }
    }
    return pos;
  }

  public static int parseQuotedString(String s, int pos) {
    if (pos < s.length() && isDQUOTE(s.charAt(pos))) {
      int idx = pos + 1;
      while (idx < s.length()) {
        char c = s.charAt(idx);
        if (isQdtext(c)) {
          idx++;
        } else if (c == '\\') {
          // Quoted pair
          if (idx + 1 < s.length() && ((c = s.charAt(idx + 1)) == '\t' | c == ' ' | isVCHAR(c) )) {
            idx += 2;
          } else {
            break;
          }
        } else {
          break;
        }
      }
      if (idx < s.length() && isDQUOTE(s.charAt(idx))) {
        return idx + 1;
      }
    }
    return pos;
  }

  public static boolean isVCHAR(char c) {
    return c >= 0x21 && c <= 0x7E;
  }

  public static boolean isQdtext(char c) {
    return c == '\t' | c == ' ' | c == 0x21 | (c >= 0x23 && c <= 0x5B) | (c >= 0x5D && c <= 0x7E) | isObsText(c);
  }

  public static boolean isObsText(char c) {
    return (c >= 0x80 && c <= 0xFF);
  }

  public static boolean isDQUOTE(char c) {
    return c == '"';
  }

  public static int parseToken(String s, int pos) {
    int len = s.length();
    while (pos < len) {
      if (isTchar(s.charAt(pos))) {
        pos++;
      } else {
        break;
      }
    }
    return pos;
  }

  private static boolean isTchar(char c) {
    return isALPHA(c) | isDIGIT(c) | c == '!' | c == '#' | c == '$' | c == '%' | c == '&' | c == '\\' | c == '*'
      | c == '+' | c == '-' | c == '.' | c == '^' | c == '_' | c == '`' | c == '|' | c == '~';
  }

  private static boolean isALPHA(char ch) {
    return ('A' <= ch && ch <= 'Z')
      || ('a'<= ch && ch <= 'z');
  }

  private static boolean isDIGIT(char ch) {
    return ('0' <= ch && ch <= '9');
  }

  private static int parseOWS(String s, int pos) {
    int length = s.length();
    while (pos < length && isOWS(s.charAt(pos))) {
      pos++;
    }
    return pos;
  }

  private static boolean isOWS(char c) {
    return c == ' ' | c == '\t';
  }

  private final String type;
  private final String subType;
  private Map<String, ParameterValue> parameters;
  private String string;
  private int pos;

  public MediaTypeImpl(String type, String subType) {
    if (type == null && subType != null) {
      throw new IllegalArgumentException();
    }
    this.type = "*".equals(type) ? null : type;
    this.subType = "*".equals(subType) ? null : subType;
  }

  /**
   * @return the type or {@code null} when everything is matched (*)
   */
  public String type() {
    return type;
  }

  /**
   * @return the sub type or {@code null} when everything is matched (*)
   */
  public String subType() {
    return subType;
  }

  /**
   * @return whether the {@code other} mime type is accepted
   */
  public boolean accepts(MediaType other) {
    if (other == null) {
      return false;
    }
    if (type == null) {
      return true;
    } else {
      if (type.equals(other.type())) {
        if (subType == null) {
          return true;
        } else {
          return subType.equals(other.subType());
        }
      } else {
        return false;
      }
    }
  }

  @Override
  public String parameter(String name) {
    ParameterValue value;
    return parameters == null ? null : (value = parameters.get(name)) == null ? null : value.value;
  }

  @Override
  public void accept(String name, ParameterValue value) {
    if (parameters == null) {
      parameters = new HashMap<>();
    }
    parameters.put(name, value);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj instanceof MediaTypeImpl) {
      MediaTypeImpl that = (MediaTypeImpl) obj;
      if (Objects.equals(type, that.type) && Objects.equals(subType, that.subType)) {
        if (parameters != null) {
          if (that.parameters != null && parameters.keySet().equals(that.parameters.keySet())) {
            for (String parameterName : parameters.keySet()) {
              if (!parameters.get(parameterName).value.equals(that.parameters.get(parameterName).value)) {
                return false;
              }
            }
            return true;
          }
        } else {
          return that.parameters == null;
        }
      }
    }
    return false;
  }

  @Override
  public String toString() {
    String ret = string;
    if (ret == null) {
      StringBuilder sb = new StringBuilder();
      sb.append(type == null ? "*" : type);
      sb.append('/');
      sb.append(subType == null ? "*" : subType);
      if (parameters != null) {
        for (Map.Entry<String, ParameterValue> parameter : parameters.entrySet()) {
          sb.append(';').append(parameter.getKey()).append('=');
          ParameterValue value = parameter.getValue();
          if (value.quoted) {
            sb.append('"');
            sb.append(value.value);
            sb.append('"');
          } else {
            sb.append(value.value);
          }
        }
      }
      ret = sb.toString();
      string = ret;
    }
    return ret;
  }
}
