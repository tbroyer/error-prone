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

import com.sun.tools.javac.util.JCDiagnostic;
import com.sun.tools.javac.util.Log;
import java.io.IOException;
import java.io.UncheckedIOException;

public class SarifDiagnosticHandler extends Log.DiagnosticHandler {
  private final SarifWriter sarifWriter;

  public SarifDiagnosticHandler(Log log, SarifWriter sarifWriter) {
    this.sarifWriter = sarifWriter;
    install(log);
  }

  @Override
  public void report(JCDiagnostic diag) {
    switch (diag.getCode()) {
      case "compiler.err.error.prone":
      case "compiler.warn.error.prone":
      case "compiler.note.error.prone":
        // those are emitted by JavacErrorDescriptionListener;
        // SARIF reporting should be handled there, so ignore them here.
        break;
      default:
        try {
          sarifWriter.emitResult(diag);
        } catch (IOException ioe) {
          // FIXME
          ioe.printStackTrace();
          throw new UncheckedIOException(ioe);
        }
    }
    prev.report(diag);
  }
}
