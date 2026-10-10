---
title: Configuration
---

# Configuration

Put templates in `src/main/resources/templates/` as `*.looma.html` and return the view name from a controller:

as `*.looma.html`, and return the view name from a controller:

```java
@GetMapping("/") String home(Model m) { m.addAttribute("user", "Ada"); return "home"; }
```

| Property | Default | |
|---|---|---|
| `looma.prefix` | `templates/` | classpath folder |
| `looma.suffix` | `.looma.html` | appended to the view name |
| `looma.cache` | `true` (`false` if `template-dir` is set) | parsed-template cache |
| `looma.template-dir` | unset | load templates from this folder on disk (live reload, no rebuild) |
| `looma.check-templates` | `true` | parse all templates at startup and fail on syntax errors |
| `looma.content-type` | `text/html;charset=UTF-8` | |
| `looma.beans.access` | `annotated` | which Spring beans templates may call as `@name`: `none`, `annotated`, `all` |
| `looma.beans.allowed` | empty | extra bean names templates may call (e.g. library beans you cannot annotate) |

View names use dots or slashes for folders: `admin.users` -> `templates/admin/users.looma.html`.
Unknown views fall through to other `ViewResolver`s.

## Spring beans in templates

`{{ @priceService.format($total) }}` calls a Spring bean, like Thymeleaf's `${@priceService.format(total)}`.
Unlike Thymeleaf, not every bean is reachable by default:

```java
@Service
@TemplateAccessible          // io.github.djamelkorei.looma.spi.TemplateAccessible
public class PriceService { public String format(BigDecimal amount) { ... } }
```

```properties
looma.beans.allowed=urlHelper,featureFlags   # beans you cannot annotate
looma.beans.access=all                       # every bean, like Thymeleaf (not recommended)
looma.beans.access=none                      # no bean access at all
```

A template that names a bean it may not use fails with a message saying how to allow it. Calls still go through
the sandbox (no `getClass()`, reflection, etc.).

## Live reload in development

```properties
looma.template-dir=src/main/resources/templates
```

Templates are read from that folder on every request (caching switches off automatically), so you edit a
`.looma.html` file and refresh the browser, with no rebuild or restart. Leave the property unset in production
to load from the classpath with caching on.

## Fail fast at startup

`looma.check-templates` (default `true`) parses every template at startup and fails the boot with **all**
syntax errors at once, naming template and line, for example
`In template 'orders' at line 12: Unclosed block: @foreach is never closed`. It also reports literal
`@include('x')`, `@extends('x')` and `<x-x>` references to templates that do not exist. Use it in CI too: a context
test that boots the app catches a broken template before it ships. Programmatic access: `engine.check(names)`.

## Compatibility

The full suite (including Spring Security) runs in CI on every push for each of these:

| | Spring Boot 3.3 | 3.4 | 3.5 | 4.0 |
|---|---|---|---|---|
| **Java 21** | ✅ | ✅ | ✅ | ✅ |
| **Java 25** | ✅ | ✅ | ✅ | ✅ |

Spring Boot 4.1 was also run (Java 21) before release but is not in the CI matrix. Java 17 is not supported
(the engine uses Java 21 features).
