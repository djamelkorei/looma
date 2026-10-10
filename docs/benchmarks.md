---
title: Benchmarks
---

# Benchmarks

Numbers for orientation, not a ranking. One machine, one template, and engines differ in features that
cost time (Thymeleaf's natural templating, for example). Measure your own pages before deciding anything.

**Machine:** Apple M2 Pro, macOS, OpenJDK 25.0.4. **Engines:** Looma (this repo), Thymeleaf 3.1.5, Pebble 4.1.2.

## 1. Template rendering (JMH)

The same table in each engine (a loop, an escaped value, a conditional and the loop counter per row), warm,
templates cached, byte-identical output (checked before measuring). Average time per render, lower is better.

| rows | Looma | Pebble | Thymeleaf |
|---|---|---|---|
| 10 | **4.9 µs** | 5.1 µs | 11.5 µs |
| 200 | 98.7 µs | **85.4 µs** | 231.8 µs |

Reading it honestly: Looma is about **2.3× faster than Thymeleaf** here and **on par with Pebble**
(slightly ahead on a small table, about 15% behind on a larger one). Looma evaluates expressions with SpEL;
Pebble compiles to its own expression nodes, which is probably where the gap on bigger loops comes from.

Reproduce (2 forks, 4 warm-up and 6 measured iterations of 1 s each):

```
mvn -Pbench -pl looma-bench -am package -DskipTests
mvn -Pbench -pl looma-bench dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
java -cp looma-bench/target/classes:looma-core/target/classes:$(cat /tmp/cp.txt) org.openjdk.jmh.Main EngineBenchmark
```

## 2. HTTP, end to end

The demo app's home page (layout + include + component + `@forelse` + `@lang` + `@push`/`@stack`, 627 bytes)
served by the packaged jar, `ab -k`, 30,000 requests after a warm-up, cache on:

| concurrency | requests/s | mean latency | p99 | failed |
|---|---|---|---|---|
| 8 | 24,353 | 0.33 ms | 1 ms | 0 |
| 32 | 44,122 | 0.73 ms | 2 ms | 0 |

Caveats: the load generator runs on the same machine as the server, the page is tiny, there is no database
or network latency, and the server log showed no errors. It shows the engine is not a bottleneck, not what your
application will do.
