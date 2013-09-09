package com.intellij.codeInsight;

import com.intellij.codeInsight.hint.ParameterInfoComponent;
import com.intellij.codeInsight.hint.api.impls.MethodParameterInfoHandler;
import com.intellij.lang.parameterInfo.CreateParameterInfoContext;
import com.intellij.lang.parameterInfo.ParameterInfoUIContextEx;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.testFramework.LightCodeInsightTestCase;
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext;
import com.intellij.util.Function;
import junit.framework.Assert;

public class ParameterInfoTest extends LightCodeInsightTestCase {

  private static final String BASE_PATH = "/codeInsight/parameterInfo/";

  private Object[] doTest(String paramsList) throws Exception {
    configureByFile(BASE_PATH + getTestName(false) + ".java");

    final MethodParameterInfoHandler handler = new MethodParameterInfoHandler();
    final CreateParameterInfoContext context = new MockCreateParameterInfoContext(myEditor, myFile);
    final PsiExpressionList list = handler.findElementForParameterInfo(context);
    assertNotNull(list);
    assertNotNull(context.getItemsToShow());
    assertTrue(context.getItemsToShow().length > 0);
    Object[] params = handler.getParametersForDocumentation(context.getItemsToShow()[0], context);
    assertNotNull(params);
    String joined = StringUtil.join(params, new Function<Object, String>() {
      @Override
      public String fun(Object o) {
        return ((PsiParameter)o).getName();
      }
    }, ",");
    assertEquals(paramsList, joined);
    return params;
  }

  public void testPrivateMethodOfEnclosingClass() throws Exception {
    doTest("param");
  }

  public void testNotAccessible() throws Exception {
    doTest("param");
  }

  public void testNoParams() throws Exception {
    doTestPresentation("<html>&lt;no parameters&gt;</html>");
  }

  public void testGenericsInsideCall() throws Exception {
    doTestPresentation("<html>List&lt;String&gt; param</html>");
  }

  public void testGenericsOutsideCall() throws Exception {
    doTestPresentation("<html>List&lt;String&gt; param</html>");
  }

  private void doTestPresentation(String expectedString) {
    configureByFile(BASE_PATH + getTestName(false) + ".java");

    final MethodParameterInfoHandler handler = new MethodParameterInfoHandler();
    final CreateParameterInfoContext context = new MockCreateParameterInfoContext(myEditor, myFile);
    final PsiExpressionList list = handler.findElementForParameterInfo(context);
    assertNotNull(list);
    final Object[] itemsToShow = context.getItemsToShow();
    assertNotNull(itemsToShow);
    assertTrue(itemsToShow.length == 1);
    assertTrue(itemsToShow[0] instanceof MethodCandidateInfo);
    final PsiMethod method = ((MethodCandidateInfo)itemsToShow[0]).getElement();
    final ParameterInfoUIContextEx parameterContext = ParameterInfoComponent.createContext(itemsToShow, myEditor, handler);
    Assert.assertEquals(expectedString,
                        MethodParameterInfoHandler
                          .updateMethodPresentation(method, ((MethodCandidateInfo)itemsToShow[0]).getSubstitutor(), parameterContext));
  }
}