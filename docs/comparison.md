---
title: Looma vs Thymeleaf vs Blade
---

# Looma vs Thymeleaf vs Blade

An honest feature-by-feature comparison, including where Looma falls short. Looma brings Laravel Blade's syntax
to Java; Thymeleaf is the established Java choice; Blade itself is part of Laravel, so it is here as the
syntax reference, not as an option for a Spring application.

**Legend:** ✅ built in · ⚠️ partial, via an add-on, or with a workaround · ❌ not available

Versions compared: Looma 0.1.0, Thymeleaf 3.1 (with Spring integration), Laravel 12 Blade. October 2026.

## At a glance

| | Thymeleaf | Blade | Looma |
|---|---|---|---|
| Platform | Java (Spring optional) | Laravel only | Java 21+ (Spring optional) |
| Template style | HTML attributes (`th:text`) | Text with `{{ }}` and `@directives` | Text with `{{ }}` and `@directives` |
| Templates open as valid static HTML ("natural templates") | ✅ | ❌ | ❌ |
| Maturity | Since 2011, very widely used | Since 2013, very widely used | New (0.1), no production track record yet |

## Output and expressions

| Feature | Thymeleaf | Blade | Looma |
|---|---|---|---|
| Escaped output by default | ✅ `th:text` | ✅ `{{ }}` | ✅ `{{ }}` |
| Raw output | ✅ `th:utext` | ✅ `{!! !!}` | ✅ `{!! !!}` |
| Inline expressions in text | ✅ `[[...]]` / `[(...)]` | ✅ | ✅ |
| Comments not sent to the browser | ✅ `<!--/* */-->` | ✅ `{{-- --}}` | ✅ `{{-- --}}` |
| Expression language | OGNL, or SpEL with Spring | Unrestricted (any code) | SpEL subset (`$x->y`, `??` supported) |
| Utility functions | ✅ `#strings`, `#numbers`, `#temporals`, `#lists`... | ✅ any function | ⚠️ `String` and `Math` helpers, plus your own [helpers](extending.md#template-helpers); no date/number formatting helpers yet |
| JSON safe for `<script>` | ✅ JavaScript inlining | ✅ `@json` / `@js` | ✅ `@json` |
| Text/JS/CSS/XML template modes | ✅ | ⚠️ any text, HTML escaping only | ⚠️ any text, HTML escaping only |

## Control flow

| Feature | Thymeleaf | Blade | Looma |
|---|---|---|---|
| if / else-if / else, unless | ✅ (`th:if`, `th:unless`; no else-if) | ✅ | ✅ |
| isset / empty checks | ⚠️ via expressions | ✅ `@isset` `@empty` | ✅ `@isset` (no `@empty($x)`) |
| switch | ✅ `th:switch` | ✅ | ✅ |
| for-each with loop variable | ✅ status: index, count, first, last, even, odd | ✅ `$loop` incl. depth, parent | ✅ `$loop` incl. depth, parent |
| Empty-list fallback | ⚠️ separate `th:if` | ✅ `@forelse` | ✅ `@forelse` |
| Counted `for` / `while` loops | ⚠️ `#numbers.sequence` | ✅ | ✅ (bounded to 100,000 passes) |
| break / continue (conditional) | ❌ | ✅ | ✅ |

## Layouts and composition

| Feature | Thymeleaf | Blade | Looma |
|---|---|---|---|
| Layout inheritance | ⚠️ Layout Dialect (third-party) or fragments | ✅ `@extends` / `@section` / `@yield` | ✅ `@extends` / `@section` / `@yield` |
| Append to the parent's section | ⚠️ Layout Dialect | ✅ `@parent` | ✅ `@parent` |
| Includes / partials | ✅ `th:insert` / `th:replace` | ✅ | ✅ |
| Conditional includes | ⚠️ with `th:if` | ✅ `@includeIf` / `When` / `First`, `@each` | ❌ (use `@if` around `@include`) |
| Components with attributes | ⚠️ parameterised fragments | ✅ class and anonymous components | ✅ anonymous components (`<x-alert>`) |
| Named slots | ⚠️ fragment expressions `~{}` | ✅ | ✅ `<x-slot:title>` |
| Attribute bag (`merge`, `except`) | ❌ | ✅ | ✅ |
| Declared props with defaults | ⚠️ fragment parameters | ✅ `@props`, `@aware` | ❌ |
| Stacks (`@push` into a layout's `<head>`) | ❌ | ✅ (incl. `@pushOnce`) | ✅ `@push` / `@prepend` / `@stack` |
| Render once per page | ❌ | ✅ `@once` | ✅ `@once` |
| Render only a fragment (htmx-style partial updates) | ✅ `view :: fragment` | ✅ `@fragment` | ❌ |

## Forms and web

| Feature | Thymeleaf | Blade | Looma |
|---|---|---|---|
| CSRF token | ✅ automatic in `th:action` forms | ✅ `@csrf` | ✅ `@csrf` (works with Spring Security) |
| PUT / PATCH / DELETE forms | ✅ `th:method` | ✅ `@method` | ✅ `@method` |
| Validation error messages | ✅ `#fields.hasErrors`, `th:errors` | ✅ `@error` | ✅ `@error` (from `@Valid` `BindingResult`) |
| Form field binding (name, value, checked from the object) | ✅ `th:object` + `th:field` | ⚠️ `old()` | ❌ write `name` / `value` yourself |
| Conditional boolean attributes | ✅ `th:checked`, `th:selected`... | ✅ `@checked`, `@selected`, `@disabled`... | ❌ (use `@if` or the attribute bag) |
| Conditional classes / styles | ✅ `th:classappend`, `th:styleappend` | ✅ `@class` / `@style` | ✅ `@class` / `@style` |
| Signed-in / role checks | ✅ `sec:authorize` (Spring Security extras) | ✅ `@auth`, `@can` (policies) | ⚠️ `@auth`, `@auth('ROLE')`, `@guest`; no permission expressions |
| Translations | ✅ `#{key}` | ✅ `@lang` / `__()` | ✅ `@lang` (Spring `MessageSource`, request locale) |

## Safety and reliability

| Feature | Thymeleaf | Blade | Looma |
|---|---|---|---|
| Templates can run arbitrary code | ❌ no | ✅ yes (raw code blocks) | ❌ no |
| Expression sandbox | ⚠️ 3.1 blocks calls on `java.*` / `javax.*` / `jdk.*` classes; three bypass CVEs in 2026, fixed in 3.1.4–3.1.5 | ❌ templates can run any code | ✅ no `T()`, `new`, bean refs, reflection, `Class`/`Thread`/`Process` access; tested against bypasses |
| Static class access | ⚠️ restricted in 3.1 | ✅ unrestricted | ⚠️ only through helpers you expose |
| Broken templates found at startup | ❌ at render time | ⚠️ precompile views in CI | ✅ app fails to boot, listing every syntax error and missing include/layout/component with its line |
| Runaway recursion / loops bounded | ❌ | ❌ | ✅ nesting 100 / 200, loops 100,000 |
| Error points at template and line | ✅ | ✅ | ✅ |
| No half-rendered page on error | ⚠️ depends on configuration | ✅ | ✅ output is buffered |

## Integration and extending

| Feature | Thymeleaf | Blade | Looma |
|---|---|---|---|
| Spring Boot starter | ✅ official | ❌ | ✅ |
| Usable without Spring | ✅ | n/a (Laravel) | ✅ `looma-core` |
| Custom tags / directives | ⚠️ dialects and processors (powerful, verbose) | ✅ `Blade::directive`, `Blade::if` | ✅ one class per directive, auto-registered as a Spring bean |
| Expose your own functions | ✅ custom expression objects | ✅ any function | ✅ `TemplateHelper` beans |
| Live reload in development | ✅ cache off + DevTools | ✅ recompiles on change | ✅ `looma.template-dir` |
| Template caching | ✅ | ✅ compiled and cached | ✅ parsed once, thread-safe |
| IDE support | ✅ IntelliJ Ultimate, VS Code | ✅ JetBrains IDEs, VS Code | ⚠️ IntelliJ plugin in development |

## Performance

Same page in each Java engine, measured with JMH ([details](benchmarks.md)):

| Render time | Thymeleaf | Looma |
|---|---|---|
| 10-row table | 11.5 µs | **2.9 µs** |
| 200-row table | 230.7 µs | **55.1 µs** |

Blade is not a Java engine, so it is not part of this measurement.

## When to choose which

**Choose Thymeleaf** if designers edit templates as static HTML (natural templates), you rely on `th:field`
form binding or htmx fragment rendering, or you need a mature engine with a long track record and wide IDE support.

**Choose Looma** if your team prefers Blade-style templates on Spring, you want layouts, components, slots and
stacks without add-ons, a stricter sandbox, startup validation and speed, and you are comfortable adopting a
new 0.x project.

**Blade** is the right choice only inside Laravel; Looma exists for people who like Blade and work on the JVM.

## Gaps Looma may close next

Conditional includes (`@includeIf` / `@includeWhen` / `@each`), `@props` with defaults, `@checked` /
`@selected` / `@disabled`, fragment rendering for htmx, date and number formatting helpers, and form field binding.
