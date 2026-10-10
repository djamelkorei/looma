# Looma

[![CI](https://github.com/djamelkorei/looma/actions/workflows/ci.yml/badge.svg)](https://github.com/djamelkorei/looma/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**Blade-style templates for Spring Boot.** Write HTML with `{{ }}` and `@directives`, and get layouts, components,
validation errors and CSRF without ceremony.

📖 **Documentation: <https://djamelkorei.github.io/looma/>**

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

## Install

```xml
<dependency>
  <groupId>io.github.djamelkorei</groupId>
  <artifactId>looma-spring-boot-starter</artifactId>
  <version>0.1.0</version>
</dependency>
```

Needs Java 21+ and Spring Boot 3.3+. Put templates in `src/main/resources/templates/` as `*.looma.html` and return
the view name from a controller. Version `0.1.0` is not published yet, so until then build from source
(`mvn install`); see [Installation](https://djamelkorei.github.io/looma/installation/) for GitHub Packages.

## Why Looma

* **Familiar**: if you know Laravel Blade, you know the syntax.
* **Safe by default**: escaped output, a sandboxed expression language, bounded loops, and a startup check that
  fails the app (with file and line) if any template is broken or points at a missing one.
* **Easy to extend**: every directive is one small class behind a public API; yours are plain Spring beans.
* **Fast**: about 4× faster than Thymeleaf and 1.5× faster than Pebble on the same page ([benchmarks](https://djamelkorei.github.io/looma/benchmarks/)).
* **Tested widely**: Java 21 and 25 × Spring Boot 3.3, 3.4, 3.5 and 4.0, including Spring Security, on every push.

## Learn more

[Getting started](https://djamelkorei.github.io/looma/getting-started/) ·
[Template syntax](https://djamelkorei.github.io/looma/syntax/) ·
[Configuration](https://djamelkorei.github.io/looma/configuration/) ·
[Extending & architecture](https://djamelkorei.github.io/looma/extending/) ·
[Security](https://djamelkorei.github.io/looma/security/) ·
[Changelog](CHANGELOG.md)

## Build

```
mvn verify            # Java 21+, Maven 3.9+
mvn -Prelease verify  # also builds sources and javadoc jars
```

## License

[Apache License 2.0](LICENSE) © 2026 Djamel Eddine Korei
