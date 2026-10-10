package io.github.djamelkorei.looma.intellij.lexer;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LoomaLexerTest {

    private static String lex(String text) {
        LoomaLexer lexer = new LoomaLexer();
        lexer.start(text, 0, text.length(), 0);
        StringBuilder out = new StringBuilder();
        while (lexer.getTokenType() != null) {
            if (lexer.getTokenType() != LoomaTokenTypes.WHITE_SPACE) {
                out.append(lexer.getTokenType()).append('[')
                        .append(text, lexer.getTokenStart(), lexer.getTokenEnd()).append("] ");
            }
            lexer.advance();
        }
        return out.toString().trim();
    }

    @Test
    public void echoes() {
        assertEquals("TEMPLATE_TEXT[<b>] {{[{{] VARIABLE[$user] ->[->] IDENTIFIER[name] }}[}}] TEMPLATE_TEXT[</b>]",
                lex("<b>{{ $user->name }}</b>"));
        assertEquals("{!![{!!] VARIABLE[$html] !!}[!!}]", lex("{!! $html !!}"));
        assertEquals("{{[{{] STRING['}}'] }}[}}]", lex("{{ '}}' }}"));
    }

    @Test
    public void commentsAndEscapes() {
        assertEquals("COMMENT[{{-- {{ $x }} --}}] TEMPLATE_TEXT[ a]", lex("{{-- {{ $x }} --}} a"));
        assertEquals("ESCAPE[@] TEMPLATE_TEXT[{{ vue }}]", lex("@{{ vue }}"));
        assertEquals("ESCAPE[@] TEMPLATE_TEXT[@] TEMPLATE_TEXT[media]", lex("@@media"));
    }

    @Test
    public void directives() {
        assertEquals("DIRECTIVE[@if] ([(] VARIABLE[$a] OPERATOR[&&] OPERATOR[!] VARIABLE[$b] )[)] TEMPLATE_TEXT[ x ]"
                + " DIRECTIVE[@endif]", lex("@if ($a && !$b) x @endif"));
        assertEquals("DIRECTIVE[@foreach] ([(] VARIABLE[$posts] KEYWORD[as] VARIABLE[$post] )[)]",
                lex("@foreach($posts as $post)"));
        assertEquals("DIRECTIVE[@csrf] TEMPLATE_TEXT[ (x)]", lex("@csrf (x)"));
        // OPTIONAL: '(' must follow directly
        assertEquals("DIRECTIVE[@break] TEMPLATE_TEXT[ (x)]", lex("@break (x)"));
        assertEquals("DIRECTIVE[@break] ([(] VARIABLE[$x] )[)]", lex("@break($x)"));
        // REQUIRED without '(' is text
        assertEquals("TEMPLATE_TEXT[@include x]", lex("@include x"));
    }

    @Test
    public void textThatLooksLikeDirectives() {
        assertEquals("TEMPLATE_TEXT[mail a@empty.com or @media (x) or @click=\"go\"]",
                lex("mail a@empty.com or @media (x) or @click=\"go\""));
        assertEquals("TEMPLATE_TEXT[@endverbatim]", lex("@endverbatim"));
    }

    @Test
    public void verbatim() {
        assertEquals("DIRECTIVE[@verbatim] TEMPLATE_TEXT[ {{ x }} @if ] DIRECTIVE[@endverbatim] TEMPLATE_TEXT[!]",
                lex("@verbatim {{ x }} @if @endverbatim!"));
    }

    @Test
    public void unclosedConstructsEndAtTheLine() {
        assertEquals("DIRECTIVE[@if] ([(] VARIABLE[$a] TEMPLATE_TEXT[\n<p>]", lex("@if($a\n<p>"));
        assertEquals("{{[{{] VARIABLE[$a] TEMPLATE_TEXT[\n<p>]", lex("{{ $a\n<p>"));
    }

    @Test
    public void expressions() {
        assertEquals("{{[{{] IDENTIFIER[items] .[.] IDENTIFIER[size] ([(] )[)] OPERATOR[>] NUMBER[1.5]"
                + " OPERATOR[?] STRING['it''s'] :[:] LBRACE[{] STRING['a'] :[:] KEYWORD[true] RBRACE[}] }}[}}]",
                lex("{{ items.size() > 1.5 ? 'it''s' : {'a': true} }}").replace("{[{]", "LBRACE[{]")
                        .replace("}[}]", "RBRACE[}]"));
        assertEquals("{{[{{] VARIABLE[$a] OPERATOR[??] STRING[\"b\"] }}[}}]", lex("{{ $a ?? \"b\" }}"));
    }

    @Test
    public void beansInExpressions() {
        assertEquals("{{[{{] BEAN[@priceService] .[.] IDENTIFIER[format] ([(] VARIABLE[$t] )[)] }}[}}] TEMPLATE_TEXT[ @media]",
                lex("{{ @priceService.format($t) }} @media"));
    }

    @Test
    public void restartableAtTopLevelTokens() {
        String text = "<a>{{ $x }}</a>@if($y)b@endif@verbatim{{ z }}@endverbatim{{-- c --}}";
        LoomaLexer full = new LoomaLexer();
        full.start(text, 0, text.length(), 0);
        while (full.getTokenType() != null) {
            if (full.getState() == 0) {
                LoomaLexer partial = new LoomaLexer();
                partial.start(text, full.getTokenStart(), text.length(), 0);
                LoomaLexer reference = new LoomaLexer();
                reference.start(text, 0, text.length(), 0);
                while (reference.getTokenStart() < full.getTokenStart()) reference.advance();
                while (reference.getTokenType() != null) {
                    assertEquals(reference.getTokenType(), partial.getTokenType());
                    assertEquals(reference.getTokenEnd(), partial.getTokenEnd());
                    reference.advance();
                    partial.advance();
                }
            }
            full.advance();
        }
    }
}
