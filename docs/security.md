---
title: Security & operations
---

# Security & operations

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
