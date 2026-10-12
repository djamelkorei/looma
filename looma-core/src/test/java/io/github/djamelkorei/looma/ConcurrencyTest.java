package io.github.djamelkorei.looma;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ConcurrencyTest {

    private final AtomicInteger loads = new AtomicInteger();
    private final Map<String, String> files = new ConcurrentHashMap<>(Map.of(
            "layout", "<h1>@yield('title')</h1>@stack('js')@yield('body')",
            "page", "@extends('layout')@section('title', $title)@section('body')"
                    + "@foreach($items as $i)[{{ $loop->iteration }}:{{ $i }}]@endforeach"
                    + "@push('js')<s>{{ $title }}</s>@endpush@endsection"));

    private final TemplateLoader loader = new TemplateLoader() {
        @Override public boolean exists(String name) { return files.containsKey(name); }
        @Override public String load(String name) { loads.incrementAndGet(); return files.get(name); }
    };

    @Test
    void parallelRendersDoNotInterfereAndTemplatesAreParsedOnce() throws Exception {
        LoomaEngine engine = new LoomaEngine(loader, true);
        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Future<String>> results = new ArrayList<>();
        for (int t = 0; t < 400; t++) {
            int n = t;
            Callable<String> task = () -> engine.render("page",
                    Map.of("title", "T" + n, "items", List.of("a" + n, "b" + n)));
            results.add(pool.submit(task));
        }
        for (int t = 0; t < 400; t++) {
            assertEquals("<h1>T" + t + "</h1><s>T" + t + "</s>[1:a" + t + "][2:b" + t + "]", results.get(t).get());
        }
        pool.shutdown();
        assertEquals(2, loads.get(), "each template should be loaded and parsed exactly once");
    }

    @Test
    void uncachedEngineSeesTemplateChanges() {
        LoomaEngine engine = new LoomaEngine(loader, false);
        files.put("live", "v1");
        assertEquals("v1", engine.render("live", Map.of()));
        files.put("live", "v2");
        assertEquals("v2", engine.render("live", Map.of()));
    }

    @Test
    void cachedEngineRemembersLookupsEitherWay() {
        LoomaEngine engine = new LoomaEngine(loader, true);
        assertTrue(!engine.exists("later"));
        files.put("later", "x");
        assertTrue(!engine.exists("later"), "a miss is remembered when caching is on (an @includeIf per request)");
        assertTrue(engine.exists("layout"));
        files.remove("layout");
        assertTrue(engine.exists("layout"), "a hit is remembered when caching is on");
    }

    @Test
    void uncachedEngineLooksUpEveryTime() {
        LoomaEngine engine = new LoomaEngine(loader, false);
        assertTrue(!engine.exists("later"));
        files.put("later", "x");
        assertTrue(engine.exists("later"));
    }
}
