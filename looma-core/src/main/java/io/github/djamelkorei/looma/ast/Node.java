package io.github.djamelkorei.looma.ast;

import java.util.List;
import org.springframework.expression.Expression;

public sealed interface Node {

    record Text(String value) implements Node {}

    /** {@code {{ expr }}} (escaped) or {@code {!! expr !!}} (raw). */
    record Echo(Expression expr, boolean raw) implements Node {}

    record If(List<Branch> branches, List<Node> orElse) implements Node {}

    record Branch(Expression test, List<Node> body) {}

    /** {@code @foreach} / {@code @forelse}; {@code empty} is empty for plain foreach. */
    record Foreach(Expression items, String var, List<Node> body, List<Node> empty) implements Node {}

    /** {@code @switch}; a case with no tests is the {@code @default} case. */
    record Switch(Expression subject, List<Case> cases) implements Node {}

    record Case(List<Expression> tests, List<Node> body) {}

    record Include(Expression name, Expression data) implements Node {}

    record Extends(Expression name) implements Node {}

    record Section(String name, List<Node> body) implements Node {}

    record Yield(String name, Expression fallback) implements Node {}
}
