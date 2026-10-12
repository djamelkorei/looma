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

<div class="looma-proof">
<a href="https://central.sonatype.com/artifact/io.github.djamelkorei/looma-spring-boot-starter"><img src="https://img.shields.io/maven-central/v/io.github.djamelkorei/looma-spring-boot-starter.svg?label=Maven%20Central&amp;color=2B3A8F" alt="Latest version on Maven Central"></a>
<a href="https://github.com/djamelkorei/looma/actions/workflows/ci.yml"><img src="https://github.com/djamelkorei/looma/actions/workflows/ci.yml/badge.svg" alt="Build status"></a>
<a href="https://github.com/djamelkorei/looma/blob/main/LICENSE"><img src="https://img.shields.io/badge/license-Apache%202.0-2B3A8F.svg" alt="Apache 2.0 license"></a>
<span>Open source, tested on Java 21 and 25 with Spring Boot 3.3 to 4.0.</span>
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
<div><strong>4× to 9× less CPU</strong> than Thymeleaf for the same pages, from a simple list to a heavy dashboard. <a href="benchmarks/">Benchmarks</a></div>
<div><strong>Fails at startup</strong> on a syntax error or a missing layout, include or component, with file and line.</div>
<div><strong>Sandboxed</strong> expressions: no <code>T()</code>, constructors, reflection or bean access unless you opt in.</div>
<div><strong>Java 21 and 25</strong> × Spring Boot 3.3 to 4.0, including Spring Security, tested on every push.</div>
</div>

## How a page is rendered

Four steps, from the template you write to the HTML your visitor gets. Click a step, or let it play.

<div class="looma-walk" data-looma-walkthrough>
<div class="looma-walk__tabs" role="tablist" aria-label="How Looma renders a page">
<button type="button" class="looma-walk__tab" role="tab" data-step="0" aria-selected="true"><b>Step 1</b>You write a template</button>
<button type="button" class="looma-walk__tab" role="tab" data-step="1" aria-selected="false"><b>Step 2</b>Looma prepares it once</button>
<button type="button" class="looma-walk__tab" role="tab" data-step="2" aria-selected="false"><b>Step 3</b>Your controller adds data</button>
<button type="button" class="looma-walk__tab" role="tab" data-step="3" aria-selected="false"><b>Step 4</b>Each request fills the gaps</button>
</div>
<div class="looma-walk__panel" role="tabpanel" data-panel="0">
<pre><code class="language-looma">&lt;h1&gt;Hello, {{ $user.name }}!&lt;/h1&gt;
&lt;ul&gt;
@foreach($posts as $post)
  &lt;li&gt;{{ $post.title }}&lt;/li&gt;
@endforeach
&lt;/ul&gt;</code></pre>
<div class="looma-walk__note"><strong>A template is HTML with gaps.</strong> <code>{{ }}</code> prints a value, escaped for HTML. Directives such as <code>@foreach</code> and <code>@if</code> control the page. The HTML around them stays exactly as you wrote it.</div>
</div>
<div class="looma-walk__panel" role="tabpanel" data-panel="1">
<pre><code><span class="chunk chunk-text" style="--i:0">&lt;h1&gt;Hello, </span><span class="chunk chunk-expr" style="--i:1">{{ $user.name }}</span><span class="chunk chunk-text" style="--i:2">!&lt;/h1&gt;
&lt;ul&gt;
</span><span class="chunk chunk-dir" style="--i:3">@foreach($posts as $post)</span><span class="chunk chunk-text" style="--i:4">
  &lt;li&gt;</span><span class="chunk chunk-expr" style="--i:5">{{ $post.title }}</span><span class="chunk chunk-text" style="--i:6">&lt;/li&gt;
</span><span class="chunk chunk-dir" style="--i:7">@endforeach</span><span class="chunk chunk-text" style="--i:8">
&lt;/ul&gt;</span></code></pre>
<div class="looma-walk__note"><strong>Parsed once, then cached.</strong> Plain HTML becomes ready-made text. Only the <span class="key key-expr">expressions</span> and <span class="key key-dir">directives</span> are left for each request. A syntax error stops the app at startup, with the file and line.</div>
</div>
<div class="looma-walk__panel" role="tabpanel" data-panel="2">
<pre><code>@GetMapping("/")
String home(Model model) {
    model.addAttribute("user", new User("Ada"));
    model.addAttribute("posts", List.of(
            new Post("Hello Looma"),
            new Post("Layouts and components")));
    return "home";   // templates/home.looma.html
}</code></pre>
<div class="looma-walk__note"><strong>Plain Spring MVC.</strong> Model attributes become <code>$user</code> and <code>$posts</code> in the template. Records, getters and maps all work, and so do the request, the signed-in user and validation errors.</div>
</div>
<div class="looma-walk__panel" role="tabpanel" data-panel="3">
<pre><code>&lt;h1&gt;Hello, <span class="filled" style="--i:0">Ada</span>!&lt;/h1&gt;
&lt;ul&gt;
  &lt;li&gt;<span class="filled" style="--i:1">Hello Looma</span>&lt;/li&gt;
  &lt;li&gt;<span class="filled" style="--i:2">Layouts and components</span>&lt;/li&gt;
&lt;/ul&gt;</code></pre>
<div class="looma-walk__note"><strong>Copy the text, fill the gaps.</strong> The prepared HTML is copied as it is and each gap gets its value. Thymeleaf walks every tag on every request instead, which is why the same page costs Looma 4 to 9 times less CPU. <a href="benchmarks/">See the benchmarks</a></div>
</div>
<div class="looma-walk__controls">
<button type="button" class="looma-btn" data-action="prev" aria-label="Previous step">Back</button>
<button type="button" class="looma-btn" data-action="play" aria-pressed="true">Pause</button>
<button type="button" class="looma-btn" data-action="next" aria-label="Next step">Next</button>
</div>
</div>

## Install

One dependency. Templates go in `src/main/resources/templates/` as `*.looma.html` and controllers return the view name as usual.

=== "Maven"

    ```xml
    <dependency>
      <groupId>io.github.djamelkorei</groupId>
      <artifactId>looma-spring-boot-starter</artifactId>
      <version>0.1.3</version>
    </dependency>
    ```

=== "Gradle"

    ```kotlin
    implementation("io.github.djamelkorei:looma-spring-boot-starter:0.1.3")
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
