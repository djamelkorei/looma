---
title: Benchmarks
---

# Benchmarks

Numbers for orientation, not a ranking. One machine, one template, and engines differ in features that
cost time (Thymeleaf's natural templating, for example). Measure your own pages before deciding anything.

**Machine:** Apple M2 Pro, macOS, OpenJDK 25.0.4. **Engines:** Looma 0.1.2, Thymeleaf 3.1.5, Pebble 4.1.2.
**Last run:** 2026-10-12.

## 1. Template rendering (JMH)

The same table in each engine (a loop, an escaped value, a conditional and the loop counter per row), warm,
templates cached, byte-identical output (checked before measuring). Average time per render over 2 forks ×
6 iterations, lower is better; the error margins were under 1% except Thymeleaf at 200 rows (±4%).

| rows | Looma | Pebble | Thymeleaf |
|---|---|---|---|
| 10 | **2.7 µs** | 4.3 µs | 12.2 µs |
| 200 | **54.5 µs** | 85.0 µs | 222.3 µs |

On this page Looma is about **1.5× faster than Pebble** and **4× faster than Thymeleaf**.

### What changed (0.1.0 performance work)

| rows | before | after |
|---|---|---|
| 10 | 4.9 µs | 2.9 µs (−42%) |
| 200 | 99.6 µs | 55.1 µs (−45%) |

Profiling showed most of the time went into SpEL's general machinery for reading a simple property
(type descriptors built on every read). Looma now evaluates the most common shapes directly (`user.name`,
`!user.active`, `loop.even ? 'e' : 'o'`) with the same rules SpEL and the sandbox apply, falling back to SpEL for
everything else, so results and error messages are unchanged; a test compares both paths on the tricky cases.
Scopes no longer allocate a hash map or copy the model, loops reuse their `loop` object, numbers skip escaping, and
escaping copies untouched text in bulk.

Reproduce (2 forks, 4 warm-up and 6 measured iterations of 1 s each):

```
mvn -Pbench -pl looma-bench -am package -DskipTests
mvn -Pbench -pl looma-bench dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
java -cp looma-bench/target/classes:looma-core/target/classes:$(cat /tmp/cp.txt) org.openjdk.jmh.Main EngineBenchmark
```

## 2. HTTP, end to end

The demo app's home page (layout + include + component + `@forelse` + `@lang` + `@push`/`@stack`, 627 bytes)
served by the packaged jar, `ab -k`, 30,000 requests per run, cache on, numbers from the third run (steady state
after JIT warm-up; earlier runs were 30–50% lower):

| concurrency | requests/s | p99 | failed |
|---|---|---|---|
| 8 | ~50,000 | <1 ms | 0 |
| 32 | ~58,000 | 1 ms | 0 |

Caveats: the load generator ran on the same laptop as the server, which was also busy (load average about 6),
the page is tiny, there is no database or network latency, and the server log showed no errors. Treat it as
"the engine is not your bottleneck", not as a capacity figure.
