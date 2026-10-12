---
title: Template syntax
---

# Template syntax

Every directive in one place. The built-in ones are ordinary classes in `looma-core/.../directive`; [write your own](extending.md) the same way.

## How the pieces fit

Two short walk-throughs before the reference: how a page and its layout become one page, and how a component
becomes plain HTML. Click a step, or let them play.

### Layouts and sections

<div class="looma-walk" data-looma-walkthrough>
<div class="looma-walk__tabs" role="tablist" aria-label="How layouts and sections fit">
<button type="button" class="looma-walk__tab" role="tab" data-step="0" aria-selected="true"><b>Step 1</b>The layout leaves holes</button>
<button type="button" class="looma-walk__tab" role="tab" data-step="1" aria-selected="false"><b>Step 2</b>A page fills them</button>
<button type="button" class="looma-walk__tab" role="tab" data-step="2" aria-selected="false"><b>Step 3</b>Looma puts them together</button>
</div>
<div class="looma-walk__panel" role="tabpanel" data-panel="0">
<div><span class="looma-walk__file">layouts/app.looma.html</span><pre><code class="language-looma">&lt;html&gt;
&lt;head&gt;&lt;title&gt;@yield('title', 'My app')&lt;/title&gt;@stack('head')&lt;/head&gt;
&lt;body&gt;
  @include('partials.nav')
  &lt;main&gt;@yield('content')&lt;/main&gt;
&lt;/body&gt;
&lt;/html&gt;</code></pre></div>
<div class="looma-walk__note"><strong>A layout is a page with named holes.</strong> <code>@yield</code> marks where a page's sections go, <code>@stack</code> where pushed tags go, and <code>@include</code> pulls in a partial.</div>
</div>
<div class="looma-walk__panel" role="tabpanel" data-panel="1">
<div><span class="looma-walk__file">posts.looma.html</span><pre><code class="language-looma">@extends('layouts.app')

@section('title', 'Posts')
@push('head')&lt;link rel="stylesheet" href="/posts.css"&gt;@endpush

@section('content')
  &lt;h1&gt;Posts&lt;/h1&gt;
@endsection</code></pre></div>
<div class="looma-walk__note"><strong>The page names its layout and fills the holes by name.</strong> A short section can be written inline, like the title, and a <code>@push</code> can sit anywhere in the page.</div>
</div>
<div class="looma-walk__panel" role="tabpanel" data-panel="2">
<div><span class="looma-walk__file">Rendered HTML</span><pre><code>&lt;html&gt;
&lt;head&gt;&lt;title&gt;<span class="filled" style="--i:0">Posts</span>&lt;/title&gt;<span class="filled" style="--i:1">&lt;link rel="stylesheet" href="/posts.css"&gt;</span>&lt;/head&gt;
&lt;body&gt;
  <span class="filled" style="--i:2">&lt;nav&gt;…&lt;/nav&gt;</span>
  &lt;main&gt;
  <span class="filled" style="--i:3">&lt;h1&gt;Posts&lt;/h1&gt;</span>
&lt;/main&gt;
&lt;/body&gt;
&lt;/html&gt;</code></pre></div>
<div class="looma-walk__note"><strong>One page comes out.</strong> Each hole holds what the page gave it, or the default: without a title section the page would read <em>My app</em>. Rendered by Looma, blank lines removed.</div>
</div>
<div class="looma-walk__controls">
<button type="button" class="looma-btn" data-action="prev" aria-label="Previous step">Back</button>
<button type="button" class="looma-btn" data-action="play" aria-pressed="true">Pause</button>
<button type="button" class="looma-btn" data-action="next" aria-label="Next step">Next</button>
</div>
</div>

### Components and slots

<div class="looma-walk" data-looma-walkthrough>
<div class="looma-walk__tabs" role="tablist" aria-label="How components and slots work">
<button type="button" class="looma-walk__tab" role="tab" data-step="0" aria-selected="true"><b>Step 1</b>A component declares props</button>
<button type="button" class="looma-walk__tab" role="tab" data-step="1" aria-selected="false"><b>Step 2</b>A page uses it like a tag</button>
<button type="button" class="looma-walk__tab" role="tab" data-step="2" aria-selected="false"><b>Step 3</b>The page gets plain HTML</button>
</div>
<div class="looma-walk__panel" role="tabpanel" data-panel="0">
<div><span class="looma-walk__file">components/alert.looma.html</span><pre><code class="language-looma">@props({'type': 'info'})
&lt;div {{ $attributes-&gt;merge({'class': 'alert alert-' + $type}) }}&gt;
  @isset($title)&lt;strong&gt;{{ $title }}&lt;/strong&gt;@endisset
  {{ $slot }}
&lt;/div&gt;</code></pre></div>
<div class="looma-walk__note"><strong>A component is a template in <code>components/</code>.</strong> <code>@props</code> lists what it accepts, with defaults. Every other attribute lands in <code>$attributes</code>, and the body arrives as <code>$slot</code>.</div>
</div>
<div class="looma-walk__panel" role="tabpanel" data-panel="1">
<div><span class="looma-walk__file">any template</span><pre><code class="language-looma">&lt;x-alert type="warning" class="mb-4"&gt;
  &lt;x-slot:title&gt;Heads up&lt;/x-slot:title&gt;
  Your trial ends in {{ $days }} days.
&lt;/x-alert&gt;</code></pre></div>
<div class="looma-walk__note"><strong>Attributes become variables.</strong> <code>type</code> fills the prop, <code>class</code> is merged into <code>$attributes</code>, <code>&lt;x-slot:title&gt;</code> becomes <code>$title</code>, and the rest is the slot. Prefix an attribute with <code>:</code> to pass an expression instead of text, as in <code>:count="$items.size()"</code>.</div>
</div>
<div class="looma-walk__panel" role="tabpanel" data-panel="2">
<div><span class="looma-walk__file">Rendered HTML</span><pre><code>&lt;div class="<span class="filled" style="--i:0">alert alert-warning mb-4</span>"&gt;
  &lt;strong&gt;<span class="filled" style="--i:1">Heads up</span>&lt;/strong&gt;
  <span class="filled" style="--i:2">Your trial ends in 3 days.</span>
&lt;/div&gt;</code></pre></div>
<div class="looma-walk__note"><strong>Nothing of the component is left but its HTML.</strong> Without a <code>type</code> it would read <code>alert alert-info</code>, and without a title slot the <code>&lt;strong&gt;</code> is left out. Rendered by Looma with <code>$days = 3</code>, blank lines removed.</div>
</div>
<div class="looma-walk__controls">
<button type="button" class="looma-btn" data-action="prev" aria-label="Previous step">Back</button>
<button type="button" class="looma-btn" data-action="play" aria-pressed="true">Pause</button>
<button type="button" class="looma-btn" data-action="next" aria-label="Next step">Next</button>
</div>
</div>

## All directives

| Write | What it does |
|---|---|
| `{{ $x }}` / `{!! $x !!}` | escaped / raw output |
| `{{-- comment --}}`, `@{{ x }}`, `@@` | comment, literal braces, literal `@` |
| `@if @elseif @else @endif`, `@unless`, `@isset`, `@empty($x)` | conditionals; `@unless` and `@empty` use the same truthiness as `@if` (null, false, 0, `""` and empty collections are empty) |
| `@switch($x) @case(1) ... @break @default ... @endswitch` | switch (stacked `@case`s share a body) |
| `@foreach($xs as $x)`, `@foreach($map as $k => $v)`, `@forelse ... @empty ... @endforelse` | loops; `$loop->index/iteration/count/first/last/odd/even/remaining/parent/depth`; `$k => $v` gives map entries, or index and item for a list (a map iterated as `$e` has `$e->key`, `$e->value`) |
| `@for($i = 0; $i < 10; $i++)` ... `@endfor` | counted loop; step is `++`, `--`, `+= n` or `-= n` on the loop variable (`$i` is local to the loop) |
| `@while($it.hasNext())` ... `@endwhile` | loops while the condition is truthy; the body must change it (e.g. `$it.next()`) |
| `@break`, `@continue`, `@break($cond)`, `@continue($cond)` | in any loop; `@break` in a `@switch` ends the switch; both are errors outside a loop |
| `@extends('layouts.app')`, `@section`, `@yield('x', 'default')`, `@parent`, `@show` | layouts |
| `@include('partials.nav', {'n': 1})` | partials, inheriting the current scope |
| `@includeIf('promo')`, `@includeWhen($cond, 'x')`, `@includeUnless($cond, 'x')` | include only if the template exists / the condition holds |
| `@includeFirst({'themes.dark.nav', 'nav'})` | the first template of the list that exists |
| `@each('partials.row', $orders, 'order', 'partials.empty')` | one include per item (with `$loop`), or the empty view |
| `@push('js')`, `@prepend`, `@stack('js')` | stacks, usable before the pushes happen; a page that extends a layout can push from anywhere in it |
| `<x-alert type="info" :count="items.size()" title="Hi {{ $name }}">..</x-alert>` | components from `templates/components/`; `:attr` is an expression, `{{ }}` interpolates inside a plain attribute |
| `<x-slot:title>..</x-slot:title>` | named slots; the body is `$slot` |
| `@props({'type': 'info', 'dismissible': false})` | at the top of a component: defaults for missing props; declared props are removed from `$attributes` |
| `@fragment('rows') ... @endfragment` | a part of the page that can be rendered alone ([htmx](#partial-updates-htmx)) |
| `{{ $attributes->except('type')->merge({'class': 'a'}) }}` | attribute bag |
| `@csrf`, `@method('PUT')` | hidden inputs |
| `@checked($c)`, `@selected($c)`, `@disabled($c)`, `@readonly($c)`, `@required($c)` | writes the boolean attribute only when the condition is truthy |
| `@bind('form') ... @endbind` | binds a model object for the field directives below ([form binding](#form-binding)) |
| `@field('email')`, `@name('bio')`, `@value('bio')` | `id` + `name` + `value` / `id` + `name` / the escaped value |
| `@checkbox('newsletter')`, `@checkbox('roles', 'ADMIN')`, `@radio('plan', 'pro')`, `@option('country', 'FR')` | checked / selected from the bound object; checkboxes include Spring's `_field` marker |
| `@auth ... @endauth`, `@guest` | signed in / not signed in (based on `principal`) |
| `@auth('ADMIN')`, `@auth('ADMIN', 'EDITOR')` | signed in **and** in one of the roles (`request.isUserInRole`; with Spring Security a role `ADMIN` matches authority `ROLE_ADMIN`) |
| `@can('posts:edit')`, `@can('edit', $post)`, `@cannot(...)`, `@canany('a', 'b')` | [permissions](#permissions): an authority, or a permission on an object (Spring Security `PermissionEvaluator`); denied when unsure |
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

Components run in an isolated scope: they see their attributes, slots and `$attributes`, plus the request-level
variables `errors`, `principal`, `request` and the `_`-prefixed ones (`_csrf`, ...), so `@error`, `@csrf`, `@auth`,
`@can`, `@lang` and form binding work inside a component. In the Spring starter every view gets `request`,
`principal` and, when Spring Security's CSRF filter is active, `_csrf`. Model attributes with the same names win.

## Calling Java methods

**Methods on a value** work directly, so all of `String`'s instance methods are available on any text:

```html
{{ $name.toUpperCase() }}   {{ $name.substring(0, 3) }}   {{ $title.replace('-', ' ') }}
{{ $email.split('@')[0] }}  {{ $items.size() }}            @if($name.startsWith('A')) ... @endif
```

**Class (static) methods** go through *helpers*, objects that expose a chosen set of methods. Two are built in:

| Helper | Methods |
|---|---|
| `String` | `format`, `formatLocale('fr', fmt, args...)`, `join`, `valueOf`, `isEmpty`, `isBlank`, `defaultIfEmpty`, `defaultIfBlank`, `abbreviate(s, 20)`, `truncateWords(s, 10)`, `wordCount`, `capitalize`, `uncapitalize`, `initials`, `substringBefore`, `substringAfter`, `slug`, `plural(n, 'item', 'items')`, `mask(card, 4)`, `nl2br` (escapes first) |
| `Number` | `format(n, '#,##0.00')`, `decimal(n, 2)`, `integer`, `percent(0.25)`, `currency(n, 'EUR')`, `compact` (1.2K), `bytes` (1.5 KB), `ordinal` (1st), `sequence(1, 10)` |
| `Date` | `format(d, 'd MMMM yyyy')`, `shortFormat` / `medium` / `longFormat`, `iso`, `relative` ("3 hours ago"), `daysBetween`, `isToday`, `isPast`, `isFuture`, `dayName`, `monthName`, `now`, `today` |
| `List` | `size`, `isEmpty`, `first`, `last`, `contains`, `sort`, `sortBy(list, 'name')`, `sortByDesc`, `reverse`, `take`, `skip`, `chunk(list, 3)`, `distinct`, `pluck(list, 'price')`, `groupBy(list, 'team')`, `sum`, `avg`, `min`, `max` |
| `Math` | `max`, `min`, `abs` (whole numbers stay whole), `round`, `ceil`, `floor`, `pow`, `sqrt` |

`Number` and `Date` use the visitor's locale (in Spring, the request locale): `Number.currency(1234.5, 'EUR')` is
`€1,234.50` in English and `1 234,50 €` in French. `Date.relative` is in English. All helpers are null-safe and never
modify the lists you pass.

```html
{{ String.format('%.2f €', $price) }}     {{ String.join(', ', $tags) }}
{{ Math.ceil($total / 20.0) }} pages      @if(String.isBlank($bio)) No bio yet @endif
```

**Spring beans**, like Thymeleaf's `${@bean.method()}`: `{{ @priceService.format($order.total) }}`. Only beans
annotated `@TemplateAccessible` (or listed in `looma.beans.allowed`) are reachable, so a template cannot call your
repositories by accident; see [configuration](configuration.md#spring-beans-in-templates).

Add your own with a `TemplateHelper` bean ([see Extending](extending.md#template-helpers)). A model attribute with
the same name as a helper wins.

**Why not `T(String).format(...)`?** `T(...)` can reach *any* class, including `java.lang.Runtime`, so allowing it
would let a template run system commands. Helpers give you the useful functions without that door; `T(...)`,
`new`, and reflection stay blocked.

## Form binding

`@bind('form')` binds the model attribute `form`; inside it, field directives read and name the fields:

```html
<form method="post" action="/signup">
    @csrf
    @bind('form')
        <input type="email" @field('email') @class({'invalid': $errors.containsKey('email')})>
        @error('email')<small>{{ $message }}</small>@enderror

        <textarea @name('bio')>@value('bio')</textarea>
        <input type="password" @name('password')>            {{-- never echo a password back --}}

        <label>@checkbox('newsletter') Send me news</label>
        @foreach($allRoles as $role)
            <label>@checkbox('roles', $role) {{ $role }}</label>
        @endforeach

        <label><input type="radio" @radio('plan', 'free')> Free</label>
        <label><input type="radio" @radio('plan', 'pro')> Pro</label>

        <select @name('country')>
            <option @option('country', 'FR')>France</option>
            <option @option('country', 'DE')>Germany</option>
        </select>
    @endbind
</form>
```

With the Spring starter, values come from Spring's `BindingResult`, exactly like `th:field`: they are formatted
by your converters (`@DateTimeFormat`, `@NumberFormat`...), and **after a failed submit each field shows what the
user typed**, even input that could not be converted (`"abc"` in a number field). `@checkbox` writes Spring's
hidden `_field` marker, so unticking a box is submitted. Nested paths work: `@field('address.city')`.

## Permissions

| | |
|---|---|
| `@can('posts:edit') ... @else ... @endcan` | the user has this authority (exact match; roles are `ROLE_...`) |
| `@can('edit', $post) ... @endcan` | the user has the permission on this object |
| `@cannot(...) ... @endcannot` | the opposite |
| `@canany('posts:edit', 'posts:delete') ... @endcanany` | at least one of the authorities |

With Spring Security, authorities come from the signed-in user, and object permissions from your
`PermissionEvaluator` bean (the same one `@PreAuthorize("hasPermission(#post, 'edit')")` uses):

```java
@Bean
PermissionEvaluator permissionEvaluator() {
    return new PermissionEvaluator() {
        public boolean hasPermission(Authentication auth, Object target, Object permission) {
            return target instanceof Post post && "edit".equals(permission) && post.owner().equals(auth.getName());
        }
        public boolean hasPermission(Authentication auth, Serializable id, String type, Object permission) {
            return false;
        }
    };
}
```

Checks **fail closed**: anonymous users have no authorities, there is no permission without a `PermissionEvaluator`,
and without Spring Security only servlet roles are checked. Raw security expressions (`sec:authorize="hasRole(...)"`)
are deliberately not supported: they run in Spring Security's unrestricted expression language, outside Looma's
sandbox.

## Partial updates (htmx)

Wrap the part of a page you want to refresh in `@fragment`, and render only that part:

```html
{{-- contacts.looma.html --}}
@extends('layouts.app')
@section('content')
    <input hx-get="/contacts/search" hx-trigger="keyup changed delay:300ms" hx-target="#rows" name="q">
    <table><tbody id="rows">
        @fragment('rows')
            @foreach($contacts as $c)<tr><td>{{ $c.name }}</td></tr>@endforeach
        @endfragment
    </tbody></table>
@endsection
```

```java
@GetMapping("/contacts")        String page(Model m)                  { m.addAttribute("contacts", all());     return "contacts"; }
@GetMapping("/contacts/search") String rows(@RequestParam String q, Model m) { m.addAttribute("contacts", find(q)); return "contacts :: rows"; }
```

`"contacts :: rows"` (the same syntax as Thymeleaf) returns only the fragment's HTML; in a normal render the markers
are invisible. The fragment can sit inside a layout section, a loop or an include. Without Spring:
`engine.renderFragment("contacts", "rows", model)`.
