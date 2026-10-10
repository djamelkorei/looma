# Changelog

## 0.1.0 (unreleased)

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
