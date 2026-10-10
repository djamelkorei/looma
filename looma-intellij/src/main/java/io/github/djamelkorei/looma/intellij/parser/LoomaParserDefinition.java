package io.github.djamelkorei.looma.intellij.parser;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import io.github.djamelkorei.looma.intellij.lang.LoomaProjectDirectives;
import io.github.djamelkorei.looma.intellij.lexer.LoomaLexer;
import io.github.djamelkorei.looma.intellij.lexer.LoomaTokenTypes;
import io.github.djamelkorei.looma.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public final class LoomaParserDefinition implements ParserDefinition {

    @Override
    public @NotNull Lexer createLexer(Project project) {
        return new LoomaLexer(LoomaProjectDirectives.get(project));
    }

    @Override
    public @NotNull PsiParser createParser(Project project) {
        return new LoomaParser(LoomaProjectDirectives.get(project));
    }

    @Override public @NotNull IFileElementType getFileNodeType() { return LoomaElementTypes.FILE; }
    @Override public @NotNull TokenSet getCommentTokens() { return LoomaTokenTypes.COMMENTS; }
    @Override public @NotNull TokenSet getStringLiteralElements() { return LoomaTokenTypes.STRINGS; }
    @Override public @NotNull TokenSet getWhitespaceTokens() { return LoomaTokenTypes.WHITESPACES; }

    @Override
    public @NotNull PsiElement createElement(ASTNode node) {
        IElementType type = node.getElementType();
        if (type == LoomaElementTypes.ECHO || type == LoomaElementTypes.RAW_ECHO) return new LoomaEcho(node);
        if (type == LoomaElementTypes.DIRECTIVE) return new LoomaDirective(node);
        if (type == LoomaElementTypes.ARGUMENTS) return new LoomaArguments(node);
        if (type == LoomaElementTypes.BLOCK) return new LoomaBlock(node);
        if (type == LoomaElementTypes.VARIABLE) return new LoomaVariable(node);
        if (type == LoomaElementTypes.VARIABLE_DECLARATION) return new LoomaVariableDeclaration(node);
        if (type == LoomaElementTypes.MEMBER) return new LoomaMember(node);
        if (type == LoomaElementTypes.STRING_LITERAL) return new LoomaStringLiteral(node);
        if (type == LoomaElementTypes.BEAN) return new LoomaBean(node);
        return new LoomaPsiElement(node);
    }

    @Override
    public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) {
        return new LoomaPsiFile(viewProvider);
    }
}
