package io.github.djamelkorei.looma.intellij.java;

import com.intellij.openapi.projectRoots.JavaSdk;
import com.intellij.psi.*;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.DefaultLightProjectDescriptor;
import io.github.djamelkorei.looma.intellij.reference.LoomaTemplateReference;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import io.github.djamelkorei.looma.intellij.lang.LoomaDirectives;
import io.github.djamelkorei.looma.intellij.lang.LoomaProjectDirectives;
import java.util.List;

public class LoomaJavaTest extends LightJavaCodeInsightFixtureTestCase {

    /** The real JDK: the bundled mock JDK may lack collections, which loop typing needs. */
    private static final LightProjectDescriptor REAL_JDK = new DefaultLightProjectDescriptor(
            () -> JavaSdk.getInstance().createJdk("real", System.getProperty("java.home"), false));

    @Override
    protected @NotNull LightProjectDescriptor getProjectDescriptor() {
        return REAL_JDK;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addClass("package org.springframework.stereotype; public @interface Controller {}");
        myFixture.addClass("package org.springframework.ui; public interface Model { Model addAttribute(String n, Object v); }");
        myFixture.addClass("package org.springframework.web.bind.annotation; public @interface ModelAttribute { String value() default \"\"; }");
        myFixture.addClass("package io.github.djamelkorei.looma.spi; public interface Directive { String name(); }");
        myFixture.addClass("package io.github.djamelkorei.looma.spi; public enum Syntax { NONE, OPTIONAL, REQUIRED }");
        myFixture.addClass("package io.github.djamelkorei.looma.spi; public interface TemplateHelper { String name(); }");
        myFixture.addClass("package app; public class User { public String getName() { return null; } public boolean isAdmin() { return false; } }");
        myFixture.addClass("package app; public record Post(String title, int likes) {}");
        myFixture.addClass("package app; public class SignupForm { public String getEmail() { return null; } }");
        myFixture.addClass("""
                package app;
                import org.springframework.stereotype.Controller;
                import org.springframework.ui.Model;
                import java.util.*;
                @Controller
                public class HomeController {
                    public String home(Model model) {
                        model.addAttribute("user", new User());
                        model.addAttribute("posts", new ArrayList<Post>());
                        model.addAttribute("scores", new HashMap<String, Integer>());
                        return "home";
                    }
                    public String signup(SignupForm form) { return "signup"; }
                    public String redirect() { return "redirect:/home"; }
                }
                """);
        myFixture.addFileToProject("templates/layouts/app.looma.html", "{{ $user->name }} @yield('content')");
        myFixture.addFileToProject("templates/signup.looma.html", "{{ $signupForm->email }}");
    }

    private PsiElement resolveInTemplate(String path, String text) {
        PsiFile file = myFixture.addFileToProject(path, text);
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull("no reference at caret", ref);
        return ref.resolve();
    }

    public void testControllerViewNameResolvesToTemplate() {
        PsiFile home = myFixture.addFileToProject("templates/home.looma.html", "x");
        myFixture.configureByText("C.java", """
                @org.springframework.stereotype.Controller
                class C { String a() { return "ho<caret>me"; } }
                """);
        PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull(ref);
        assertEquals(home, ref.resolve());
    }

    public void testRedirectIsNotAView() {
        myFixture.configureByText("C.java", """
                @org.springframework.stereotype.Controller
                class C { String a() { return "redirect:/ho<caret>me"; } }
                """);
        PsiElement literal = myFixture.getFile().findElementAt(myFixture.getCaretOffset()).getParent();
        assertFalse(Arrays.stream(literal.getReferences()).anyMatch(r -> r instanceof LoomaTemplateReference));
    }

    public void testModelAttributeResolvesToController() {
        PsiElement target = resolveInTemplate("templates/home.looma.html", "{{ $us<caret>er }}");
        assertTrue(String.valueOf(target), target instanceof PsiLiteralExpression);
        assertEquals("user", ((PsiLiteralExpression) target).getValue());
    }

    public void testPropertyResolvesToGetter() {
        PsiElement target = resolveInTemplate("templates/home.looma.html", "{{ $user->na<caret>me }}");
        assertTrue(String.valueOf(target), target instanceof PsiMethod);
        assertEquals("getName", ((PsiMethod) target).getName());
    }

    public void testLoopVariableHasElementType() {
        PsiElement target = resolveInTemplate("templates/home.looma.html",
                "@foreach($posts as $post) {{ $post->tit<caret>le }} @endforeach");
        assertTrue(String.valueOf(target), target instanceof PsiMethod);
        assertEquals("title", ((PsiMethod) target).getName());
    }

    public void testMapEntries() {
        PsiElement target = resolveInTemplate("templates/home.looma.html",
                "@foreach($scores as $e) {{ $e->va<caret>lue }} @endforeach");
        assertTrue(String.valueOf(target), target instanceof PsiMethod);
        assertEquals("getValue", ((PsiMethod) target).getName());
    }

    public void testMemberCompletion() {
        PsiFile file = myFixture.addFileToProject("templates/home.looma.html", "{{ $user-><caret> }}");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertTrue(items.toString(), items.containsAll(List.of("name", "admin", "getName")));
        assertFalse(items.contains("getClass"));
    }

    public void testModelVariableCompletion() {
        PsiFile file = myFixture.addFileToProject("templates/home.looma.html", "{{ $<caret> }}");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        myFixture.completeBasic();
        List<String> items = myFixture.getLookupElementStrings();
        assertNotNull(items);
        assertTrue(items.toString(), items.containsAll(List.of("user", "posts", "scores")));
    }

    public void testFormObjectIsAModelAttribute() {
        myFixture.configureFromExistingVirtualFile(myFixture.findFileInTempDir("templates/signup.looma.html"));
        PsiFile template = myFixture.getFile();
        assertTrue(LoomaJavaModel.attributes(template).containsKey("signupForm"));
    }

    public void testLayoutSeesChildModel() {
        myFixture.addFileToProject("templates/home.looma.html", "@extends('layouts.app')");
        PsiFile layout = getPsiManager().findFile(myFixture.findFileInTempDir("templates/layouts/app.looma.html"));
        assertTrue(LoomaJavaModel.attributes(layout).containsKey("user"));
    }

    public void testCustomDirectiveIsKnown() {
        myFixture.addClass("""
                package app;
                import io.github.djamelkorei.looma.spi.*;
                import java.util.Map;
                public class RepeatDirective implements Directive {
                    public String name() { return "repeat"; }
                    public Map<String, Syntax> tags() { return Map.of("endrepeat", Syntax.NONE); }
                }
                """);
        LoomaDirectives directives = LoomaProjectDirectives.get(getProject());
        assertEquals(LoomaDirectives.Syntax.REQUIRED, directives.syntax("repeat"));
        assertTrue(directives.directive("repeat").isBlock());
        myFixture.configureByText("t.looma.html", "@repeat(3) x @endrepeat");
        myFixture.checkHighlighting(false, false, false);
    }

    public void testHelperResolvesToClassAndMethod() {
        myFixture.addClass("""
                package app;
                public class PriceHelper implements io.github.djamelkorei.looma.spi.TemplateHelper {
                    public String name() { return "Price"; }
                    public String eur(Number n) { return null; }
                }
                """);
        PsiElement cls = resolveInTemplate("templates/p.looma.html", "{{ Pri<caret>ce.eur(1) }}");
        assertTrue(String.valueOf(cls), cls instanceof PsiClass);
        PsiElement method = resolveInTemplate("templates/q.looma.html", "{{ Price.e<caret>ur(1) }}");
        assertTrue(String.valueOf(method), method instanceof PsiMethod);
    }

    public void testEachGivesTheRowTemplateItsVariable() {
        myFixture.addFileToProject("templates/home.looma.html", "@each('partials.post', $posts, 'post')");
        PsiElement target = resolveInTemplate("templates/partials/post.looma.html", "{{ $post->tit<caret>le }}");
        assertTrue(String.valueOf(target), target instanceof PsiMethod);
        assertEquals("title", ((PsiMethod) target).getName());
    }
}
