---
title: Security & operations
---

# Security & operations

## At a glance

<div class="looma-reach">
<div class="looma-reach__col looma-reach__col--ok">
<p class="looma-reach__title">A template can use</p>
<ul>
<li>model attributes, records, getters and maps</li>
<li>methods on values, such as <code>$name.toUpperCase()</code></li>
<li>the <code>String</code>, <code>Number</code>, <code>Date</code>, <code>List</code> and <code>Math</code> helpers</li>
<li>beans you mark <code>@TemplateAccessible</code></li>
<li><code>request</code>, <code>principal</code> and <code>errors</code></li>
</ul>
</div>
<div class="looma-reach__col looma-reach__col--no">
<p class="looma-reach__title">Blocked, whatever the template says</p>
<ul>
<li><code>T(...)</code> and <code>new</code></li>
<li><code>class</code> and <code>getClass()</code></li>
<li>reflection: <code>Class</code>, <code>Method</code>, <code>ClassLoader</code></li>
<li><code>Runtime</code>, <code>Process</code>, <code>Thread</code></li>
<li>beans you did not opt in</li>
<li>assigning variables</li>
</ul>
</div>
</div>

**Escaping, step by step.** What happens to a value that contains markup when a template prints it with `{{ }}`:

<div class="looma-escape">
<div class="looma-escape__box" style="--i:0"><span class="looma-escape__label">In the model</span><code>Ada &lt;script&gt;alert(1)&lt;/script&gt;</code></div>
<span class="looma-escape__arrow" style="--i:1" aria-hidden="true"><code>{{ $name }}</code></span>
<div class="looma-escape__box" style="--i:2"><span class="looma-escape__label">Sent to the browser</span><code>Ada &amp;lt;script&amp;gt;alert(1)&amp;lt;/script&amp;gt;</code></div>
<span class="looma-escape__arrow" style="--i:3" aria-hidden="true">displayed as</span>
<div class="looma-escape__box looma-escape__box--seen" style="--i:4"><span class="looma-escape__label">The visitor sees text</span><span>Ada &lt;script&gt;alert(1)&lt;/script&gt;</span></div>
</div>

## Security model

- `{{ }}` and `@lang` HTML-escape; only `{!! !!}`, component slots and `$attributes` emit markup as-is.
  Never put untrusted input in `{!! !!}`.
- Templates are **trusted code** written by developers, not by end users. The expression sandbox
  is defense in depth (no `T()`, `new`, bean references, `class`/`getClass()`, or calls on
  `Class`/`ClassLoader`/`Method`/`Runtime`/`Process`); it is not meant for running user-submitted templates.
- Do not build `@include` / `@extends` / `<x-...>` names from request input.
- Template names are validated (`[A-Za-z0-9_-./]`, no `..`), so view names cannot escape the template folder.
- Rendering is buffered, so an error produces a normal error response instead of half a page.
- **Escaping is for HTML text and quoted attributes.** It does not make a value safe as a URL or as JavaScript:
  `<a href="{{ $url }}">` still accepts `javascript:...` if the URL comes from a user, so validate URLs (allow
  `http`/`https` only). Always quote attributes (`title="{{ $x }}"`, never `title={{ $x }}`). For data in scripts use
  `@json`, never `{{ }}`. `@style` with user input can inject CSS.

## Operations

- Parsed templates are immutable and cached per `LoomaEngine` (thread-safe); keep `looma.cache=true` in production.
- Errors are `LoomaException`s naming the template and line, e.g. `In template 'home' at line 12: ...`.
- `@include`/`@extends`/components may nest 100 deep before failing, which stops accidental recursion, and blocks
  may be nested 200 deep in one template (deeper fails with the line instead of overflowing the stack).
- `@for`/`@while` stop with an error after 100,000 passes, so a condition that never turns false cannot hang a request.
- `looma.*` properties have IDE auto-completion through the generated configuration metadata.
