package io.github.djamelkorei.looma.intellij.injection;

import com.intellij.lang.ASTNode;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.PsiParser;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.tree.IFileElementType;
import com.intellij.psi.tree.TokenSet;
import io.github.djamelkorei.looma.intellij.lang.LoomaDirectives;
import io.github.djamelkorei.looma.intellij.lexer.LoomaLexer;
import io.github.djamelkorei.looma.intellij.lexer.LoomaTokenTypes;
import io.github.djamelkorei.looma.intellij.parser.LoomaParser;
import io.github.djamelkorei.looma.intellij.parser.LoomaParserDefinition;
import io.github.djamelkorei.looma.intellij.psi.LoomaElementTypes;
import org.jetbrains.annotations.NotNull;

public final class LoomaExpressionParserDefinition implements ParserDefinition {

    private final LoomaParserDefinition looma = new LoomaParserDefinition();

    @Override public @NotNull Lexer createLexer(Project project) { return new LoomaLexer(LoomaDirectives.BUILTIN, true); }
    @Override public @NotNull PsiParser createParser(Project project) { return new LoomaParser(LoomaDirectives.BUILTIN); }
    @Override public @NotNull IFileElementType getFileNodeType() { return LoomaElementTypes.EXPRESSION_FILE; }
    @Override public @NotNull TokenSet getCommentTokens() { return LoomaTokenTypes.COMMENTS; }
    @Override public @NotNull TokenSet getStringLiteralElements() { return LoomaTokenTypes.STRINGS; }
    @Override public @NotNull TokenSet getWhitespaceTokens() { return LoomaTokenTypes.WHITESPACES; }
    @Override public @NotNull PsiElement createElement(ASTNode node) { return looma.createElement(node); }
    @Override public @NotNull PsiFile createFile(@NotNull FileViewProvider viewProvider) { return new LoomaExpressionFile(viewProvider); }
}
