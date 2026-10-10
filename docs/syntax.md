---
title: Template syntax
---

# Template syntax

Every directive in one place. The built-in ones are ordinary classes in `looma-core/.../directive`; [write your own](extending.md) the same way.

| | |
|---|---|
| `{{ $x }}` / `{!! $x !!}` | escaped / raw output |
| `{{-- comment --}}`, `@{{ x }}`, `@@` | comment, literal braces, literal `@` |
| `@if @elseif @else @endif`, `@unless`, `@isset` | conditionals |
| `@switch($x) @case(1) ... @break @default ... @endswitch` | switch (stacked `@case`s share a body) |
| `@foreach($xs as $x)`, `@forelse ... @empty ... @endforelse` | loops; `$loop->index/iteration/count/first/last/odd/even/remaining/parent/depth`; maps give `$e->key`, `$e->value` |
| `@for($i = 0; $i < 10; $i++)` ... `@endfor` | counted loop; step is `++`, `--`, `+= n` or `-= n` on the loop variable (`$i` is local to the loop) |
| `@while($it.hasNext())` ... `@endwhile` | loops while the condition is truthy; the body must change it (e.g. `$it.next()`) |
| `@break`, `@continue`, `@break($cond)`, `@continue($cond)` | in any loop; `@break` in a `@switch` ends the switch; both are errors outside a loop |
| `@extends('layouts.app')`, `@section`, `@yield('x', 'default')`, `@parent`, `@show` | layouts |
| `@include('partials.nav', {'n': 1})` | partials, inheriting the current scope |
| `@push('js')`, `@prepend`, `@stack('js')` | stacks, usable before the pushes happen |
| `<x-alert type="info" :count="items.size()">..</x-alert>` | components from `templates/components/` |
| `<x-slot:title>..</x-slot:title>` | named slots; the body is `$slot` |
| `{{ $attributes->except('type')->merge({'class': 'a'}) }}` | attribute bag |
| `@csrf`, `@method('PUT')` | hidden inputs |
| `@auth ... @endauth`, `@guest` | signed in / not signed in (based on `principal`) |
| `@auth('ADMIN')`, `@auth('ADMIN', 'EDITOR')` | signed in **and** in one of the roles (`request.isUserInRole`; with Spring Security a role `ADMIN` matches authority `ROLE_ADMIN`) |
| `@error('email') {{ $message }} @enderror` | shown only if the field has a validation error; `$message` is the first, `$messages` all. Filled from `@Valid` `BindingResult`s via your `MessageSource`; global errors are under `_global`; `$errors` is the whole map |
| `<li @class('nav', {'active': $on})>` | builds `class="nav active"`; strings always, map keys when truthy, lists flattened; omitted if empty |
| `<p @style('margin: 0', {'color: red': $err})>` | same for `style="margin: 0; color: red;"` |
| `@once ... @endonce` | renders only the first time per page, even inside loops and components (e.g. a component's `<style>`) |
| `@json($data)` | JSON safe inside `<script>` (`< > & '` are escaped); in an attribute use single quotes: `data-x='@json($x)'` |
| `@verbatim ... @endverbatim` | no processing |

Expressions are SpEL with Blade conveniences: a leading `$` is optional, `->` is `.`,
`??` is the Elvis operator, and `&&`, `||`, `!` work. PHP-style truthiness applies to
`@if` (null, `0`, `""` and empty collections are false). Templates run in SpEL's
`SimpleEvaluationContext`: no `T(...)`, constructors or bean references.
Map literals use SpEL syntax: `{'key': value}`.

Components run in an isolated scope: they only see their attributes, slots and `$attributes`.
In the Spring starter every view also gets `request`, `principal` and, when Spring Security's
CSRF filter is active, `_csrf`. Model attributes with the same names win.

## Calling Java methods

**Methods on a value** work directly, so all of `String`'s instance methods are available on any text:

```html
{{ $name.toUpperCase() }}   {{ $name.substring(0, 3) }}   {{ $title.replace('-', ' ') }}
{{ $email.split('@')[0] }}  {{ $items.size() }}            @if($name.startsWith('A')) ... @endif
```

**Class (static) methods** go through *helpers*, objects that expose a chosen set of methods. Two are built in:

| Helper | Methods |
|---|---|
| `String` | `format(fmt, args...)`, `formatLocale('fr', fmt, args...)`, `join(', ', list)` / `join('-', a, b...)`, `valueOf(x)`, `isEmpty(s)`, `isBlank(s)` (null-safe) |
| `Math` | `max`, `min`, `abs` (whole numbers stay whole), `round`, `ceil`, `floor` (return whole numbers), `pow`, `sqrt` |

```html
{{ String.format('%.2f €', $price) }}     {{ String.join(', ', $tags) }}
{{ Math.ceil($total / 20.0) }} pages      @if(String.isBlank($bio)) No bio yet @endif
```

Add your own with a `TemplateHelper` bean ([see Extending](extending.md#template-helpers)). A model attribute with
the same name as a helper wins.

**Why not `T(String).format(...)`?** `T(...)` can reach *any* class, including `java.lang.Runtime`, so allowing it
would let a template run system commands. Helpers give you the useful functions without that door; `T(...)`,
`new`, and reflection stay blocked.
