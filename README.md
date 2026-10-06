# Looma

A Blade-style template engine for Spring Boot.

```
looma-core                  parser, AST, renderer (depends only on spring-expression)
looma-spring-boot-starter   auto-configured engine + ViewResolver
looma-demo                  sample app
```

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
| `looma.cache` | `true` | set `false` in development |
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

## Build

    mvn test          # Java 21+
