package io.github.djamelkorei.looma.ast;

import java.util.List;

public sealed interface Node {

    record Text(String value) implements Node {}

    /** {@code {{ expr }}} (escaped) or {@code {!! expr !!}} (raw). */
    record Echo(Expr expr, boolean raw) implements Node {}

    record If(List<Branch> branches, List<Node> orElse) implements Node {}

    record Branch(Expr test, List<Node> body) {}

    /** {@code @foreach} / {@code @forelse}; {@code empty} is empty for plain foreach. */
    record Foreach(Expr items, String var, List<Node> body, List<Node> empty) implements Node {}

    /** {@code @switch}; a case with no tests is the {@code @default} case. */
    record Switch(Expr subject, List<Case> cases) implements Node {}

    record Case(List<Expr> tests, List<Node> body) {}

    record Include(Expr name, Expr data) implements Node {}

    record Extends(Expr name) implements Node {}

    record Section(String name, List<Node> body) implements Node {}

    /** {@code @parent} inside a section: renders the overridden section from the parent layout. */
    record Parent() implements Node {}

    /** {@code @push} / {@code @prepend}: captures its body onto a named stack. */
    record Push(String name, List<Node> body, boolean prepend) implements Node {}

    /** {@code @stack('name')}: outputs everything pushed onto the stack. */
    record Stack(String name) implements Node {}

    /** {@code <x-name attr="v" :attr="expr">body</x-name>}. */
    record Component(String name, List<Attr> attrs, List<Node> body, java.util.Map<String, List<Node>> slots)
            implements Node {}

    record Attr(String name, Expr value) {}

    /** {@code <x-slot:name>} inside a component; only meaningful as a child of {@link Component}. */
    record Slot(String name, List<Node> body) implements Node {}

    /** {@code @csrf}: hidden input for the {@code _csrf} variable (renders nothing if absent). */
    record Csrf() implements Node {}

    /** {@code @method('PUT')}: hidden {@code _method} input for HTML form method spoofing. */
    record Method(Expr value) implements Node {}

    record Yield(String name, Expr fallback) implements Node {}
}
