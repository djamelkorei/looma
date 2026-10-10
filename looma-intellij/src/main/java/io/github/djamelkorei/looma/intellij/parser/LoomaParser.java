package io.github.djamelkorei.looma.intellij.parser;

import static io.github.djamelkorei.looma.intellij.lexer.LoomaTokenTypes.*;

import com.intellij.lang.ASTNode;
import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiParser;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import io.github.djamelkorei.looma.intellij.lang.LoomaDirectives;
import io.github.djamelkorei.looma.intellij.lang.LoomaDirectives.Directive;
import io.github.djamelkorei.looma.intellij.psi.LoomaElementTypes;
import java.util.ArrayDeque;
import java.util.Deque;
import org.jetbrains.annotations.NotNull;

/**
 * Builds the Looma tree: echoes, directives and blocks. It never reports errors itself; problems such as
 * unclosed blocks are found by {@code LoomaAnnotator} so they can be worded like the engine's messages.
 */
public final class LoomaParser implements PsiParser {

    private static final TokenSet EXPRESSION = TokenSet.create(WHITE_SPACE, BAD_CHARACTER, VARIABLE, IDENTIFIER,
            KEYWORD, BEAN, STRING, NUMBER, LPAREN, RPAREN, LBRACKET, RBRACKET, LBRACE, RBRACE, COMMA, SEMICOLON, COLON,
            DOT, ARROW, SAFE_DOT, OPERATOR);
    private static final TokenSet OPENING = TokenSet.create(LPAREN, LBRACKET, LBRACE);
    private static final TokenSet CLOSING = TokenSet.create(RPAREN, RBRACKET, RBRACE);

    private final LoomaDirectives directives;
    private PsiBuilder builder;
    private final Deque<Directive> open = new ArrayDeque<>();

    public LoomaParser(LoomaDirectives directives) {
        this.directives = directives;
    }

    @Override
    public @NotNull ASTNode parse(@NotNull IElementType root, @NotNull PsiBuilder builder) {
        this.builder = builder;
        PsiBuilder.Marker file = builder.mark();
        if (root == LoomaElementTypes.EXPRESSION_FILE) {
            while (!builder.eof()) {
                expression(false, null);
                if (!builder.eof()) builder.advanceLexer();
            }
        }
        while (!builder.eof()) {
            content();
        }
        file.done(root);
        return builder.getTreeBuilt();
    }

    /** One item of template content; stops (without consuming) at a tag that belongs to an open block. */
    private boolean content() {
        IElementType type = builder.getTokenType();
        if (type == DIRECTIVE) {
            String name = directiveName();
            if (closesOpenBlock(name)) return false;
            Directive d = directives.directive(name);
            if (d != null && d.isBlock()) {
                block(d);
            } else {
                directive(null);
            }
        } else if (type == ECHO_OPEN) {
            echo(ECHO_CLOSE, LoomaElementTypes.ECHO);
        } else if (type == RAW_OPEN) {
            echo(RAW_CLOSE, LoomaElementTypes.RAW_ECHO);
        } else if (type != null && EXPRESSION.contains(type)) {
            expression(false, null); // stray expression tokens (should not happen), keep them in the tree
        } else {
            builder.advanceLexer();
        }
        return true;
    }

    private void block(Directive d) {
        PsiBuilder.Marker block = builder.mark();
        int topLevelCommas = directive(d.name());
        if (d.name().equals("section") && topLevelCommas > 0) {
            block.drop(); // @section('name', 'value') has no body
            return;
        }
        open.push(d);
        try {
            while (!builder.eof()) {
                if (builder.getTokenType() == DIRECTIVE) {
                    String name = directiveName();
                    if (d.ends().containsKey(name)) {
                        directive(null);
                        break;
                    }
                    if (d.middle().containsKey(name)) {
                        directive(null);
                        continue;
                    }
                }
                if (!content()) break; // a tag of an outer block: this one is unclosed
            }
        } finally {
            open.pop();
        }
        block.done(LoomaElementTypes.BLOCK);
    }

    private boolean closesOpenBlock(String name) {
        for (Directive d : open) {
            if (d.ends().containsKey(name) || d.middle().containsKey(name)) return true;
        }
        return false;
    }

    /** {@code @name(args)}; returns the number of top-level commas in the arguments. */
    private int directive(String declaring) {
        PsiBuilder.Marker m = builder.mark();
        builder.advanceLexer();
        int commas = 0;
        if (builder.getTokenType() == LPAREN) {
            PsiBuilder.Marker args = builder.mark();
            commas = expression(true, declaring);
            args.done(LoomaElementTypes.ARGUMENTS);
        }
        m.done(LoomaElementTypes.DIRECTIVE);
        return commas;
    }

    private void echo(IElementType close, IElementType type) {
        PsiBuilder.Marker m = builder.mark();
        builder.advanceLexer();
        expression(false, null);
        if (builder.getTokenType() == close) builder.advanceLexer();
        m.done(type);
    }

    /**
     * Expression tokens of an echo or, if {@code arguments}, of an argument list starting at '(' and ending at
     * its matching ')'. Marks variables, members and strings. {@code declaring} is the directive whose
     * arguments these are, to find the variables it introduces. Returns the number of top-level commas.
     */
    private int expression(boolean arguments, String declaring) {
        Deque<IElementType> brackets = new ArrayDeque<>();
        int parens = 0;
        int commas = 0;
        int depth = arguments ? 1 : 0; // bracket depth of the top level
        IElementType previous = null;
        boolean afterAs = false;
        boolean first = true;
        while (!builder.eof() && EXPRESSION.contains(builder.getTokenType())) {
            IElementType type = builder.getTokenType();
            if (type == VARIABLE || type == IDENTIFIER) {
                boolean member = previous != null && MEMBER_ACCESS.contains(previous);
                PsiBuilder.Marker m = builder.mark();
                builder.advanceLexer();
                if (member) {
                    m.done(LoomaElementTypes.MEMBER);
                } else if (type == IDENTIFIER && (builder.getTokenType() == LPAREN || isMapKey(previous, brackets))) {
                    m.drop(); // a function call or a map key, not a variable
                } else if (declares(declaring, afterAs, first)) {
                    m.done(LoomaElementTypes.VARIABLE_DECLARATION);
                } else {
                    m.done(LoomaElementTypes.VARIABLE);
                }
            } else if (type == STRING || type == BEAN) {
                PsiBuilder.Marker m = builder.mark();
                builder.advanceLexer();
                m.done(type == STRING ? LoomaElementTypes.STRING_LITERAL : LoomaElementTypes.BEAN);
            } else {
                boolean as = type == KEYWORD && "as".equals(builder.getTokenText()) && brackets.size() == depth;
                if (OPENING.contains(type)) brackets.push(type);
                if (CLOSING.contains(type) && !brackets.isEmpty()) brackets.pop();
                if (type == COMMA && brackets.size() == depth) commas++;
                if (type == LPAREN) parens++;
                boolean opener = arguments && previous == null;
                builder.advanceLexer();
                if (type == RPAREN && --parens == 0 && arguments) return commas; // end of the argument list
                previous = type;
                afterAs = as;
                if (!opener) first = false;
                continue;
            }
            previous = type;
            afterAs = false;
            first = false;
        }
        return commas;
    }

    /** {@code key} in {@code {key: value}}: an identifier right after '{' or ',' inside braces, before ':'. */
    private boolean isMapKey(IElementType previous, Deque<IElementType> brackets) {
        return brackets.peek() == LBRACE && (previous == LBRACE || previous == COMMA) && builder.getTokenType() == COLON;
    }

    private static boolean declares(String directive, boolean afterAs, boolean first) {
        if (directive == null) return false;
        return switch (directive) {
            case "foreach", "forelse" -> afterAs;
            case "for" -> first;
            default -> false;
        };
    }

    private String directiveName() {
        String text = builder.getTokenText();
        return text == null || text.isEmpty() ? "" : text.substring(1);
    }
}
