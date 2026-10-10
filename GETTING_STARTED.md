# Getting started

About ten minutes. Every snippet here exists in [`looma-demo`](looma-demo), where it is tested.

## 1. Add it

Add the repository and dependency from the [README](README.md#install) (GitHub Packages needs a token). That is
the only setup: Spring Boot auto-configures the engine and the view resolver.

## 2. Your first page

`src/main/resources/templates/hello.looma.html`

```blade
<h1>Hello, {{ $name }}!</h1>
```

```java
@Controller
class HelloController {
    @GetMapping("/hello")
    String hello(Model model) {
        model.addAttribute("name", "Ada <script>");   // escaped automatically
        return "hello";                               // -> templates/hello.looma.html
    }
}
```

`{{ }}` always HTML-escapes. Use `{!! !!}` only for markup you trust.

**Develop with live reload:** `looma.template-dir=src/main/resources/templates`. Edit, refresh, done.

## 3. A shared layout

`templates/layouts/app.looma.html`

```blade
<!DOCTYPE html>
<html>
<head>
    <title>@yield('title', 'My app')</title>
    @stack('head')
</head>
<body>
    @include('partials.nav')
    <main>@yield('content')</main>
    @stack('scripts')
</body>
</html>
```

`templates/hello.looma.html`

```blade
@extends('layouts.app')

@section('title', 'Hello')

@section('content')
    <h1>Hello, {{ $name }}!</h1>
    @push('scripts')<script>console.log('only on this page')</script>@endpush
@endsection
```

## 4. Loops and conditions

```blade
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

[`signup.looma.html`](looma-demo/src/main/resources/templates/signup.looma.html) and
[`SignupController`](looma-demo/src/main/java/io/github/djamelkorei/looma/demo/SignupController.java):

```blade
<form method="post" action="/signup">
    @csrf
    <input name="email" value="{{ $form.email }}" @class({'invalid': $errors.containsKey('email')})>
    @error('email')<small class="error">{{ $message }}</small>@enderror
    <button>Create account</button>
</form>
```

```java
@PostMapping("/signup")
String submit(@Valid @ModelAttribute("form") SignupForm form, BindingResult result) {
    return result.hasErrors() ? "signup" : "signup-done";   // BindingResult feeds @error, no glue code
}
```

With Spring Security, `@csrf` renders the token the filter expects (tested in `looma-it`), and
`@auth('ADMIN') ... @endauth` / `@guest` follow the signed-in user.

## 6. A component

`templates/components/alert.looma.html`

```blade
<div {{ $attributes->except('type')->merge({'class': 'alert alert-' + $type}) }}>{{ $slot }}</div>
```

```blade
<x-alert type="info" class="mb-4">Saved!</x-alert>
```

Components see only what you pass them. Named slots: `<x-slot:title>…</x-slot:title>` becomes `$title`.

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

```blade
Total: @money($order.total)
```

Block directives, loops and more: [ARCHITECTURE.md](ARCHITECTURE.md).

## 8. Before you ship

* Keep `looma.cache=true` (the default) and leave `looma.template-dir` unset in production.
* The app fails at startup, naming template and line, if any template has a syntax error or points at a
  missing `@include` / `@extends` / `<x-…>`. Boot it once in CI and typos never reach users.
* Templates are trusted code. Never build template names from request input. See
  [the security model](README.md#security-model).

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
