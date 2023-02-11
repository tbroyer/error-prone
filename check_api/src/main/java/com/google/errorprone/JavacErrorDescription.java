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

package com.google.errorprone;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.errorprone.fixes.AppliedFix;
import com.google.errorprone.matchers.Description;

/**
 * Public API for integration with {@link com.sun.tools.javac.util.Log.DiagnosticHandler diagnostic handlers}.
 */
@AutoValue
public abstract class JavacErrorDescription {
  static JavacErrorDescription of(Description description, ImmutableList<AppliedFix> appliedFixes) {
    return new AutoValue_JavacErrorDescription(description, appliedFixes);
  }

  public abstract Description description();
  public abstract ImmutableList<AppliedFix> appliedFixes();

  @Override
  public String toString() {
    StringBuilder messageBuilder = new StringBuilder(description().getMessage());
    boolean first = true;
    for (AppliedFix appliedFix : appliedFixes()) {
      if (first) {
        messageBuilder.append("\nDid you mean ");
      } else {
        messageBuilder.append(" or ");
      }
      if (appliedFix.isRemoveLine()) {
        messageBuilder.append("to remove this line");
      } else {
        messageBuilder.append("'").append(appliedFix.getNewCodeSnippet()).append("'");
      }
      first = false;
    }
    if (!first) { // appended at least one suggested fix to the message
      messageBuilder.append("?");
    }
    return messageBuilder.toString();
  }
}
