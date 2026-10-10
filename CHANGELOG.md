# Changelog

## 0.1.0 (unreleased)

First version.

- Core engine: `@for`, `@while`, `@break`, `@continue`, `{{ }}`, `{!! !!}`, comments, `@if/@elseif/@else/@unless/@isset/@switch`,
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
