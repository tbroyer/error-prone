/*
 * Copyright 2020 The Error Prone Authors.
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

package com.google.errorprone.refaster;

import com.google.errorprone.refaster.PlaceholderUnificationVisitor.State;
import com.sun.source.tree.CaseTree;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Type.TypeVar;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCCase;

/**
 * Contains methods that need to be implemented differently in other JDK versions.
 *
 * <p>Those implementations are provided by specific copies of this class that will eventually to
 * packaged in a Multi-Release JAR.
 */
class JdkCompatHelper {
  static void setUpperBound(TypeVar typeVar, Type bound) {
    // This field has been renamed and made private in JDK 13 in
    // https://github.com/openjdk/jdk/commit/777ad9080e8a35229fe5110a3f1dbc6cc7e95a1d
    // where it's replaced with a setUpperBound setter.
    typeVar.bound = bound;
  }

  static Choice<State<JCCase>> unifyCase(
      PlaceholderUnificationVisitor visitor, final CaseTree node, State<?> state) {
    // JDK 12 introduces Switch Expressions in
    // https://github.com/openjdk/jdk/commit/b3b644438e9e4d30a062e509d939d4b8e197a389
    return PlaceholderUnificationVisitor.chooseSubtrees(
        state,
        s -> visitor.unifyStatements(node.getStatements(), s),
        stmts -> visitor.maker().Case((JCTree.JCExpression) node.getExpression(), stmts));
  }

  private JdkCompatHelper() {}
}
