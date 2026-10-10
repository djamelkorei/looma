---
title: IntelliJ plugin
---

# IntelliJ plugin

`looma-intellij/` is a plugin for IntelliJ IDEA (2025.2 and newer, Community or Ultimate) and the other
JetBrains IDEs. It gives `*.looma.html` templates the same help Java code gets.

## What it does

**Highlighting and checks.** Directives, echoes and expressions are highlighted on top of full HTML support.
What the engine would reject when it parses a template is reported as you type, worded like the engine's
messages: unclosed or stray block tags, `@break` outside a loop, unclosed `{{`, and missing templates or
components.

**Navigation** (Ctrl+Click / Cmd+Click, Find Usages, Rename):

| From | To |
|---|---|
| `@include('partials.nav')`, `@extends`, `@includeWhen`, `@each`, ... | the template; renaming or moving the file updates the names |
| `@section('content')` / `@yield('content')` | the other side along the layout chain |
| `@push('scripts')` / `@stack('scripts')` | the other side, in any template |
| `<x-alert>`, `<x-slot:title>` | the component template, the `$title` it reads |
| `$post` in `@foreach($posts as $post)` | the loop variable; `$loop` to its loop |
| `@lang('greeting')` | the key in `messages.properties` |
| `return "home";` in a `@Controller`, `new ModelAndView("home")`, `engine.render("home", ...)` | the template |
| `$user` | the `model.addAttribute("user", ...)`, `@ModelAttribute` or form object that provides it |
| `$user->name`, `$post.title()` | the getter, record accessor, field or method, typed through loops and maps |
| `Price.eur(...)`, `@priceService.format(...)` | your `TemplateHelper` / `@TemplateAccessible` class and method |
| `@bind('form')`, `@field('address.city')` | the model attribute, then each property of the path |

Layouts and partials see the model of the templates that extend or include them; components only see their
attributes.

**Completion** for directives (with their end tag), template, section and stack names, message keys, model
variables, Java properties and methods, helpers, beans, `$loop->...`, component tags and their attributes
(the variables the component reads), and form field paths. `:attr="..."` values of components are full
expressions too.

**Editing.** Typing `{{` gives `{{ | }}` (a `-` right after makes it a comment), `{!!` gives `{!! | !!}`;
Comment Line uses `{{-- --}}`; block bodies fold; the caret on `@if` highlights its `@else` and `@endif`;
Quick Documentation (F1 / Ctrl+Q) explains directives; the Structure view lists blocks and sections; File |
New | Looma Template creates a page, layout or component. Colors are under Settings | Editor | Color
Scheme | Looma.

Custom directives are picked up from your code: a class implementing `Directive` whose `name()`,
`syntax()` and `tags()` return constants is lexed, completed and checked like a built-in one.

## Install

The plugin is not on the JetBrains Marketplace yet. Build it and install the zip:

```bash
cd looma-intellij
./gradlew buildPlugin            # build/distributions/looma-intellij-*.zip
```

Then Settings | Plugins | ⚙ | Install Plugin from Disk.

## Develop

```bash
./gradlew runIde                 # a sandbox IDE with the plugin
./gradlew test                   # needs looma-core compiled for the sync tests: mvn -pl looma-core compile
./gradlew verifyPlugin           # binary compatibility with the oldest supported IDE and the current one
```

Pass `-PlocalIdePath="/Applications/IntelliJ IDEA.app"` to build against an installed IDE instead of
downloading one.

The plugin keeps its own table of the built-in directives and helpers (it cannot load the engine).
`BuiltinDirectivesInSyncTest` and `BuiltinHelpersInSyncTest` compare them with the compiled engine, so a
new directive or helper fails the plugin build until it is added; `scripts/update-helpers.sh` regenerates
the helper list.
