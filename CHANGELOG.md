# Changelog

## 0.1.0 (unreleased)

First version.

- Core engine: `@for`, `@while`, `@break`, `@continue`, `{{ }}`, `{!! !!}`, comments, `@if/@elseif/@else/@unless/@isset/@switch`,
  `@foreach/@forelse` with `$loop`, `@include`, `@extends/@section/@yield/@parent`,
  `@push/@prepend/@stack`, `@verbatim`, `@lang`, `@csrf`, `@method`, `@auth/@guest`,
  components (`<x-name>`, slots, `$attributes`).
- Expressions are SpEL with Blade conveniences, evaluated in a sandbox (no `T()`, constructors,
  bean references, or reflection via `class`/`getClass()`).
- Errors name the template and line; nesting depth is capped.
- Spring Boot starter: auto-configured engine and `ViewResolver`, `looma.*` properties,
  `request` / `principal` / CSRF / `MessageSource` integration.
