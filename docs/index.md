---
title: Looma
hide:
  - navigation
  - toc
---

# Looma

**Blade-style templates for Spring Boot.** Write HTML with `{{ }}` and `@directives` instead of attributes
bolted onto your markup, and get a layout system, components, validation errors and CSRF that just work.

```html
@extends('layouts.app')

@section('content')
    <h1>Hello, {{ $user.name }}!</h1>

    @forelse($posts as $post)
        <li @class({'first': $loop->first})>{{ $post.title }}</li>
    @empty
        <li>No posts yet</li>
    @endforelse

    <x-alert type="info">Saved!</x-alert>
@endsection
```

<div class="grid cards" markdown>

-   **Familiar syntax**

    ---

    If you know Laravel Blade you already know Looma. Layouts, stacks, components, slots, `@error`, `@csrf`.

    [:octicons-arrow-right-24: Template syntax](syntax.md)

-   **Safe by default**

    ---

    Output is escaped, expressions run in a sandbox, loops and recursion are bounded, and a broken template fails
    the app at startup with its file and line.

    [:octicons-arrow-right-24: Security & operations](security.md)

-   **Easy to extend**

    ---

    Every directive is one small class behind a public API. Yours are Spring beans, no engine changes needed.

    [:octicons-arrow-right-24: Extending & architecture](extending.md)

-   **Fast**

    ---

    About 2.3× faster than Thymeleaf and level with Pebble on the same page, measured with JMH.

    [:octicons-arrow-right-24: Benchmarks](benchmarks.md)

</div>

## Start here

1. [Install it](installation.md): one dependency.
2. [Getting started](getting-started.md): a page, a layout, a validated form and a component in ten minutes.
3. [Configuration](configuration.md): the `looma.*` properties, live reload in development.

Looma is open source under the [Apache License 2.0](https://github.com/djamelkorei/looma/blob/main/LICENSE) and
lives on [GitHub](https://github.com/djamelkorei/looma).
