package io.github.djamelkorei.looma.intellij.java;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.RecursionManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import io.github.djamelkorei.looma.intellij.lexer.LoomaTokenTypes;
import io.github.djamelkorei.looma.intellij.psi.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Java types of template expressions: model attributes have the type of the value put into the model, loop
 * variables the element type of what they iterate, members the type of the getter, field or method.
 */
public final class LoomaJavaTypes {

    public static final String LOOP = "io.github.djamelkorei.looma.directive.Loop";

    /** A property or method of a type, with the type it yields. */
    public record Member(String name, PsiMember element, @Nullable PsiType type, boolean method) {}

    private LoomaJavaTypes() {}

    /** The type of a variable, declaration or member, or null if unknown. */
    public static @Nullable PsiType typeOf(@Nullable PsiElement e) {
        if (!(e instanceof LoomaVariable) && !(e instanceof LoomaVariableDeclaration) && !(e instanceof LoomaMember)) {
            return null;
        }
        return CachedValuesManager.getCachedValue(e, () -> CachedValueProvider.Result.create(
                RecursionManager.doPreventingRecursion(e, true, () -> compute(e)), PsiModificationTracker.MODIFICATION_COUNT));
    }

    private static @Nullable PsiType compute(PsiElement e) {
        if (e instanceof LoomaMember member) {
            PsiType qualifier = typeOf(member.getQualifier());
            Member m = qualifier == null ? null : member(qualifier, member.getName(), member.isCall(), member);
            return m == null ? null : m.type();
        }
        if (e instanceof LoomaVariableDeclaration d) return declaredType(d);
        LoomaVariable v = (LoomaVariable) e;
        PsiElement local = LoomaScopes.resolve(v, v.getName());
        if (local instanceof LoomaVariableDeclaration d) return typeOf(d);
        Project project = v.getProject();
        if (local instanceof LoomaDirective) return classType(project, LOOP, v);
        PsiFile file = LoomaFiles.template(v);
        LoomaJavaModel.Attribute attribute = file == null ? null : LoomaJavaModel.attributes(file).get(v.getName());
        if (attribute != null) return attribute.type();
        PsiClass helper = LoomaJavaHelpers.helper(project, v.getName());
        return helper == null ? null : JavaPsiFacade.getElementFactory(project).createType(helper);
    }

    /** {@code $post} in {@code @foreach($posts as $post)}: the element type of {@code $posts}. */
    private static @Nullable PsiType declaredType(LoomaVariableDeclaration d) {
        LoomaDirective directive = d.getDirective();
        if (directive.getName().equals("for")) return PsiTypes.intType();
        PsiElement before = d.getPrevSibling();
        while (before != null && before.getNode().getElementType() == LoomaTokenTypes.WHITE_SPACE) before = before.getPrevSibling();
        if (before == null || before.getNode().getElementType() != LoomaTokenTypes.KEYWORD) return null;
        PsiElement iterable = before.getPrevSibling();
        while (iterable != null && iterable.getNode().getElementType() == LoomaTokenTypes.WHITE_SPACE) iterable = iterable.getPrevSibling();
        PsiType type = typeOf(iterable);
        return type == null ? null : elementType(type, d);
    }

    /** What a loop over {@code type} yields: array and {@code Iterable} elements, {@code Map.Entry} for maps. */
    public static @Nullable PsiType elementType(@NotNull PsiType type, @NotNull PsiElement context) {
        if (type instanceof PsiArrayType array) return array.getComponentType();
        if (InheritanceUtil.isInheritor(type, "java.util.Map")) {
            PsiType key = PsiUtil.substituteTypeParameter(type, "java.util.Map", 0, false);
            PsiType value = PsiUtil.substituteTypeParameter(type, "java.util.Map", 1, false);
            String text = "java.util.Map.Entry<" + (key == null ? "java.lang.Object" : key.getCanonicalText()) + ", "
                    + (value == null ? "java.lang.Object" : value.getCanonicalText()) + ">";
            try {
                return JavaPsiFacade.getElementFactory(context.getProject()).createTypeFromText(text, null);
            } catch (com.intellij.util.IncorrectOperationException ex) {
                return null;
            }
        }
        return PsiUtil.extractIterableTypeParameter(type, false);
    }

    /** The member {@code name} of {@code type}: a method if {@code call}, else a property (getter, accessor or field). */
    public static @Nullable Member member(@NotNull PsiType type, @NotNull String name, boolean call, PsiElement context) {
        for (Member m : members(type, context)) {
            if (m.name().equals(name) && m.method() == call) return m;
        }
        return null;
    }

    /** The properties and methods templates can use on {@code type}. */
    public static @NotNull List<Member> members(@NotNull PsiType type, PsiElement context) {
        if (type instanceof PsiPrimitiveType primitive) {
            PsiClassType boxed = primitive.getBoxedType(context);
            if (boxed == null) return List.of();
            type = boxed;
        }
        if (!(type instanceof PsiClassType classType)) return List.of();
        PsiClassType.ClassResolveResult resolved = classType.resolveGenerics();
        PsiClass cls = resolved.getElement();
        if (cls == null) return List.of();
        Map<String, Member> properties = new LinkedHashMap<>();
        Map<String, Member> methods = new LinkedHashMap<>();
        for (PsiMethod m : cls.getAllMethods()) {
            PsiClass owner = m.getContainingClass();
            if (owner == null || "java.lang.Object".equals(owner.getQualifiedName()) || m.isConstructor()
                    || !m.hasModifierProperty(PsiModifier.PUBLIC) || m.hasModifierProperty(PsiModifier.STATIC)) {
                continue;
            }
            PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(owner, cls, resolved.getSubstitutor());
            PsiType returnType = m.getReturnType() == null ? null : substitutor.substitute(m.getReturnType());
            methods.putIfAbsent(m.getName(), new Member(m.getName(), m, returnType, true));
            String property = propertyName(m, cls);
            if (property != null) properties.putIfAbsent(property, new Member(property, m, returnType, false));
        }
        for (PsiField f : cls.getAllFields()) {
            if (f.hasModifierProperty(PsiModifier.PUBLIC) && !f.hasModifierProperty(PsiModifier.STATIC)) {
                PsiClass owner = f.getContainingClass();
                PsiSubstitutor substitutor = owner == null ? PsiSubstitutor.EMPTY
                        : TypeConversionUtil.getSuperClassSubstitutor(owner, cls, resolved.getSubstitutor());
                properties.putIfAbsent(f.getName(), new Member(f.getName(), f, substitutor.substitute(f.getType()), false));
            }
        }
        List<Member> out = new ArrayList<>(properties.values());
        out.addAll(methods.values());
        return out;
    }

    /** {@code getTitle()} and {@code isDone()} are properties, as are a record's accessors. */
    private static @Nullable String propertyName(PsiMethod m, PsiClass cls) {
        if (!m.getParameterList().isEmpty() || PsiTypes.voidType().equals(m.getReturnType())) return null;
        String name = m.getName();
        if (name.equals("getClass")) return null;
        if (name.startsWith("get") && name.length() > 3 && Character.isUpperCase(name.charAt(3))) return decapitalize(name.substring(3));
        if (name.startsWith("is") && name.length() > 2 && Character.isUpperCase(name.charAt(2))
                && PsiTypes.booleanType().equals(m.getReturnType())) {
            return decapitalize(name.substring(2));
        }
        PsiClass owner = m.getContainingClass();
        if (owner != null && owner.isRecord()) {
            for (PsiRecordComponent c : owner.getRecordComponents()) if (c.getName().equals(name)) return name;
        }
        return null;
    }

    private static String decapitalize(String s) {
        if (s.length() > 1 && Character.isUpperCase(s.charAt(1))) return s;
        return Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    private static @Nullable PsiType classType(Project project, String fqn, PsiElement context) {
        PsiClass cls = JavaPsiFacade.getInstance(project).findClass(fqn, GlobalSearchScope.allScope(project));
        return cls == null ? null : JavaPsiFacade.getElementFactory(project).createType(cls);
    }
}
