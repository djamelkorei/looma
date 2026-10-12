---
title: Migrating from Thymeleaf
---

# Migrating from Thymeleaf

You can move one page at a time. Looma's view resolver only claims a view when a `*.looma.html` template exists
for it, so Thymeleaf keeps serving every page you have not converted yet.

## Run both engines

1. Add the [starter](installation.md). Keep `spring-boot-starter-thymeleaf`.
2. Convert a page by creating `templates/name.looma.html` next to `templates/name.html`. The controller does not change.
3. Delete the Thymeleaf file once the Looma one is live. When none are left, remove the Thymeleaf starter.

Looma registers its view resolver just ahead of Thymeleaf's (`Ordered.LOWEST_PRECEDENCE - 10`), so the Looma file
wins when both exist. Adjust with a `LoomaViewResolver` bean of your own if you need a different order.

The startup check parses every `*.looma.html` and fails the application on a syntax error or a reference to a
missing layout, include or component, so a half-converted page is found on boot rather than by a visitor.

## Side by side

The same three snippets in both engines. Pick a tab once and every example on the page follows it.

**A list with an empty state**

=== "Thymeleaf"

    ```html
    <ul>
      <li th:each="post, s : ${posts}"
          th:classappend="${s.first} ? 'first'"
          th:text="${post.title}"></li>
      <li th:if="${posts.isEmpty()}">No posts yet</li>
    </ul>
    ```

=== "Looma"

    ```html
    <ul>
    @forelse($posts as $post)
      <li @class({'first': $loop->first})>{{ $post.title }}</li>
    @empty
      <li>No posts yet</li>
    @endforelse
    </ul>
    ```

**A form field with its validation error**

=== "Thymeleaf"

    ```html
    <form th:action="@{/signup}" th:object="${form}" method="post">
      <input th:field="*{email}"
             th:classappend="${#fields.hasErrors('email')} ? 'invalid'">
      <small th:if="${#fields.hasErrors('email')}" th:errors="*{email}"></small>
    </form>
    ```

=== "Looma"

    ```html
    <form action="/signup" method="post">
      @csrf
      @bind('form')
        <input @field('email') @class({'invalid': $errors.containsKey('email')})>
        @error('email')<small>{{ $message }}</small>@enderror
      @endbind
    </form>
    ```

**The signed-in user, a role and a message**

=== "Thymeleaf"

    ```html
    <nav>
      <span sec:authorize="isAuthenticated()" sec:authentication="name"></span>
      <a sec:authorize="hasRole('ADMIN')" href="/admin" th:text="#{nav.admin}"></a>
    </nav>
    ```

=== "Looma"

    ```html
    <nav>
      @auth<span>{{ $principal.name }}</span>@endauth
      @auth('ADMIN')<a href="/admin">@lang('nav.admin')</a>@endauth
    </nav>
    ```

## Attribute by attribute

Thymeleaf puts logic in attributes on existing elements; Looma puts it in the text. Most attributes map one to one.

| Thymeleaf | Looma |
|---|---|
| `th:text="${x}"` | `{{ $x }}` |
| `th:utext="${html}"` | `{!! $html !!}` |
| `[[${x}]]` / `[(${x})]` | `{{ $x }}` / `{!! $x !!}` |
| `th:if="${c}"` / `th:unless="${c}"` | `@if($c) ... @endif` / `@unless($c) ... @endunless` |
| `th:switch` / `th:case` | `@switch($x) @case(1) ... @break @default ... @endswitch` |
| `th:each="item, s : ${items}"` | `@foreach($items as $item)`; `s.index` is `$loop->index`, `s.first` is `$loop->first`, `s.count` is `$loop->iteration` |
| `th:each` over a map | `@foreach($map as $key => $value)` |
| empty-list fallback with a second `th:if` | `@forelse ... @empty ... @endforelse` |
| `th:insert="~{partials/nav}"` / `th:replace` | `@include('partials.nav')` |
| `th:replace="~{frag :: name(${a})}"` (parameterised fragment) | `@include('frag', {'a': $a})` or a component `<x-frag :a="$a" />` |
| Layout Dialect `layout:decorate` / `layout:fragment` | `@extends('layouts.app')` / `@section('content')` with `@yield('content')` in the layout |
| `th:classappend="${c} ? 'active'"` | `@class({'active': $c})` |
| `th:styleappend` | `@style({'display: none': $hidden})` |
| `th:attr="data-id=${id}"` / `th:data-id="${id}"` | `data-id="{{ $id }}"` |
| `th:href="@{/posts/{id}(id=${p.id})}"` | `href="/posts/{{ $p.id }}"` |
| `th:checked`, `th:selected`, `th:disabled`, `th:readonly`, `th:required` | `@checked($c)`, `@selected($c)`, `@disabled($c)`, `@readonly($c)`, `@required($c)` |
| `th:object="${form}"` + `th:field="*{email}"` | `@bind('form') ... <input @field('email')> ... @endbind` |
| `th:field` on a checkbox, radio or `<option>` | `@checkbox('tags', 'A')`, `@radio('plan', 'pro')`, `@option('country', 'FR')` |
| `#fields.hasErrors('email')` + `th:errors="*{email}"` | `@error('email') {{ $message }} @enderror` |
| `#fields.hasGlobalErrors()` | `@error('_global')` |
| CSRF hidden input (added by `th:action`) | `@csrf` |
| `th:method="put"` | `@method('PUT')` |
| `#{key}` / `#{key(${arg})}` | `@lang('key')` / `@lang('key', $arg)` |
| `sec:authorize="isAuthenticated()"` | `@auth ... @endauth` |
| `sec:authorize="hasRole('ADMIN')"` | `@auth('ADMIN') ... @endauth` |
| `sec:authorize="hasAuthority('posts:edit')"` | `@can('posts:edit') ... @endcan` |
| `sec:authorize="hasPermission(#post, 'edit')"` | `@can('edit', $post) ... @endcan` |
| `th:inline="javascript"` with `/*[[${data}]]*/` | `<script>const data = @json($data);</script>` |
| `<!--/* comment */-->` | `{{-- comment --}}` |
| `th:remove`, `th:block` | not needed: text outside a directive is just text |
| `${@bean.method()}` | `@bean.method()` for beans you mark `@TemplateAccessible` or list in `looma.beans.allowed` ([configuration](configuration.md#spring-beans-in-templates)) |
| `#strings`, `#numbers`, `#temporals`, `#lists` | `String`, `Number`, `Date`, `List` and `Math` helpers, e.g. `{{ Date.format($d, 'd MMM yyyy') }}` ([helpers](syntax.md#calling-java-methods)) |
| `view :: fragment` for partial responses | the same view name, `@fragment('rows')` in the template ([htmx](syntax.md#partial-updates-htmx)) |

## Expressions

Thymeleaf uses SpEL (with Spring) or OGNL; Looma uses SpEL too, so `${user.name}`, `${a > b}`, `${list.size()}`,
`${x ?: 'default'}` and `${s?.length()}` all work as they are once you drop the `${ }` wrapper. The differences:

- A leading `$` on a variable is optional, and `->` works as `.`, so Blade-style `$user->name` is fine.
- `??` is the Elvis operator (`?:`): it returns the fallback for null and for an empty string.
- `@if` uses PHP-style truthiness: null, `false`, `0`, `""` and an empty collection are false.
- Literal maps use SpEL syntax: `{'key': value}`.
- `T(...)`, `new`, `#root` tricks and bean references other than `@bean` are refused. Beans are reachable only if
  you opt in. Thymeleaf exposes every bean; Looma does not.
- `#request`, `#session` and `#servletContext` are not exposed. `request` is in the model for the role checks;
  put what a page needs into the model from the controller.

## What changes in the layout

Thymeleaf's natural templates open as static HTML in a browser; Looma templates are text and do not. In exchange
the page reads top to bottom as the page it renders. A typical layout:

```html
<!doctype html>
<html lang="en">
<head>
  <title>@yield('title', 'My app')</title>
  <link rel="stylesheet" href="/app.css">
  @stack('head')
</head>
<body>
  @include('partials.nav')
  <main>@yield('content')</main>
  @stack('scripts')
</body>
</html>
```

```html
@extends('layouts.app')
@section('title', 'Posts')

@push('scripts')<script src="/posts.js"></script>@endpush

@section('content')
  <x-alert type="info">Saved!</x-alert>
  ...
@endsection
```

`@push` replaces the Layout Dialect's `layout:fragment` for head and script blocks, and `@parent` appends to the
layout's own section instead of replacing it. A `@push` can sit anywhere in the page, inside a section or not.

## Things to check before you go live

- `{{ }}` escapes; `{!! !!}` does not. Anything that was `th:utext` deserves a second look.
- Vue, Alpine or Handlebars braces in a page: write them as `@{{ ... }}` or wrap the block in `@verbatim`.
- `@` in text is only a directive when followed by a known directive name and its arguments, so email addresses
  and Alpine's `@click` are untouched. Write `@@if` for a literal `@if`.
- Component templates live in `templates/components/`; `<x-form.input>` maps to `components/form/input.looma.html`.
- Enable `looma.template-dir=src/main/resources/templates` in development for live reload.

The full feature-by-feature picture, including what Looma does not have, is in
[Looma vs Thymeleaf](comparison.md).
