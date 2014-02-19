/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi;

import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * A type which represents a function denoted by a lambda expression.
 */
public class PsiLambdaExpressionType extends PsiType {
  private final PsiLambdaExpression myExpression;

  public PsiLambdaExpressionType(PsiLambdaExpression expression) {
    super(PsiAnnotation.EMPTY_ARRAY);
    myExpression = expression;
  }

  @NotNull
  @Override
  public String getPresentableText() {
    return "<lambda expression>";
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return getPresentableText();
  }

  @NotNull
  @Override
  public String getInternalCanonicalText() {
    return getCanonicalText();
  }

  @Override
  public boolean isValid() {
    return myExpression.isValid();
  }

  @Override
  public boolean equalsToText(@NotNull @NonNls final String text) {
    return false;
  }

  @Override
  public <A> A accept(@NotNull final PsiTypeVisitor<A> visitor) {
    return visitor.visitLambdaExpressionType(this);
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return null;
  }

  @NotNull
  @Override
  public PsiType[] getSuperTypes() {
    return PsiType.EMPTY_ARRAY;
  }

  public PsiLambdaExpression getExpression() {
    return myExpression;
  }
}
