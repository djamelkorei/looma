---
title: Getting started
---

# Getting started

About ten minutes. Every snippet here exists in [`looma-demo`](https://github.com/djamelkorei/looma/tree/main/looma-demo), where it is tested.

<div class="looma-steps" markdown>

## 1. Add it

Add the dependency from [Installation](installation.md). That is
the only setup: Spring Boot auto-configures the engine and the view resolver.

## 2. Your first page

```html title="src/main/resources/templates/hello.looma.html"
<h1>Hello, {{ $name }}!</h1>
```

```java
@Controller
class HelloController {
    @GetMapping("/hello")
    String hello(Model model) {
        model.addAttribute("name", "Ada <script>"); // (1)!
        return "hello"; // (2)!
    }
}
```

1.  Escaped automatically: the page shows `Ada <script>` as text, never as a tag.
2.  The view name. Looma renders `templates/hello.looma.html`, so there is nothing to configure.

`{{ }}` always HTML-escapes. Use `{!! !!}` only for markup you trust.

**Develop with live reload:** `looma.template-dir=src/main/resources/templates`. Edit, refresh, done.

## 3. A shared layout

```html title="templates/layouts/app.looma.html"
<!DOCTYPE html>
<html>
<head>
    <title>@yield('title', 'My app')</title> <!-- (1)! -->
    @stack('head') <!-- (2)! -->
</head>
<body>
    @include('partials.nav')
    <main>@yield('content')</main>
    @stack('scripts')
</body>
</html>
```

1.  The page's `@section('title', ...)`, or `My app` when the page sets none.
2.  Everything pages `@push('head')`, for example a stylesheet only one page needs.

```html title="templates/hello.looma.html"
@extends('layouts.app')

@section('title', 'Hello')

@section('content')
    <h1>Hello, {{ $name }}!</h1>
    @push('scripts')<script>console.log('only on this page')</script>@endpush
@endsection
```

## 4. Loops and conditions

```html
<ul>
@forelse($posts as $post)
    <li @class({'first': $loop->first})>
        {{ $loop->iteration }}/{{ $loop->count }} {{ $post.title }}
        @unless($post.published) (draft) @endunless
    </li>
@empty
    <li>No posts yet</li>
@endforelse
</ul>
```

Expressions are SpEL with Blade conveniences: `$post->title`, `$post.title` and `post.title` are the same;
`$a ?? 'default'` works; `&&`, `||`, `!` work. Also `@for`, `@while`, `@switch`, `@break`, `@continue`.

## 5. A form with validation

[`signup.looma.html`](https://github.com/djamelkorei/looma/blob/main/looma-demo/src/main/resources/templates/signup.looma.html) and
[`SignupController`](https://github.com/djamelkorei/looma/blob/main/looma-demo/src/main/java/io/github/djamelkorei/looma/demo/SignupController.java):

```html
<form method="post" action="/signup">
    @csrf
    @bind('form')
        <input type="email" @field('email') @class({'invalid': $errors.containsKey('email')})>
        @error('email')<small class="error">{{ $message }}</small>@enderror
    @endbind
    <button>Create account</button>
</form>
```

`@bind('form')` + `@field('email')` writes `id`, `name` and `value` from the `form` object. After a failed submit
the field shows what the user typed, as with Thymeleaf's `th:field`. See [form binding](syntax.md#form-binding)
for checkboxes, radios and selects.

```java
@PostMapping("/signup")
String submit(@Valid @ModelAttribute("form") SignupForm form, BindingResult result) {
    return result.hasErrors() ? "signup" : "signup-done"; // (1)!
}
```

1.  On a failed submit the form is shown again: `@error` reads the messages from the `BindingResult` and `@field`
    keeps what the user typed. No glue code.

With Spring Security, `@csrf` renders the token the filter expects (tested in `looma-it`), and
`@auth('ADMIN') ... @endauth` / `@guest` follow the signed-in user.

## 6. A component

```html title="templates/components/alert.looma.html"
<div {{ $attributes->except('type')->merge({'class': 'alert alert-' + $type}) }}>{{ $slot }}</div>
```

```html
<x-alert type="info" class="mb-4">Saved!</x-alert>
```

Declare a component's props and their defaults at its top with `@props({'type': 'info'})`; declared props are
removed from `$attributes`, so only real HTML attributes are written. Components see only what you pass them. Named slots: `<x-slot:title>…</x-slot:title>` becomes `$title`.

## 7. Your own directive

```java
@Component
class MoneyDirective implements Directive {
    public String name() { return "money"; }
    public Node parse(ParseContext p) {
        Expr amount = p.expr();
        return ctx -> ctx.writeEscaped(String.format("%.2f €", ((Number) ctx.eval(amount)).doubleValue()));
    }
}
```

```html
Total: @money($order.total)
```

Block directives, loops and more: [Extending & architecture](extending.md).

## 8. Before you ship

* Keep `looma.cache=true` (the default) and leave `looma.template-dir` unset in production.
* The app fails at startup, naming template and line, if any template has a syntax error or points at a
  missing `@include` / `@extends` / `<x-…>`. Boot it once in CI and typos never reach users.
* Templates are trusted code. Never build template names from request input. See
  [the security model](security.md).

</div>

## Coming from Thymeleaf?

| Thymeleaf | Looma |
|---|---|
| `<p th:text="${user.name}">` | `<p>{{ $user.name }}</p>` |
| `th:utext` | `{!! $html !!}` |
| `th:if` / `th:unless` | `@if` / `@unless` |
| `th:each="p, s : ${posts}"` | `@foreach($posts as $p)` with `$loop` |
| `th:replace` / `th:insert` | `@include('partials.x')` |
| layout dialect `layout:decorate` | `@extends` + `@section` / `@yield` |
| `th:fragment` with parameters | `<x-component :arg="…">` |
| `#{key}` | `@lang('key')` |
| `th:field`, `#fields.hasErrors` | `@error('field')` |

The main difference: Looma templates are not valid HTML on their own (no natural templating), which is what
lets them read like plain text with directives.
