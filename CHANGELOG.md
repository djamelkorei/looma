# Changelog

## 0.1.3 (2026-10-12)

- Fixed: in a page that extends a layout, a `@push` or `@prepend` outside any `@section` was silently dropped. It now
  reaches the layout's `@stack`, as in Blade.
- Rendering real pages is much faster: the common expressions (variables, getters and records, method calls,
  arithmetic, comparisons, conditions, `??`, inline lists and maps) are evaluated directly instead of through SpEL,
  with SpEL's exact rules and the same sandbox; anything else still goes to SpEL, one step at a time, so results and
  error messages are unchanged and every getter or method runs once. On a 2-CPU server the same pages cost 4× (a
  simple list) to 9× (a heavy dashboard) less CPU than with Thymeleaf. [Benchmarks](https://djamelkorei.github.io/looma/benchmarks/)
- `Number` and `Date` helpers reuse their formats, `List.sortBy` and friends cache property lookups, and
  `String.truncateWords`, `wordCount`, `initials` and `slug` no longer use regular expressions.
- Method calls in templates are resolved once per class, and a missing optional include (`@includeIf`) is looked up
  once instead of on every request.

## 0.1.2 (2026-10-11)

- Components now see the request-level variables (`errors`, `principal`, `request`, `_csrf` and the other
  `_`-prefixed ones), so `@error`, `@csrf`, `@auth`, `@can`, `@lang` and `@bind` / `@field` work inside
  `<x-input>`-style form components. Ordinary model variables stay hidden, as before.
- `{{ }}` interpolates inside plain component attributes: `<x-alert title="Hello {{ $name }}">`.
- `@foreach($map as $key => $value)` (and `@forelse`); on a list `$key` is the index.

## 0.1.1 (2026-10-10)

Fixes found by running a realistic application (Spring Security, validation, a deliberately complex dashboard)
against the published 0.1.0:

- Errors inside a `@section`, a slot or an include now name the template the expression was written in, not the
  layout that rendered it.
- After a failed form submit, multi-value fields (`@checkbox('tags', ...)`, `@option` on a list) kept no
  selections, because Spring formats a list as one string; the raw values are used now.
- `Number.compact` dropped the decimals for `BigDecimal` / `BigInteger` (`1M` instead of `1.3M`).
- An unclosed block followed by an outer closing tag (`@foreach` ... `@endsection`) is reported at the unclosed
  block's line instead of as "Unexpected @endsection".
- `@bean` references to an unknown name suggest similar bean names (e.g. a nested class registered as
  `outer.ProjectService`).
- Builds use the Maven Wrapper (Maven 3.9.16): Maven 3.10 adds files to the Maven Central bundle that Central rejects.

## 0.1.0 (2026-10-10)

First version.

- Core engine: `@once`, `@json`, `@class`, `@style`, `@error`, `@auth('ROLE')`, `@for`, `@while`, `@break`, `@continue`, `{{ }}`, `{!! !!}`, comments, `@if/@elseif/@else/@unless/@isset/@switch`,
  `@foreach/@forelse` with `$loop`, `@include`, `@extends/@section/@yield/@parent`,
  `@push/@prepend/@stack`, `@verbatim`, `@lang`, `@csrf`, `@method`, `@auth/@guest`,
  components (`<x-name>`, slots, `$attributes`).
- Expressions are SpEL with Blade conveniences, evaluated in a sandbox (no `T()`, constructors,
  bean references, or reflection via `class`/`getClass()`).
- Errors name the template and line (unclosed blocks point at the opening tag); nesting depth is capped.
- Extensible: every directive implements `io.github.djamelkorei.looma.spi.Directive`; custom ones are `Directive` beans.
- `looma.check-templates` parses all templates at startup and reports every syntax error at once.
- Spring Boot starter: auto-configured engine and `ViewResolver`, `looma.*` properties,
  `request` / `principal` / CSRF / `MessageSource` integration.
- `looma.template-dir` for live reload from disk; startup check also reports missing `@include`/`@extends`/
  `<x-...>` targets.
- Tested on Spring Boot 3.3, 3.4, 3.5, 4.0 and 4.1, Java 21 and 25, including Spring Security.
- Licensed under Apache-2.0; published to GitHub Packages as `io.github.djamelkorei`.
- Security audit: closed a sandbox bypass where SpEL's cached property accessors skipped the blocked-type check
  inside loops, and blocked method calls on the template's own scope (`#root.put(...)`). Abuse cases are covered by
  `SecurityTest`. Added `SECURITY.md`, private vulnerability reporting, Dependabot and CodeQL.
- Rendering is about 1.8× faster (fast path for simple expressions, fewer allocations).
- Template helpers: `{{ String.format(...) }}`, `String.join`, `Math.max`... built in, and your own via
  `TemplateHelper` beans. Class access through `T(...)` stays blocked.
- Form binding (`@bind`, `@field`, `@name`, `@value`, `@checkbox`, `@radio`, `@option`) backed by Spring's
  `BindingResult`; boolean attributes (`@checked`, `@selected`, `@disabled`, `@readonly`, `@required`);
  conditional includes (`@includeIf`, `@includeWhen`, `@includeUnless`, `@includeFirst`, `@each`).
- Permissions: `@can`, `@cannot`, `@canany` (Spring Security authorities and `PermissionEvaluator`, fail closed).
- Helpers: `Number`, `Date`, `List` and many more `String` functions, formatted for the visitor's locale; opt-in
  Spring bean access with `@bean.method()` (`@TemplateAccessible`, `looma.beans.*`).
- `@empty($x)`, `@props` with defaults for components, and fragment rendering (`@fragment`, view name
  `"page :: fragment"`, `engine.renderFragment`) for htmx-style partial updates.
- Fixed: `@unless` failed for non-boolean values (lists, strings); it now uses the same truthiness as `@if`.
