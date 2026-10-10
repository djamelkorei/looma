<p align="center">
  <a href="https://djamelkorei.github.io/looma/"><img src="docs/assets/logo.svg" width="96" height="96" alt="Looma"></a>
</p>

<h1 align="center">Looma</h1>

<p align="center"><strong>Blade-style templates for Spring Boot.</strong><br>
Write HTML with <code>{{ }}</code> and <code>@directives</code>. Get layouts, components, validation errors and CSRF without ceremony.</p>

<p align="center">
  <a href="https://github.com/djamelkorei/looma/actions/workflows/ci.yml"><img src="https://github.com/djamelkorei/looma/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://central.sonatype.com/artifact/io.github.djamelkorei/looma-spring-boot-starter"><img src="https://img.shields.io/maven-central/v/io.github.djamelkorei/looma-spring-boot-starter.svg?color=2B3A8F" alt="Maven Central"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-2B3A8F.svg" alt="License"></a>
  <img src="https://img.shields.io/badge/Java-21%2B-F2A83B.svg" alt="Java 21+">
  <img src="https://img.shields.io/badge/Spring%20Boot-3.3%20to%204.0-F2A83B.svg" alt="Spring Boot 3.3 to 4.0">
</p>

<p align="center">
  <a href="https://djamelkorei.github.io/looma/">Documentation</a> ·
  <a href="https://djamelkorei.github.io/looma/getting-started/">Getting started</a> ·
  <a href="https://djamelkorei.github.io/looma/thymeleaf-migration/">Migrating from Thymeleaf</a> ·
  <a href="https://djamelkorei.github.io/looma/syntax/">Syntax</a> ·
  <a href="CHANGELOG.md">Changelog</a>
</p>

---

```html
@extends('layouts.app')

@section('content')
    <h1>Hello, {{ $user.name }}!</h1>

    @forelse($posts as $post)
        <li @class({'first': $loop->first})>{{ $post.title }}</li>
    @empty
        <li>No posts yet</li>
    @endforelse

    <form method="post" action="/posts">
        @csrf
        <input @field('title')>
        @error('title')<small>{{ $message }}</small>@enderror
    </form>

    <x-alert type="info">Saved!</x-alert>
@endsection
```

## Install

```xml
<dependency>
  <groupId>io.github.djamelkorei</groupId>
  <artifactId>looma-spring-boot-starter</artifactId>
  <version>0.1.1</version>
</dependency>
```

```kotlin
implementation("io.github.djamelkorei:looma-spring-boot-starter:0.1.1")
```

Needs Java 21+ and Spring Boot 3.3+. Put templates in `src/main/resources/templates/` as `*.looma.html` and
return the view name from a controller. Nothing else to configure. More options:
[Installation](https://djamelkorei.github.io/looma/installation/).

## Why Looma

| | |
|---|---|
| **Familiar** | If you know Laravel Blade, you know the syntax: `@extends`, `@section`, `@foreach`, `<x-component>`, `@props`, `@push`, `@once`. |
| **Spring-aware forms** | `@bind` and `@field` fill `name`, `id` and `value` from the model object and keep the rejected input after a failed `@Valid` submit. `@error` shows the message from your `MessageSource`. `@csrf` works with Spring Security. |
| **Safe by default** | Escaped output, a sandboxed SpEL subset (no `T()`, constructors, reflection or bean access unless you opt in), bounded loops and recursion, `@auth` / `@can` that fail closed. |
| **Fails at startup** | Every template is parsed on boot. A syntax error or a reference to a missing layout, include or component stops the app with file and line, instead of a 500 for a visitor. |
| **Fast** | About 4× faster than Thymeleaf and 1.5× faster than Pebble on the same page, measured with JMH. [Benchmarks](https://djamelkorei.github.io/looma/benchmarks/) |
| **Easy to extend** | Every directive is one small class behind a public API. Yours are plain Spring beans, and so are template helpers. |
| **Tested widely** | Java 21 and 25 × Spring Boot 3.3, 3.4, 3.5 and 4.0, including Spring Security, on every push. |

## Coming from Thymeleaf

Looma only claims a view when a `*.looma.html` file exists, so you can convert one page at a time while Thymeleaf
serves the rest. The attributes map almost one to one:

| Thymeleaf | Looma |
|---|---|
| `th:text="${x}"` | `{{ $x }}` |
| `th:each="p, s : ${posts}"` | `@foreach($posts as $p)` with `$loop` |
| `layout:decorate` / `layout:fragment` | `@extends` / `@section` |
| `th:object` + `th:field="*{email}"` | `@bind('form')` + `@field('email')` |
| `th:errors="*{email}"` | `@error('email') {{ $message }} @enderror` |
| `sec:authorize="hasRole('ADMIN')"` | `@auth('ADMIN')` |
| `~{frag :: name(${a})}` | `<x-frag :a="$a" />` |

The full mapping, the expression differences and what to check before going live:
[Migrating from Thymeleaf](https://djamelkorei.github.io/looma/thymeleaf-migration/). An honest feature-by-feature
comparison, gaps included: [Looma vs Thymeleaf](https://djamelkorei.github.io/looma/comparison/).

## Modules

| Module | What it is |
|---|---|
| `looma-spring-boot-starter` | Auto-configuration, view resolver, form binding, Spring Security integration. This is the one you add. |
| `looma-core` | The engine, usable without Spring. |
| `looma-intellij` | IntelliJ plugin: highlighting, completion, navigation to templates, components and Java members. |
| `looma-demo`, `looma-it`, `looma-bench` | Sample app, Spring Security integration tests, JMH benchmarks. |

## Build

```
./mvnw verify            # Java 21+; the wrapper pins Maven 3.9.16
./mvnw -Prelease verify  # also builds sources and javadoc jars
```

## License

[Apache License 2.0](LICENSE) © 2026 Djamel Eddine Korei
