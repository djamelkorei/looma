package io.github.djamelkorei.looma.intellij.highlight;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import com.intellij.openapi.options.colors.AttributesDescriptor;
import com.intellij.openapi.options.colors.ColorDescriptor;
import com.intellij.openapi.options.colors.ColorSettingsPage;
import io.github.djamelkorei.looma.intellij.LoomaIcons;
import io.github.djamelkorei.looma.intellij.lang.LoomaDirectives;
import java.util.Map;
import javax.swing.Icon;
import org.jetbrains.annotations.NotNull;

public final class LoomaColorSettingsPage implements ColorSettingsPage {

    private static final AttributesDescriptor[] DESCRIPTORS = {
            new AttributesDescriptor("Directive", LoomaColors.DIRECTIVE),
            new AttributesDescriptor("Braces//Echo {{ }}", LoomaColors.ECHO_BRACES),
            new AttributesDescriptor("Braces//Raw echo {!! !!}", LoomaColors.RAW_BRACES),
            new AttributesDescriptor("Comment", LoomaColors.COMMENT),
            new AttributesDescriptor("Escape (@{{, @@)", LoomaColors.ESCAPE),
            new AttributesDescriptor("Expression//Variable", LoomaColors.VARIABLE),
            new AttributesDescriptor("Expression//Identifier", LoomaColors.IDENTIFIER),
            new AttributesDescriptor("Expression//Property", LoomaColors.PROPERTY),
            new AttributesDescriptor("Expression//Method call", LoomaColors.METHOD),
            new AttributesDescriptor("Expression//Keyword", LoomaColors.KEYWORD),
            new AttributesDescriptor("Expression//String", LoomaColors.STRING),
            new AttributesDescriptor("Expression//Number", LoomaColors.NUMBER),
            new AttributesDescriptor("Expression//Operator", LoomaColors.OPERATOR),
            new AttributesDescriptor("Expression//Parentheses", LoomaColors.PARENTHESES),
            new AttributesDescriptor("Expression//Brackets", LoomaColors.BRACKETS),
            new AttributesDescriptor("Expression//Braces", LoomaColors.BRACES),
            new AttributesDescriptor("Expression//Comma", LoomaColors.COMMA),
            new AttributesDescriptor("Expression//Dot", LoomaColors.DOT),
            new AttributesDescriptor("Bad character", LoomaColors.BAD_CHARACTER),
    };

    @Override public Icon getIcon() { return LoomaIcons.FILE; }
    @Override public @NotNull String getDisplayName() { return "Looma"; }

    @Override
    public @NotNull SyntaxHighlighter getHighlighter() {
        return new LoomaSyntaxHighlighter(LoomaDirectives.BUILTIN);
    }

    @Override
    public @NotNull String getDemoText() {
        return """
                @extends('layouts.app')

                @section('content')
                    {{-- a comment --}}
                    <h1>Hello, {{ $user->name ?? 'guest' }}</h1>
                    @foreach($posts as $post)
                        <a href="/posts/{{ $post.id }}" @class('post', {'first': $loop->first})>{{ $post->title }}</a>
                    @endforeach
                    {!! $html !!} @{{ vue }} @@media
                @endsection
                """;
    }

    @Override public Map<String, TextAttributesKey> getAdditionalHighlightingTagToDescriptorMap() { return null; }
    @Override public AttributesDescriptor @NotNull [] getAttributeDescriptors() { return DESCRIPTORS; }
    @Override public ColorDescriptor @NotNull [] getColorDescriptors() { return ColorDescriptor.EMPTY_ARRAY; }
}
