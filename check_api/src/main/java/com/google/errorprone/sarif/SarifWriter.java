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

import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.ErrorProneVersion;
import com.google.errorprone.apply.ImportOrganizer;
import com.google.errorprone.apply.ImportStatements;
import com.google.errorprone.fixes.Fix;
import com.google.errorprone.fixes.Replacement;
import com.google.errorprone.fixes.Replacements;
import com.google.errorprone.matchers.Description;
import com.sun.tools.javac.tree.JCTree.JCCompilationUnit;
import com.sun.tools.javac.util.DiagnosticSource;
import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Position;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.checkerframework.checker.nullness.qual.Nullable;

public class SarifWriter {
  private final JsonWriter writer;
  private final Locale locale;
  private final ImportOrganizer importOrganizer;

  public SarifWriter(
      String sarifOutputFile,
      Locale locale,
      @Nullable String encoding,
      ImportOrganizer importOrganizer)
      throws IOException {
    // XXX: move some of this to a static factory?
    if (encoding == null || encoding.isEmpty()) {
      encoding = Charset.defaultCharset().name();
    }
    this.importOrganizer = importOrganizer;
    this.locale = locale;
    this.writer =
        new JsonWriter(new BufferedWriter(new FileWriter(sarifOutputFile, StandardCharsets.UTF_8)));
    writer.stringValue("version", "2.1.0");
    writer.stringValue(
        "$schema",
        "https://raw.githubusercontent.com/oasis-tcs/sarif-spec/master/Schemata/sarif-schema-2.1.0.json");
    writer.startArray("runs");
    writer.startObject();
    writer
        .stringValue("defaultFileEncoding", encoding)
        .stringValue("columnKind", "utf16CodeUnits")
        .startObject("tool")
        .startObject("driver")
        .stringValue("name", "ErrorProne")
        .stringValue("version", ErrorProneVersion.loadVersionFromPom().or("unknown version"))
        // XXX: find a way to list all enabled checks in "rules"?
        .endObject() // driver
        .endObject(); // tool
    writer.startArray("results");
  }

  public void emitResult(
      Description description, DiagnosticSource source, JCCompilationUnit compilationUnit)
      throws IOException {
    writer
        .startObject()
        .stringValue("ruleId", description.checkName)
        .stringValue("level", severityToLevel(description.severity()))
        .startObject("message")
        .stringValue("text", description.getRawMessage())
        .endObject() // message
        .startArray("locations")
        .startObject()
        .startObject("physicalLocation")
        .startObject("artifactLocation")
        .stringValue("uri", source.getFile().toUri().toString())
        .endObject() // artifactLocation
        // XXX: what if start == end? (can it happen?)
        // XXX: handle the case where there's no position (can it even happen?)
        // XXX: factorize getStartPosition and getEndPosition
        .startObject("region")
        .numberValue("startLine", source.getLineNumber(description.position.getStartPosition()))
        .numberValue(
            "startColumn", source.getColumnNumber(description.position.getStartPosition(), false))
        .numberValue(
            "endLine",
            source.getLineNumber(description.position.getEndPosition(source.getEndPosTable())))
        .numberValue(
            "endColumn",
            source.getColumnNumber(
                description.position.getEndPosition(source.getEndPosTable()), false))
        .numberValue("charOffset", description.position.getStartPosition())
        .numberValue(
            "charLength",
            description.position.getEndPosition(source.getEndPosTable())
                - description.position.getStartPosition())
        .endObject() // region
        .endObject() // physicalLocation
        .endObject() // location object
        .endArray(); // locations
    if (!description.fixes.isEmpty()) {
      writer.startArray("fixes");
      for (Fix fix : description.fixes) {
        writer.startObject();
        if (fix.getShortDescription().isEmpty()) {
          writer
              .startObject("description")
              .stringValue("text", fix.getShortDescription())
              .endObject();
        }
        writer.startArray("artifactChanges");
        var replacements = new Replacements();
        fix.getReplacements(source.getEndPosTable()).forEach(replacements::add);
        if (!fix.getImportsToAdd().isEmpty() || !fix.getImportsToRemove().isEmpty()) {
          ImportStatements importStatements =
              ImportStatements.create(compilationUnit, importOrganizer);
          importStatements.addAll(fix.getImportsToAdd());
          importStatements.removeAll(fix.getImportsToRemove());
          if (importStatements.importsHaveChanged()) {
            replacements.add(
                Replacement.create(
                    importStatements.getStartPos(),
                    importStatements.getEndPos(),
                    importStatements.toString()),
                Replacements.CoalescePolicy.REPLACEMENT_FIRST);
          }
        }
        for (Replacement replacement : replacements.ascending()) {
          writer
              .startObject()
              .startObject("artifactLocation")
              .stringValue("uri", source.getFile().toUri().toString())
              .endObject() // artifactLocation
              .startArray("replacements")
              .startObject()
              .startObject("deletedRegion")
              .numberValue("startLine", source.getLineNumber(replacement.startPosition()))
              .numberValue(
                  "startColumn", source.getColumnNumber(replacement.startPosition(), false))
              .numberValue("endLine", source.getLineNumber(replacement.endPosition()))
              .numberValue("endColumn", source.getColumnNumber(replacement.endPosition(), false))
              .numberValue("charOffset", replacement.startPosition())
              .numberValue("charLength", replacement.length())
              .endObject(); // deletedRegion
          if (!replacement.replaceWith().isEmpty()) {
            writer
                .startObject("insertedContent")
                .stringValue("text", replacement.replaceWith())
                .endObject(); // insertedContent
          }
          writer
              .endObject() // replacement object
              .endArray() // replacements
              .endObject();
        }
        writer
            .endArray() // artifactChanges
            .endObject(); // fix object
      }
      writer.endArray(); // fixes
    }
    writer.endObject(); // result object
  }

  private String severityToLevel(SeverityLevel severity) {
    switch (severity) {
      case ERROR:
        return "error";
      case WARNING:
        return "warning";
      case SUGGESTION:
        return "note";
      default:
        return "none";
    }
  }

  public void emitResult(JCDiagnostic diagnostic) throws IOException {
    writer
        .startObject()
        .stringValue("ruleId", diagnostic.getLintCategory().option)
        .stringValue("level", diagnosticTypeToLevel(diagnostic.getType()))
        .startObject("message")
        .stringValue("text", diagnostic.getMessage(locale))
        .endObject(); // message
    if (diagnostic.getSource() != null) {
      writer
          .startArray("locations")
          .startObject()
          .startObject("physicalLocation")
          .startObject("artifactLocation")
          .stringValue("uri", diagnostic.getSource().toUri().toString())
          .endObject(); // artifactLocation
      if (diagnostic.getDiagnosticPosition() != null
          && diagnostic.getDiagnosticPosition().getPreferredPosition() != Position.NOPOS) {
        // XXX: what if start == end? (can it happen?)
        // XXX: factorize getStartPosition and getEndPosition
        writer
            .startObject("region")
            .numberValue(
                "startLine",
                diagnostic
                    .getDiagnosticSource()
                    .getLineNumber(diagnostic.getDiagnosticPosition().getStartPosition()))
            .numberValue(
                "startColumn",
                diagnostic
                    .getDiagnosticSource()
                    .getColumnNumber(diagnostic.getDiagnosticPosition().getStartPosition(), false))
            .numberValue(
                "endLine",
                diagnostic
                    .getDiagnosticSource()
                    .getLineNumber(
                        diagnostic
                            .getDiagnosticPosition()
                            .getEndPosition(diagnostic.getDiagnosticSource().getEndPosTable())))
            .numberValue(
                "endColumn",
                diagnostic
                    .getDiagnosticSource()
                    .getColumnNumber(
                        diagnostic
                            .getDiagnosticPosition()
                            .getEndPosition(diagnostic.getDiagnosticSource().getEndPosTable()),
                        false))
            .numberValue("charOffset", diagnostic.getStartPosition())
            .numberValue("charLength", diagnostic.getEndPosition() - diagnostic.getStartPosition())
            .endObject(); // region
      }
      writer
          .endObject() // physicalLocation
          .endObject() // location object
          .endArray(); // locations
    }
    writer.endObject(); // result object
  }

  private String diagnosticTypeToLevel(JCDiagnostic.DiagnosticType type) {
    switch (type) {
      case ERROR:
        return "error";
      case WARNING:
        return "warning";
      case NOTE:
        return "note";
      default:
        return "none";
    }
  }

  public void close() throws IOException {
    writer.endArray(); // end "results"
    writer.endObject(); // end run object
    writer.endArray(); // end "runs"
    writer.close();
  }
}
