package io.github.djamelkorei.looma.bench;

import io.github.djamelkorei.looma.LoomaEngine;
import io.github.djamelkorei.looma.TemplateLoader;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.StringLoader;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Renders the same table (a loop, an escaped value, a conditional and the loop counter per row) with each
 * engine, warm and with its template cache on, which is how an application runs in production.
 *
 * <p>Run: {@code mvn -Pbench -pl looma-bench -am package -DskipTests} then see BENCHMARKS.md.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 4, time = 1)
@Measurement(iterations = 6, time = 1)
@Fork(2)
public class EngineBenchmark {

    public static class Row {
        private final int id;

        public Row(int id) { this.id = id; }

        public int getId() { return id; }
        public String getName() { return "Name <" + id + ">"; }
        public boolean isActive() { return id % 2 == 0; }
    }

    @Param({"10", "200"})
    public int rows;

    private Map<String, Object> model;
    private LoomaEngine looma;
    private TemplateEngine thymeleaf;
    private PebbleTemplate pebble;

    @Setup
    public void setup() throws Exception {
        List<Row> list = new ArrayList<>();
        for (int i = 0; i < rows; i++) list.add(new Row(i));
        model = Map.of("rows", list);

        String loomaSource = "<table>@foreach($rows as $r)<tr class=\"{{ $loop->even ? 'e' : 'o' }}\"><td>{{ $r.id }}</td>"
                + "<td>{{ $r.name }}</td><td>@if($r.active)yes@else no@endif</td></tr>@endforeach</table>";
        looma = LoomaEngine.builder(new TemplateLoader() {
            public boolean exists(String name) { return true; }
            public String load(String name) { return loomaSource; }
        }).cache(true).build();

        // Resolved by name from a file, as in a real application (the string resolver would use the whole
        // template text as its cache key, which is not how anyone runs Thymeleaf).
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("thymeleaf/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(true);
        thymeleaf = new TemplateEngine();
        thymeleaf.setTemplateResolver(resolver);

        pebble = new PebbleEngine.Builder().loader(new StringLoader()).build().getTemplate(
                "<table>{% for r in rows %}<tr class=\"{{ loop.index % 2 == 1 ? 'e' : 'o' }}\"><td>{{ r.id }}</td>"
                        + "<td>{{ r.name }}</td><td>{% if r.active %}yes{% else %} no{% endif %}</td></tr>{% endfor %}</table>");
    }

    @Benchmark
    public String looma() {
        return looma.render("page", model);
    }

    @Benchmark
    public String thymeleaf() {
        Context ctx = new Context();
        ctx.setVariables(model);
        return thymeleaf.process("page", ctx);
    }

    @Benchmark
    public String pebble() throws Exception {
        StringWriter out = new StringWriter();
        pebble.evaluate(out, model);
        return out.toString();
    }
}
