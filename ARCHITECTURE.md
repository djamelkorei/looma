# Architecture

Looma is built so that the common change, *adding or fixing a directive*, touches **one file**.

## The pipeline

```
template text ──Lexer──▶ tokens ──Parser──▶ Node tree ──render(RenderContext)──▶ String
                 │                  │
                 └── asks ──────────┴── delegates every @directive to ──▶ Directive
                     DirectiveRegistry
```

| Package | Role | May depend on |
|---|---|---|
| `io.github.djamelkorei.looma.spi` | The public contract: `Directive`, `Node`, `ParseContext`, `RenderContext`, `Expr`, `Syntax`. | nothing of ours |
| `io.github.djamelkorei.looma.internal` | Lexer, parser, renderer, expression evaluation + sandbox, components. **Knows no directive by name.** | `spi` |
| `io.github.djamelkorei.looma.directive` | Every built-in directive, one class (or one small family) each. | `spi` only |
| `io.github.djamelkorei.looma` | `LoomaEngine`, loaders, `Template`, `LoomaException`. | all of the above |
| `looma-spring-boot-starter` | Auto-configuration, `ViewResolver`, `looma.*`, startup check. | core |

`ArchitectureTest` fails the build if these arrows are broken. The rule that matters most: built-in
directives use **only** the public SPI. If one ever needs more, the SPI is missing something, so fix the SPI
instead of reaching around it. That is also why a directive you write is never second class.

## How a directive works

A `Directive` has two jobs, mirrored by the two SPI interfaces:

1. **Parse time**: `parse(ParseContext)` reads the arguments (`p.expr()`, `p.splitArgs()`, ...) and, for
   block directives, the body (`p.block("endfoo")`), and returns a `Node`.
2. **Render time**: the `Node` writes output through `RenderContext` (`ctx.eval`, `ctx.write`,
   `ctx.render(body)`, `ctx.child()`, ...).

Nodes are immutable and shared between concurrent requests. **Never keep per-render data in a node field**;
use `ctx.state(Type.class, Type::new)` (see `Sections`, `StackDirectives`).

## Add a directive (the whole procedure)

Say you want `@money($amount)`.

```java
public final class MoneyDirective implements Directive {
    @Override public String name() { return "money"; }

    @Override
    public Node parse(ParseContext p) {
        Expr amount = p.expr();                       // parse-time work happens once per template
        return ctx -> ctx.writeEscaped(               // render-time work happens per request
                String.format("%.2f €", ((Number) ctx.eval(amount)).doubleValue()));
    }
}
```

* **Spring Boot:** declare it as a `@Bean` / `@Component`. Nothing else.
* **Plain Java:** `LoomaEngine.builder(loader).directive(new MoneyDirective()).build()`.
* **Built-in:** add it to `BuiltinDirectives.all()` and put a test next to the others.

A block directive declares its closing tags and reads its body:

```java
@Override public Map<String, Syntax> tags() { return Map.of("endrepeat", Syntax.NONE); }

@Override public Node parse(ParseContext p) {
    Expr times = p.expr();
    List<Node> body = p.block("endrepeat").nodes();   // consumes @endrepeat
    return ctx -> { for (int i = 0; i < n(ctx, times); i++) ctx.render(body); };
}
```

Things the SPI gives you for free: error messages with template name and line, escaping
(`writeEscaped`), isolated variable scopes (`child()`), loops with `@break`/`@continue` support
(`runLoopBody`), per-render state, an end-of-render hook (`onFinish`), and a parse-time marker mechanism
(`within` / `isWithin`) so a directive can insist on being used inside another one.

If your directive names another template by a fixed name, call `p.references(name)` (use `p.literalOrNull(arg)`
to tell a fixed name from an expression) and the startup check will report it when it is missing.

Names must be letters only; `verbatim` is reserved by the lexer; clashes fail when the engine is built.

## Testing conventions

* **Behaviour tests** render a template string and compare the output (`LoomaEngineTest`). They know nothing
  about internals, so refactors don't break them. Prefer these.
* **Custom-directive tests** (`CustomDirectiveTest`) show the SPI from the outside.
* **Starter tests** use `WebApplicationContextRunner`; **demo tests** go through MockMvc.
* Every directive needs a test for its error path (unclosed, misplaced, malformed args) as well as its happy path.

## Design decisions worth knowing

* **Expressions are SpEL** in a `SimpleEvaluationContext` plus a sandbox (`internal/Sandbox`). Blade
  conveniences (`$x`, `->`, `??`) are a text translation in `Expressions.translate`, not a new language.
* **Layouts are core**, not a directive detail: `@extends` records the layout on the `Template`; a child's
  top-level `LayoutContribution`s (sections) are registered, then the layout renders.
* **Output is buffered** per render, so an error never sends half a page. `@stack` uses placeholders
  resolved in an `onFinish` hook for the same reason it can be declared before it is filled.
* **Everything is bounded**: nesting depth (100) and `@for`/`@while` passes (100,000).
