/*
 * Copyright 2023 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.errorprone.sarif;

import static com.google.common.base.Verify.verify;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.io.Writer;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Specific, minimal JSON writer.
 *
 * <p>API is inspired by jakarta.json.stream.JsonGenerator.
 */
class JsonWriter {
  private enum Scope {
    OBJECT,
    ARRAY
  }

  // From RFC 8259: All Unicode characters may be placed within the
  //   quotation marks, except for the characters that MUST be escaped:
  //   quotation mark, reverse solidus, and the control characters (U+0000
  //   through U+001F).
  private static final CharMatcher NEEDS_ESCAPING =
      CharMatcher.anyOf("\"\\").or(CharMatcher.inRange('\0', '\u001f')).precomputed();

  // From RFC 8259: Alternatively, there are two-character sequence escape
  //   representations of some popular characters.
  // XXX: solidus doesn't make sense here as it doesn't require escaping.
  private static final ImmutableMap<Character, String> SPECIAL_ESCAPE_SEQUENCES =
      ImmutableMap.of(
          '"', "\\\"", // quotation mark
          '\\', "\\\\", // reverse solidus
          '\b', "\\b", // backspace
          '\f', "\\f", // form feed
          '\n', "\\n", // line feed
          '\r', "\\r", // carriage return
          '\t', "\\t" // tab
          );

  private final Writer writer;

  // Square's Moshi says such direct-array handling saves quite a bit.
  // Also, SARIF has a known max depth so the stack won't ever be resized.
  // Lastly, SARIF's outer object is not included in the stack.
  private int scopeIndex = -1;
  private final Scope[] scopes = new Scope[10]; // TODO: determine exact max depth

  /** Whether the insert a comma before a value / key-value pair. */
  boolean needsComma;

  public JsonWriter(Writer writer) throws IOException {
    this.writer = writer;
    // XXX: move to a static factory?
    writer.write("{\n");
  }

  public JsonWriter startObject(String name) throws IOException {
    checkScope(Scope.OBJECT);
    maybeEmitComma();
    emitString(name);
    writer.write(": ");
    open(Scope.OBJECT);
    return this;
  }

  public JsonWriter startObject() throws IOException {
    checkScope(Scope.ARRAY);
    maybeEmitComma();
    writer.write("[");
    open(Scope.OBJECT);
    return this;
  }

  public JsonWriter endObject() throws IOException {
    close(Scope.OBJECT);
    writer.write("}");
    return this;
  }

  public JsonWriter startArray(String name) throws IOException {
    checkScope(Scope.OBJECT);
    maybeEmitComma();
    emitString(name);
    writer.write(": ");
    open(Scope.ARRAY);
    return this;
  }

  public JsonWriter endArray() throws IOException {
    close(Scope.ARRAY);
    writer.write("]");
    return this;
  }

  public JsonWriter stringValue(String name, @Nullable String value) throws IOException {
    checkScope(Scope.OBJECT);
    maybeEmitComma();
    emitString(name);
    writer.write(": ");
    emitString(value);
    return this;
  }

  public JsonWriter stringValue(@Nullable String value) throws IOException {
    checkScope(Scope.ARRAY);
    maybeEmitComma();
    emitString(value);
    return this;
  }

  public JsonWriter booleanValue(String name, boolean value) throws IOException {
    return rawValue(name, Boolean.toString(value));
  }

  public JsonWriter numberValue(String name, int value) throws IOException {
    return rawValue(name, Integer.toString(value));
  }

  public JsonWriter numberValue(String name, long value) throws IOException {
    return rawValue(name, Long.toString(value));
  }

  private JsonWriter rawValue(String name, String value) throws IOException {
    checkScope(Scope.OBJECT);
    maybeEmitComma();
    emitString(name);
    writer.write(": ");
    writer.write(value);
    return this;
  }

  public void close() throws IOException {
    verify(scopeIndex == -1);
    // Close outer SARIF object
    writer.write("\n}\n");
    writer.close();
  }

  private void checkScope(Scope expectedScope) {
    verify(currentScope() == expectedScope);
  }

  private Scope currentScope() {
    if (scopeIndex < 0) {
      return Scope.OBJECT;
    }
    return scopes[scopeIndex];
  }

  private void open(Scope scope) {
    scopes[++scopeIndex] = scope;
    needsComma = false;
  }

  private void close(Scope expectedScope) {
    checkScope(expectedScope);
    scopeIndex--;
    needsComma = true;
  }

  private void maybeEmitComma() throws IOException {
    if (needsComma) {
      writer.write(", ");
    } else {
      needsComma = true;
    }
  }

  private void emitString(@Nullable String s) throws IOException {
    if (s == null) {
      writer.write("null");
      return;
    }
    writer.write('"');
    for (int prev = 0, next = NEEDS_ESCAPING.indexIn(s);
        prev < s.length();
        prev = next, next = NEEDS_ESCAPING.indexIn(s, next)) {
      if (next < 0) {
        writer.write(s, prev, s.length() - prev);
        break;
      }
      if (prev < next) {
        writer.write(s, prev, next - prev);
      }
      var matched = s.charAt(next++);
      var replacement = SPECIAL_ESCAPE_SEQUENCES.get(matched);
      if (replacement == null) {
        replacement = String.format("\\u%04x", (int) matched);
      }
      writer.write(replacement);
    }
    writer.write('"');
  }
}
