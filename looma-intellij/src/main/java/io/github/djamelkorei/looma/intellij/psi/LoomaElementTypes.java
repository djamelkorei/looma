package io.github.djamelkorei.looma.intellij.psi;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import io.github.djamelkorei.looma.intellij.lang.LoomaLanguage;

public interface LoomaElementTypes {

    IFileElementType FILE = new IFileElementType("LOOMA_FILE", LoomaLanguage.INSTANCE);
    /** An expression injected into a component attribute: {@code :count="items.size()"}. */
    IFileElementType EXPRESSION_FILE = new IFileElementType("LOOMA_EXPRESSION_FILE",
            io.github.djamelkorei.looma.intellij.injection.LoomaExpressionLanguage.INSTANCE);

    /** {@code {{ expr }}}. */
    IElementType ECHO = new LoomaElementType("ECHO");
    /** {@code {!! expr !!}}. */
    IElementType RAW_ECHO = new LoomaElementType("RAW_ECHO");
    /** {@code @name} or {@code @name(args)}. */
    IElementType DIRECTIVE = new LoomaElementType("DIRECTIVE");
    /** {@code (args)} of a directive. */
    IElementType ARGUMENTS = new LoomaElementType("ARGUMENTS");
    /** A block directive with its body, secondary tags and end tag: {@code @if ... @else ... @endif}. */
    IElementType BLOCK = new LoomaElementType("BLOCK");
    /** The content of a block between two of its tags. */
    IElementType BODY = new LoomaElementType("BODY");
    /** A variable read: {@code $user} or {@code user}. */
    IElementType VARIABLE = new LoomaElementType("VARIABLE");
    /** A variable introduced by a directive: {@code $post} in {@code @foreach($posts as $post)}. */
    IElementType VARIABLE_DECLARATION = new LoomaElementType("VARIABLE_DECLARATION");
    /** A property or method after {@code .}, {@code ->} or {@code ?.}. */
    IElementType MEMBER = new LoomaElementType("MEMBER");
    IElementType STRING_LITERAL = new LoomaElementType("STRING_LITERAL");
    /** {@code @priceService} in an expression. */
    IElementType BEAN = new LoomaElementType("BEAN_REFERENCE");
}
