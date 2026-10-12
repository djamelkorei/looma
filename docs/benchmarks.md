---
title: Benchmarks
---

# Benchmarks

Numbers for orientation, not a ranking. Engines differ in features that cost time (Thymeleaf's natural templating,
for example), and your pages are not ours. Measure your own pages before deciding anything.

**At a glance**, Looma 0.1.3 against Thymeleaf 3.1.5 on a 2-CPU server. Pick a page:

<div class="looma-bench" data-looma-benchmark>
<div class="looma-bench__tabs" role="tablist" aria-label="Page type">
<button type="button" class="looma-bench__tab" role="tab" data-page-tab="light" aria-selected="false">Light page</button>
<button type="button" class="looma-bench__tab" role="tab" data-page-tab="medium" aria-selected="false">Medium page</button>
<button type="button" class="looma-bench__tab" role="tab" data-page-tab="heavy" aria-selected="true">Heavy page</button>
</div>
<div class="looma-bench__panel" role="tabpanel" data-page-panel="light">
<p class="looma-bench__caption"><strong>Light page</strong> · a list of 20 projects, 2.5 KB</p>
<div class="looma-metric">
<div class="looma-metric__head"><span>CPU per page<em>lower is better</em></span><span class="looma-metric__ratio">3.9× less</span></div>
<div class="looma-bar looma-bar--looma"><span>Looma</span><span class="looma-bar__track"><span class="looma-bar__fill" style="--w:25.8%"></span></span><span class="looma-bar__value">0.08 ms</span></div>
<div class="looma-bar looma-bar--thyme"><span>Thymeleaf</span><span class="looma-bar__track"><span class="looma-bar__fill" style="--w:100%"></span></span><span class="looma-bar__value">0.31 ms</span></div>
</div>
<div class="looma-metric">
<div class="looma-metric__head"><span>Pages per second, server full<em>higher is better</em></span><span class="looma-metric__ratio">3.6× more</span></div>
<div class="looma-bar looma-bar--looma"><span>Looma</span><span class="looma-bar__track"><span class="looma-bar__fill" style="--w:100%"></span></span><span class="looma-bar__value">25,726</span></div>
<div class="looma-bar looma-bar--thyme"><span>Thymeleaf</span><span class="looma-bar__track"><span class="looma-bar__fill" style="--w:27.7%"></span></span><span class="looma-bar__value">7,130</span></div>
</div>
<div class="looma-metric">
<div class="looma-metric__head"><span>Median page time, 8 users<em>lower is better</em></span><span class="looma-metric__ratio">2.2× faster</span></div>
<div class="looma-bar looma-bar--looma"><span>Looma</span><span class="looma-bar__track"><span class="looma-bar__fill" style="--w:45.1%"></span></span><span class="looma-bar__value">0.23 ms</span></div>
<div class="looma-bar looma-bar--thyme"><span>Thymeleaf</span><span class="looma-bar__track"><span class="looma-bar__fill" style="--w:100%"></span></span><span class="looma-bar__value">0.51 ms</span></div>
</div>
</div>
<div class="looma-bench__panel" role="tabpanel" data-page-panel="medium">
<p class="looma-bench__caption"><strong>Medium page</strong> · a 50-row table with a component per row, 12.6 KB</p>
<div class="looma-metric">
<div class="looma-metric__head"><span>CPU per page<em>lower is better</em></span><span class="looma-metric__ratio">7.5× less</span></div>
<div class="looma-bar looma-bar--looma"><span>Looma</span><span class="looma-bar__track"><span class="looma-bar__fill" style="--w:13.5%"></span></span><span class="looma-bar__value">0.27 ms</span></div>
<div class="looma-bar looma-bar--thyme"><span>Thymeleaf</span><span class="looma-bar__track"><span class="looma-bar__fill" style="--w:100%"></span></span><span class="looma-bar__value">2.0 ms</span></div>
</div>
<div class="looma-metric">
<div class="looma-metric__head"><span>Pages per second, server full<em>higher is better</em></span><span class="looma-metric__ratio">7.1× more</span></div>
<div class="looma-bar looma-bar--looma"><span>Looma</span><span class="looma-bar__track"><span class="looma-bar__fill" style="--w:100%"></span></span><span class="looma-bar__value">7,211</span></div>
<div class="looma-bar looma-bar--thyme"><span>Thymeleaf</span><span class="looma-bar__track"><span class="looma-bar__fill" style="--w:14.1%"></span></span><span class="looma-bar__value">1,019</span></div>
</div>
<div class="looma-metric">
<div class="looma-metric__head"><span>Median page time, 8 users<em>lower is better</em></span><span class="looma-metric__ratio">4.5× faster</span></div>
<div class="looma-bar looma-bar--looma"><span>Looma</span><span class="looma-bar__track"><span class="looma-bar__fill" style="--w:22.0%"></span></span><span class="looma-bar__value">0.48 ms</span></div>
<div class="looma-bar looma-bar--thyme"><span>Thymeleaf</span><span class="looma-bar__track"><span class="looma-bar__fill" style="--w:100%"></span></span><span class="looma-bar__value">2.18 ms</span></div>
</div>
</div>
<div class="looma-bench__panel" role="tabpanel" data-page-panel="heavy">
<p class="looma-bench__caption"><strong>Heavy page</strong> · a dashboard over 100 projects, 320 KB</p>
<div class="looma-metric">
<div class="looma-metric__head"><span>CPU per page<em>lower is better</em></span><span class="looma-metric__ratio">8.7× less</span></div>
<div class="looma-bar looma-bar--looma"><span>Looma</span><span class="looma-bar__track"><span class="looma-bar__fill" style="--w:11.4%"></span></span><span class="looma-bar__value">6.6 ms</span></div>
<div class="looma-bar looma-bar--thyme"><span>Thymeleaf</span><span class="looma-bar__track"><span class="looma-bar__fill" style="--w:100%"></span></span><span class="looma-bar__value">58 ms</span></div>
</div>
<div class="looma-metric">
<div class="looma-metric__head"><span>Pages per second, server full<em>higher is better</em></span><span class="looma-metric__ratio">8.1× more</span></div>
<div class="looma-bar looma-bar--looma"><span>Looma</span><span class="looma-bar__track"><span class="looma-bar__fill" style="--w:100%"></span></span><span class="looma-bar__value">282</span></div>
<div class="looma-bar looma-bar--thyme"><span>Thymeleaf</span><span class="looma-bar__track"><span class="looma-bar__fill" style="--w:12.4%"></span></span><span class="looma-bar__value">35</span></div>
</div>
<div class="looma-metric">
<div class="looma-metric__head"><span>Median page time, 8 users<em>lower is better</em></span><span class="looma-metric__ratio">23× faster</span></div>
<div class="looma-bar looma-bar--looma"><span>Looma</span><span class="looma-bar__track"><span class="looma-bar__fill" style="--w:4.3%"></span></span><span class="looma-bar__value">8.8 ms</span></div>
<div class="looma-bar looma-bar--thyme"><span>Thymeleaf</span><span class="looma-bar__track"><span class="looma-bar__fill" style="--w:100%"></span></span><span class="looma-bar__value">205 ms</span></div>
</div>
</div>
</div>

The more a page does (loops, components, formatted values), the larger the gap. On very small pages, Spring MVC,
Spring Security and the network take most of the time in both engines.

## 1. Real pages on a small server

A Spring Boot application with each page written for both engines, served over HTTP to a logged-in user, the way a
real application runs.

**Server:** one Docker container limited to **2 CPUs and 4 GB of memory**, Java 25 (Eclipse Temurin), G1 garbage
collector, heap at 75% of the container's memory. Spring Boot 3.5.16 with Spring Security 6.5, Looma 0.1.3,
Thymeleaf 3.1.5 with its Spring Security extras. Template caches on in both engines.

**Load:** [k6](https://k6.io) in a separate container on the same Docker network, so the load generator never uses the
server's CPUs. Each engine is warmed up first, then each measurement runs 30 seconds, alternating engines. CPU time
comes from the container's own accounting. Every request returned 200 and the server logged no errors.

**The pages** produce the same HTML in both engines (checked before measuring: same text, same tags and
attributes):

| Page | What it contains | Size |
|---|---|---|
| Light | Layout and navigation, a list of 20 projects with a link, the owner and one condition | 2.5 KB |
| Medium | Layout and navigation, a 50-row table: currency, dates, initials, plurals, a status badge component per row, odd and even rows, pagination | 12.6 KB |
| Heavy | Layout and navigation, a dashboard over 100 projects and their tasks and comments: components with named slots, includes, nested loops, grouping and sorting, permission checks, a stack, about 30 formatting helpers per project | 320 KB |

Formatting that Thymeleaf has no built-in for (currency with a code, initials, plurals...) goes through a Spring bean
that calls the same helper classes Looma uses, so both pages produce identical text.

### Results

| Page | Concurrent users | Looma pages/s | Thymeleaf pages/s | Looma median | Thymeleaf median |
|---|---|---|---|---|---|
| Light | 2 | 9,717 | 5,905 | 0.16 ms | 0.29 ms |
| Light | 8 | 21,964 | 6,755 | 0.23 ms | 0.51 ms |
| Light | 32 | 25,726 | 7,130 | 0.68 ms | 1.63 ms |
| Medium | 2 | 5,266 | 1,372 | 0.32 ms | 1.35 ms |
| Medium | 8 | 7,355 | 1,150 | 0.48 ms | 2.18 ms |
| Medium | 32 | 7,211 | 1,019 | 1.61 ms | 8.95 ms |
| Heavy | 2 | 340 | 48 | 5.6 ms | 39.6 ms |
| Heavy | 8 | 304 | 35 | 8.8 ms | 205 ms |
| Heavy | 32 | 282 | 35 | 102 ms | 898 ms |

How to read it:

- **With few users**, the server has spare CPU and the page time is what a visitor waits. On the light page both
  engines answer in well under a millisecond, so Looma's lead there (1.6×) is mostly invisible.
- **Once the 2 CPUs are busy** (8 and 32 users), throughput is set by the CPU each page costs, and requests that
  cannot be served at once wait in line. That is where the difference is felt: the heavy page takes 9 ms with Looma
  and 205 ms with Thymeleaf at 8 users.
- The slowest requests (95th percentile, not shown) rise to about 80 ms for Looma under full load because Docker
  pauses a container that has used up its CPU share for the moment; the same applies to Thymeleaf.

## 2. The heavy page on a laptop

The same application without container limits, on an Apple M2 Pro (12 cores), load from k6 on the same machine,
8 concurrent users, 30-second runs after warm-up:

| | Looma | Thymeleaf |
|---|---|---|
| Pages per second | 960 | 150 |
| Median page time | 8.5 ms | 50 ms |
| CPU per page | 5 ms | 50 ms |

## 3. The engines alone (JMH)

The same small table in each engine (a loop, an escaped value, a conditional and the loop counter per row), rendered
in-process, warm, templates cached, byte-identical output (checked before measuring). Average time per render over
2 forks × 6 iterations; error margins under 1%. Apple M2 Pro, OpenJDK 25.0.4, Thymeleaf 3.1.5, Pebble 4.1.2.

| Rows | Looma | Pebble | Thymeleaf |
|---|---|---|---|
| 10 | **2.6 µs** | 4.2 µs | 12.0 µs |
| 200 | **49.4 µs** | 86.4 µs | 227.8 µs |

On this simple table Looma is about **1.7× faster than Pebble** and **4.6× faster than Thymeleaf**. A table this
simple was already fast in earlier versions; the work below matters most on real pages.

## What makes Looma fast

The heavy page, slowed down about 120 times. Each bar is the CPU work for one page: 6.6 ms with Looma,
58 ms with Thymeleaf. The labels show what each engine does on every request.

<div class="looma-race" data-looma-race>
<div class="looma-race__lane" data-lane data-ms="6.6">
<div class="looma-race__head"><strong>Looma</strong><span>pages rendered: <b data-count>8</b></span></div>
<div class="looma-race__track"></div>
<div class="looma-race__steps"><span class="looma-race__step" data-at="0">copy the prepared HTML</span><span class="looma-race__step" data-at="0.2">run the directives</span><span class="looma-race__step" data-at="0.45">evaluate the expressions</span><span class="looma-race__step" data-at="0.85">write the page</span></div>
</div>
<div class="looma-race__lane looma-race__lane--thyme" data-lane data-ms="58">
<div class="looma-race__head"><strong>Thymeleaf</strong><span>pages rendered: <b data-count>1</b></span></div>
<div class="looma-race__track"></div>
<div class="looma-race__steps"><span class="looma-race__step" data-at="0">walk every HTML tag</span><span class="looma-race__step" data-at="0.12">run the th: processors</span><span class="looma-race__step" data-at="0.4">send each ${…} to SpEL</span><span class="looma-race__step" data-at="0.72">look up request attributes</span><span class="looma-race__step" data-at="0.8">rebuild the fragments</span><span class="looma-race__step" data-at="0.93">write the page</span></div>
</div>
<div class="looma-race__foot"><button type="button" class="looma-btn" data-action="replay">Replay</button></div>
</div>

- **Static HTML is prepared once.** A template is split into text and directives when it is first loaded; rendering
  copies the text as is. Thymeleaf processes the page tag by tag on every render, looking for `th:` attributes.
- **Expressions are evaluated directly (0.1.3).** Spring's SpEL rebuilds type information on every property read
  and resolves methods by reflection; on real pages it took nearly half of the time. Looma now evaluates the
  common expression shapes itself (variables, getters and records, method calls, arithmetic, comparisons,
  conditions, `??`, inline lists and maps) with SpEL's exact rules and the same sandbox. Anything else goes to SpEL
  one step at a time, so results and error messages are identical and every getter or method runs once. About
  16,000 expressions are compared against SpEL in the test suite.
- **Formats are reused (0.1.3).** Number, currency and date formats are built once per locale and copied, getter
  lookups for `List.sortBy` and friends are cached, and word splitting no longer uses regular expressions.
- **Variables and components are cheap.** Variables live in a small scope instead of being looked up through the
  servlet request, and components are pre-parsed templates.
- Earlier (0.1.0): simple property reads skip SpEL, loops reuse their `loop` object, numbers skip escaping and
  escaping copies untouched text in bulk.

## Reproduce

The engine benchmark is in this repository:

```bash
mvn -Pbench -pl looma-bench -am package -DskipTests
mvn -Pbench -pl looma-bench dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
java -cp looma-bench/target/classes:looma-core/target/classes:$(cat /tmp/cp.txt) org.openjdk.jmh.Main EngineBenchmark
```

The HTTP test runs the application in a container started like this, and drives it from a k6 container on the same
network:

```bash
docker run --cpus=2 --memory=4g --memory-swap=4g --network bench app-image
```
