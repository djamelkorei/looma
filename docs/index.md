---
title: Looma
hide:
  - navigation
  - toc
---

<div class="looma-hero" markdown>

<div markdown>

# Templates that read like the page they render.

<p class="lead" markdown>Looma brings Laravel Blade's syntax to Spring Boot. Write HTML with <code>{{ }}</code> and <code>@directives</code>, and get layouts, components, validation errors and CSRF without attributes bolted onto your markup. Drop-in for a Spring MVC controller, with a sandboxed expression language and a startup check that refuses to boot on a broken template.</p>

<div class="actions" markdown>
[Get started](getting-started.md){ .md-button .md-button--primary }
[Coming from Thymeleaf?](thymeleaf-migration.md){ .md-button }
</div>

</div>

<div class="looma-compare" markdown>

<div markdown>
<div class="label looma">Looma</div>
```html
@extends('layouts.app')

@section('content')
  <h1>Hello, {{ $user.name }}!</h1>

  @forelse($posts as $post)
    <li @class({'first': $loop->first})>
      {{ $post.title }}
    </li>
  @empty
    <li>No posts yet</li>
  @endforelse

  <form method="post" action="/posts">
    @csrf
    <input @field('title')>
    @error('title')<small>{{ $message }}</small>@enderror
  </form>
@endsection
```
</div>

<div markdown>
<div class="label">Thymeleaf</div>
```html
<html layout:decorate="~{layouts/app}">
<div layout:fragment="content">
  <h1 th:text="|Hello, ${user.name}!|"></h1>

  <th:block th:if="${!posts.isEmpty()}">
    <li th:each="post, s : ${posts}"
        th:classappend="${s.first} ? 'first'"
        th:text="${post.title}"></li>
  </th:block>
  <li th:if="${posts.isEmpty()}">No posts yet</li>

  <form method="post" th:action="@{/posts}"
        th:object="${form}">
    <input th:field="*{title}">
    <small th:if="${#fields.hasErrors('title')}"
           th:errors="*{title}"></small>
  </form>
</div>
```
</div>

</div>

</div>

<div class="looma-facts" markdown>
<div><strong>4× faster</strong> than Thymeleaf on the same page, measured with JMH. <a href="benchmarks/">Benchmarks</a></div>
<div><strong>Fails at startup</strong> on a syntax error or a missing layout, include or component, with file and line.</div>
<div><strong>Sandboxed</strong> expressions: no <code>T()</code>, constructors, reflection or bean access unless you opt in.</div>
<div><strong>Java 21 and 25</strong> × Spring Boot 3.3 to 4.0, including Spring Security, tested on every push.</div>
</div>

## Install

One dependency. Templates go in `src/main/resources/templates/` as `*.looma.html` and controllers return the view name as usual.

=== "Maven"

    ```xml
    <dependency>
      <groupId>io.github.djamelkorei</groupId>
      <artifactId>looma-spring-boot-starter</artifactId>
      <version>0.1.2</version>
    </dependency>
    ```

=== "Gradle"

    ```kotlin
    implementation("io.github.djamelkorei:looma-spring-boot-starter:0.1.2")
    ```

## What you get

<div class="grid cards" markdown>

-   **Layouts, components, slots and stacks**

    ---

    `@extends` / `@section` inheritance, `<x-alert>` components with `@props` and named slots, `@push` into the
    layout's `<head>`, `@once` for a component's styles. No layout dialect to add.

    [:octicons-arrow-right-24: Template syntax](syntax.md)

-   **Forms that know Spring**

    ---

    `@bind` and `@field` fill `name`, `id` and `value` from the model object, keep the rejected input after a
    failed `@Valid` submit, and `@error` shows the message from your `MessageSource`.

    [:octicons-arrow-right-24: Form binding](syntax.md#form-binding)

-   **Safe by default**

    ---

    Escaped output, a sandboxed SpEL subset, bounded loops and recursion, buffered output so an error never sends
    half a page, and `@auth` / `@can` that fail closed.

    [:octicons-arrow-right-24: Security & operations](security.md)

-   **Built to be extended**

    ---

    Every directive is one small class behind a public API. Yours are plain Spring beans; so are template helpers
    and the beans you expose with `@TemplateAccessible`.

    [:octicons-arrow-right-24: Extending & architecture](extending.md)

</div>

## Start here

1. [Install it](installation.md): one dependency, no other setup.
2. [Getting started](getting-started.md): a page, a layout, a validated form and a component in ten minutes.
3. [Migrating from Thymeleaf](thymeleaf-migration.md): the `th:*` to Looma mapping, and how to run both engines while you move.

Looma is open source under the [Apache License 2.0](https://github.com/djamelkorei/looma/blob/main/LICENSE)
and lives on [GitHub](https://github.com/djamelkorei/looma).
