---
title: Looma vs Thymeleaf
---

# Looma vs Thymeleaf

An honest feature-by-feature comparison, including where Looma falls short. Thymeleaf is the established
template engine for Spring; Looma brings Laravel Blade's syntax to the same place.

**Legend:** ✅ built in · ⚠️ partial, via an add-on, or with a workaround · ❌ not available. Every row is phrased so that ✅ is the better answer.

Versions compared: Looma 0.1.1 and Thymeleaf 3.1 with Spring integration. October 2026.

## At a glance

| | Thymeleaf | Looma |
|---|---|---|
| Platform | Java (Spring optional) | Java 21+ (Spring optional) |
| Template style | HTML attributes (`th:text`) | Text with `{{ }}` and `@directives` |
| Templates open as valid static HTML ("natural templates") | ✅ | ❌ |
| Maturity | Since 2011, very widely used | New (0.1), no production track record yet |

## Output and expressions

| Feature | Thymeleaf | Looma |
|---|---|---|
| Escaped output by default | ✅ `th:text` | ✅ `{{ }}` |
| Raw output | ✅ `th:utext` | ✅ `{!! !!}` |
| Inline expressions in text | ✅ `[[...]]` / `[(...)]` | ✅ |
| Comments not sent to the browser | ✅ `<!--/* */-->` | ✅ `{{-- --}}` |
| Expression language | OGNL, or SpEL with Spring | SpEL subset (`$x->y`, `??` supported) |
| Utility functions | ✅ `#strings`, `#numbers`, `#temporals`, `#lists`... | ✅ `String`, `Number`, `Date`, `List`, `Math` helpers, locale-aware, with extras Thymeleaf lacks: relative dates, compact numbers, file sizes, `chunk`, `groupBy`, `slug`, `plural` |
| JSON safe for `<script>` | ✅ JavaScript inlining | ✅ `@json` |
| Text/JS/CSS/XML template modes | ✅ | ⚠️ any text, HTML escaping only |

## Control flow

| Feature | Thymeleaf | Looma |
|---|---|---|
| if / else-if / else, unless | ✅ (`th:if`, `th:unless`; no else-if) | ✅ |
| isset / empty checks | ⚠️ via expressions | ✅ `@isset` `@empty` |
| switch | ✅ `th:switch` | ✅ |
| for-each with loop variable | ✅ status: index, count, first, last, even, odd | ✅ `$loop` incl. depth, parent |
| Empty-list fallback | ⚠️ separate `th:if` | ✅ `@forelse` |
| Counted `for` / `while` loops | ⚠️ `#numbers.sequence` | ✅ (bounded to 100,000 passes) |
| break / continue (conditional) | ❌ | ✅ |

## Layouts and composition

| Feature | Thymeleaf | Looma |
|---|---|---|
| Layout inheritance | ⚠️ Layout Dialect (third-party) or fragments | ✅ `@extends` / `@section` / `@yield` |
| Append to the parent's section | ⚠️ Layout Dialect | ✅ `@parent` |
| Includes / partials | ✅ `th:insert` / `th:replace` | ✅ |
| Conditional includes | ⚠️ with `th:if` | ✅ `@includeIf` / `When` / `Unless` / `First`, `@each` |
| Components with attributes | ⚠️ parameterised fragments | ✅ anonymous components (`<x-alert>`) |
| Named slots | ⚠️ fragment expressions `~{}` | ✅ `<x-slot:title>` |
| Attribute bag (`merge`, `except`) | ❌ | ✅ |
| Declared props with defaults | ⚠️ fragment parameters | ✅ `@props` (no `@aware`) |
| Stacks (`@push` into a layout's `<head>`) | ❌ | ✅ `@push` / `@prepend` / `@stack` |
| Render once per page | ❌ | ✅ `@once` |
| Render only a fragment (htmx-style partial updates) | ✅ `view :: fragment` | ✅ `@fragment`, view name `view :: fragment` |

## Forms and web

| Feature | Thymeleaf | Looma |
|---|---|---|
| CSRF token | ✅ automatic in `th:action` forms | ✅ `@csrf` (works with Spring Security) |
| PUT / PATCH / DELETE forms | ✅ `th:method` | ✅ `@method` |
| Validation error messages | ✅ `#fields.hasErrors`, `th:errors` | ✅ `@error` (from `@Valid` `BindingResult`) |
| Form field binding (name, value, checked from the object) | ✅ `th:object` + `th:field` | ✅ `@bind` + `@field`, `@checkbox`, `@radio`, `@option`; shows rejected input after a failed submit |
| Conditional boolean attributes | ✅ `th:checked`, `th:selected`... | ✅ `@checked`, `@selected`, `@disabled`, `@readonly`, `@required` |
| Conditional classes / styles | ✅ `th:classappend`, `th:styleappend` | ✅ `@class` / `@style` |
| Signed-in, role and permission checks | ✅ `sec:authorize` (Spring Security extras) | ✅ `@auth('ROLE')`, `@can('authority')`, `@can('edit', $post)` via `PermissionEvaluator`, `@canany`; fail closed |
| Translations | ✅ `#{key}` | ✅ `@lang` (Spring `MessageSource`, request locale) |

## Safety and reliability

| Feature | Thymeleaf | Looma |
|---|---|---|
| Templates limited to expressions (cannot run arbitrary code) | ✅ | ✅ |
| Expression sandbox | ⚠️ 3.1 blocks calls on `java.*` / `javax.*` / `jdk.*` classes; three bypass CVEs in 2026, fixed in 3.1.4–3.1.5 | ✅ no `T()`, `new`, bean refs, reflection, `Class`/`Thread`/`Process` access; tested against bypasses |
| Static class access restricted | ⚠️ restricted in 3.1 (see sandbox) | ✅ only through helpers you expose |
| Broken templates found at startup | ❌ at render time | ✅ app fails to boot, listing every syntax error and missing include/layout/component with its line |
| Runaway recursion / loops bounded | ❌ | ✅ nesting 100 / 200, loops 100,000 |
| Error points at template and line | ✅ | ✅ |
| No half-rendered page on error | ⚠️ depends on configuration | ✅ output is buffered |

## Integration and extending

| Feature | Thymeleaf | Looma |
|---|---|---|
| Spring Boot starter | ✅ official | ✅ |
| Usable without Spring | ✅ | ✅ `looma-core` |
| Custom tags / directives | ⚠️ dialects and processors (powerful, verbose) | ✅ one class per directive, auto-registered as a Spring bean |
| Call Spring beans / your own functions | ✅ `${@bean...}` (every bean), custom expression objects | ✅ `@bean.method()` (opt-in: `@TemplateAccessible`), `TemplateHelper` beans |
| Live reload in development | ✅ cache off + DevTools | ✅ `looma.template-dir` |
| Template caching | ✅ | ✅ parsed once, thread-safe |
| IDE support | ✅ IntelliJ Ultimate, VS Code | ⚠️ IntelliJ plugin in development |

## Performance

Same page in each engine, measured with JMH ([details](benchmarks.md)):

| Render time | Thymeleaf | Looma |
|---|---|---|
| 10-row table | 12.2 µs | **2.7 µs** |
| 200-row table | 222.3 µs | **54.5 µs** |

## When to choose which

**Choose Thymeleaf** if designers edit templates as static HTML (natural templates), you rely on raw
`sec:authorize` expressions, or you need a mature engine with a long track record and wide IDE support.

**Choose Looma** if your team prefers Blade-style templates on Spring, you want layouts, components, slots, stacks
and Spring-aware form binding without add-ons, richer formatting helpers, a stricter sandbox (bean access is opt-in),
startup validation and speed, and you are comfortable adopting a new 0.x project.

## Remaining differences

Small gaps: localized relative dates (`Date.relative` is in English). Two differences are deliberate: no natural
templates (that is what lets templates read like text), and no raw `sec:authorize`-style security expressions
(they would run outside the sandbox).
