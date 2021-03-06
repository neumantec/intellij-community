/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.compiler.chainsSearch.completion.lookup;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.completion.PreferByKindWeigher;
import com.intellij.codeInsight.lookup.AutoCompletionPolicy;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.compiler.chainsSearch.context.ChainCompletionContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.stream.Stream;

public class ChainCompletionNewVariableLookupElement extends LookupElement {
  @NotNull
  private final PsiClass myQualifierClass;
  @NotNull
  private final String myNewVarName;
  private final boolean myField;

  public ChainCompletionNewVariableLookupElement(@NotNull PsiClass qualifierClass,
                                                 @NotNull ChainCompletionContext context) {
    Project project = qualifierClass.getProject();
    JavaCodeStyleManager codeStyleManager = JavaCodeStyleManager.getInstance(project);
    PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    myField = PsiTreeUtil.getParentOfType(context.getContextPsi(), PsiStatement.class, false) == null;
    VariableKind variableKind = myField ? VariableKind.FIELD : VariableKind.LOCAL_VARIABLE;
    myNewVarName = Stream
      .of(codeStyleManager.suggestVariableName(variableKind, null, null, elementFactory.createType(qualifierClass)).names)
      .max(Comparator.comparing(String::length))
      .orElseThrow(IllegalStateException::new);
    myQualifierClass = qualifierClass;
    putUserData(PreferByKindWeigher.INTRODUCED_VARIABLE, Boolean.TRUE);
  }

  @Override
  public AutoCompletionPolicy getAutoCompletionPolicy() {
    return AutoCompletionPolicy.NEVER_AUTOCOMPLETE;
  }

  @Override
  public void handleInsert(final InsertionContext context) {
    final PsiFile file = context.getFile();
    final PsiElement caretElement = ObjectUtils.notNull(file.findElementAt(context.getEditor().getCaretModel().getOffset()));
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(context.getProject());

    PsiElement newVariablePlacementAnchor;
    PsiElement newVarDeclarationTemplate;
    if (myField) {
      PsiField field = ObjectUtils.notNull(PsiTreeUtil.getParentOfType(caretElement.getPrevSibling(), PsiField.class, false));
      newVariablePlacementAnchor = field;
      PsiField newField = elementFactory.createField(myNewVarName, elementFactory.createType(myQualifierClass));
      if (field.hasModifierProperty(PsiModifier.STATIC)) {
        PsiUtil.setModifierProperty(newField, PsiModifier.STATIC, true);
      }
      newVarDeclarationTemplate = newField;
    } else {
      newVariablePlacementAnchor = ObjectUtils.notNull(PsiTreeUtil.getParentOfType(caretElement.getPrevSibling(), PsiStatement.class, false));
      newVarDeclarationTemplate = elementFactory.createVariableDeclarationStatement(myNewVarName,
                                                                                    elementFactory.createType(myQualifierClass),
                                                                                    elementFactory.createExpressionFromText(PsiKeyword.NULL, null));

    }

    PsiElement varDeclaration = newVariablePlacementAnchor.getParent().addBefore(newVarDeclarationTemplate, newVariablePlacementAnchor);
    JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(varDeclaration);
  }


  @NotNull
  @Override
  public String getLookupString() {
    return myNewVarName;
  }
}
