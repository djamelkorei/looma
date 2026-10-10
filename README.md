# Looma

A Blade-style template engine for Spring Boot.

```
looma-core                  parser, AST, renderer (depends only on spring-expression)
looma-spring-boot-starter   auto-configured engine + ViewResolver
looma-demo                  sample app
```

## Install

Looma is published to GitHub Packages, which requires a token even to download. Create a
[personal access token](https://github.com/settings/tokens) with `read:packages` and add it to `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>github</id>
      <username>YOUR_GITHUB_USERNAME</username>
      <password>YOUR_TOKEN</password>
    </server>
  </servers>
</settings>
```

Then in your project:

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/djamelkorei/looma</url>
  </repository>
</repositories>

<dependency>
  <groupId>io.github.djamelkorei</groupId>
  <artifactId>looma-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

Releasing (maintainers): create a GitHub release tagged `v0.1.0`; the *Publish* workflow sets the version
from the tag and deploys.

## Use it

Add `looma-spring-boot-starter`, put templates in `src/main/resources/templates/`
as `*.looma.html`, and return the view name from a controller:

```java
@GetMapping("/") String home(Model m) { m.addAttribute("user", "Ada"); return "home"; }
```

| Property | Default | |
|---|---|---|
| `looma.prefix` | `templates/` | classpath folder |
| `looma.suffix` | `.looma.html` | appended to the view name |
| `looma.cache` | `true` (`false` if `template-dir` is set) | parsed-template cache |
| `looma.template-dir` | unset | load templates from this folder on disk (live reload, no rebuild) |
| `looma.check-templates` | `true` | parse all templates at startup and fail on syntax errors |
| `looma.content-type` | `text/html;charset=UTF-8` | |

View names use dots or slashes for folders: `admin.users` -> `templates/admin/users.looma.html`.
Unknown views fall through to other `ViewResolver`s.

## Syntax

| | |
|---|---|
| `{{ $x }}` / `{!! $x !!}` | escaped / raw output |
| `{{-- comment --}}`, `@{{ x }}`, `@@` | comment, literal braces, literal `@` |
| `@if @elseif @else @endif`, `@unless`, `@isset` | conditionals |
| `@switch($x) @case(1) ... @break @default ... @endswitch` | switch (stacked `@case`s share a body) |
| `@foreach($xs as $x)`, `@forelse ... @empty ... @endforelse` | loops; `$loop->index/iteration/count/first/last/odd/even/remaining/parent/depth`; maps give `$e->key`, `$e->value` |
| `@for($i = 0; $i < 10; $i++)` ... `@endfor` | counted loop; step is `++`, `--`, `+= n` or `-= n` on the loop variable (`$i` is local to the loop) |
| `@while($it.hasNext())` ... `@endwhile` | loops while the condition is truthy; the body must change it (e.g. `$it.next()`) |
| `@break`, `@continue`, `@break($cond)`, `@continue($cond)` | in any loop; `@break` in a `@switch` ends the switch; both are errors outside a loop |
| `@extends('layouts.app')`, `@section`, `@yield('x', 'default')`, `@parent`, `@show` | layouts |
| `@include('partials.nav', {'n': 1})` | partials, inheriting the current scope |
| `@push('js')`, `@prepend`, `@stack('js')` | stacks, usable before the pushes happen |
| `<x-alert type="info" :count="items.size()">..</x-alert>` | components from `templates/components/` |
| `<x-slot:title>..</x-slot:title>` | named slots; the body is `$slot` |
| `{{ $attributes->except('type')->merge({'class': 'a'}) }}` | attribute bag |
| `@csrf`, `@method('PUT')` | hidden inputs |
| `@auth ... @endauth`, `@guest` | based on `principal` |
| `@verbatim ... @endverbatim` | no processing |

Expressions are SpEL with Blade conveniences: a leading `$` is optional, `->` is `.`,
`??` is the Elvis operator, and `&&`, `||`, `!` work. PHP-style truthiness applies to
`@if` (null, `0`, `""` and empty collections are false). Templates run in SpEL's
`SimpleEvaluationContext`: no `T(...)`, constructors or bean references.
Map literals use SpEL syntax: `{'key': value}`.

Components run in an isolated scope: they only see their attributes, slots and `$attributes`.
In the Spring starter every view also gets `request`, `principal` and, when Spring Security's
CSRF filter is active, `_csrf`. Model attributes with the same names win.

## Extending: your own directives

A directive is one class. Declare it as a Spring bean (or pass it to `LoomaEngine.builder(...)`):

```java
@Component
public class MoneyDirective implements Directive {
    public String name() { return "money"; }
    public Node parse(ParseContext p) {
        Expr amount = p.expr();
        return ctx -> ctx.writeEscaped(String.format("%.2f €", ((Number) ctx.eval(amount)).doubleValue()));
    }
}
```
```blade
Total: @money($order.total)
```

The built-in directives are written the same way (see `looma-core/.../directive`). The full guide, including
block directives, is in [ARCHITECTURE.md](ARCHITECTURE.md).

## Live reload in development

```properties
looma.template-dir=src/main/resources/templates
```

Templates are read from that folder on every request (caching switches off automatically), so you edit a
`.looma.html` file and refresh the browser, with no rebuild or restart. Leave the property unset in production
to load from the classpath with caching on.

## Fail fast

`looma.check-templates` (default `true`) parses every template at startup and fails the boot with **all**
syntax errors at once, naming template and line, for example
`In template 'orders' at line 12: Unclosed block: @foreach is never closed`. It also reports literal
`@include('x')`, `@extends('x')` and `<x-x>` references to templates that do not exist. Use it in CI too: a context
test that boots the app catches a broken template before it ships. Programmatic access: `engine.check(names)`.

## Security model

- `{{ }}` and `@lang` HTML-escape; only `{!! !!}`, component slots and `$attributes` emit markup as-is.
  Never put untrusted input in `{!! !!}`.
- Templates are **trusted code** written by developers, not by end users. The expression sandbox
  is defense in depth (no `T()`, `new`, bean references, `class`/`getClass()`, or calls on
  `Class`/`ClassLoader`/`Method`/`Runtime`/`Process`); it is not meant for running user-submitted templates.
- Do not build `@include` / `@extends` / `<x-...>` names from request input.
- Template names are validated (`[A-Za-z0-9_-./]`, no `..`), so view names cannot escape the template folder.
- Rendering is buffered, so an error produces a normal error response instead of half a page.

## Operations

- Parsed templates are immutable and cached per `LoomaEngine` (thread-safe); keep `looma.cache=true` in production.
- Errors are `LoomaException`s naming the template and line, e.g. `In template 'home' at line 12: ...`.
- `@include`/`@extends`/components may nest 100 deep before failing, which stops accidental recursion.
- `@for`/`@while` stop with an error after 100,000 passes, so a condition that never turns false cannot hang a request.
- `looma.*` properties have IDE auto-completion through the generated configuration metadata.

## Compatibility

Tested on every push (see `.github/workflows/ci.yml`) with the full suite, including Spring Security:

| | Spring Boot 3.3 | 3.4 | 3.5 | 4.0 | 4.1 |
|---|---|---|---|---|---|
| **Java 21** | ✅ | ✅ | ✅ | CI | ✅ |
| **Java 25** | CI | CI | ✅ | ✅ | CI |

✅ = run locally before release, CI = covered by the CI matrix. Java 17 is not supported (the engine uses Java 21 features).

## Build

    mvn verify            # Java 21+, Maven 3.9+
    mvn -Prelease verify  # also builds sources and javadoc jars

Publishing (group id, repository, signing) is not configured; set that up for your registry before releasing.

## License

[Apache License 2.0](LICENSE) © 2026 Djamel Eddine Korei
