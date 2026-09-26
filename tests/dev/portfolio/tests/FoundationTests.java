package dev.portfolio.tests;

import dev.portfolio.foundation.*;
import dev.portfolio.foundation.FileStore;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static dev.portfolio.tests.TestSupport.*;

public final class FoundationTests {
    private FoundationTests() {
    }
    public static void run(Supplier<Application> factory) {
        test("JSON nested Unicode and escape round trip", ()-> {
            var data=Json.map("text", "line\n\"quoted\" \\ 🌨", "array", Arrays.asList(true, null, 3.5), "object", Json.map("x", "y"));
            equal(Json.write(data), Json.write(Json.parse(Json.write(data))));
        });
        test("JSON duplicate keys are rejected", ()->raises(IllegalArgumentException.class, ()->Json.parse("{\"a\":1,\"a\":2}")));
        test("JSON trailing data is rejected", ()->raises(IllegalArgumentException.class, ()->Json.parse("{} {}")));
        test("JSON invalid leading-zero number is rejected", ()->raises(IllegalArgumentException.class, ()->Json.parse("01")));
        test("JSON nonfinite output is rejected", ()->raises(IllegalArgumentException.class, ()->Json.write(Double.NaN)));
        test("JSON recursive depth is bounded", ()->raises(IllegalArgumentException.class, ()->Json.parse("[".repeat(100)+"0"+"]".repeat(100))));
        test("JSON empty arrays and escaped control characters", ()-> {
            equal(0, Json.array(Json.parse("[]")).size());
            equal("a\tb", Json.parse("\"a\\tb\""));
        });
        test("Logical paths reject traversal, drives, absolute paths", ()-> {
            for(String path:List.of("../secret", "/tmp/x", "C:/x", "a//b", "a/./b", "a\\b"))problem(400, ()->Checks.logicalPath(path));
            equal("src/main/Foo.java", Checks.logicalPath("src/main/Foo.java"));
        });
        test("Constant-time secret helper distinguishes equal values", ()-> {
            truth(Checks.sameSecret("secret", "secret"));
            truth(!Checks.sameSecret("secret", "Secret"));
        });
        test("Required text validates size and null bytes", ()-> {
            problem(400, ()->Checks.text(" ", "value", 9));
            problem(400, ()->Checks.text("a\0b", "value", 9));
            problem(400, ()->Checks.text("abcd", "value", 3));
        });
        test("Read-only transaction cannot mutate store", ()-> {
            try(Store s=new MemoryStore()) {
                raises(IllegalStateException.class, ()->s.read(tx-> {
                    tx.put("things", "one", Json.map("n", 1));
                    return null;
                }));
            }
        });
        test("Transaction exception rolls back every write", ()-> {
            try(Store s=new MemoryStore()) {
                raises(IllegalStateException.class, ()->s.write(tx-> {
                    tx.put("things", "one", Json.map("n", 1));
                    throw new IllegalStateException();
                }));
                equal(0, s.read(tx->tx.scan("things").size()));
            }
        });
        test("Stored aggregates use defensive copies", ()-> {
            try(Store s=new MemoryStore()) {
                var input=Json.map("n", 1);
                s.write(tx-> {
                    tx.put("things", "one", input);
                    return null;
                });
                input.put("n", 999);
                var copy=s.read(tx->tx.require("things", "one"));
                copy.put("n", 2);
                equal(1, s.read(tx->tx.require("things", "one").get("n")));
            }
        });
        test("Concurrent increments remain serializable", ()-> {
            try(Store s=new MemoryStore(); var pool=Executors.newFixedThreadPool(4)) {
                s.write(tx-> {
                    tx.put("counts", "one", Json.map("n", 0));
                    return null;
                });
                List<Future<?>> work=new ArrayList<>();
                for(int i=0; i<4; i++)work.add(pool.submit(()-> {
                    for(int n=0; n<25; n++)s.write(tx-> {
                        var c=tx.require("counts", "one");
                        c.put("n", Json.integer(c, "n", 0)+1);
                        tx.put("counts", "one", c);
                        return null;
                    });
                }));
                for(var f:work)f.get();
                equal(100, s.read(tx->tx.require("counts", "one").get("n")));
            }
        });
        test("Atomic file store survives close and reopen", ()-> {
            Path dir=Files.createTempDirectory("workbench-reopen-");
            try {
                try(Store s=new FileStore(dir)) {
                    s.write(tx-> {
                        tx.put("things", "one", Json.map("text", "saved"));
                        return null;
                    });
                }
                try(Store s=new FileStore(dir)) {
                    equal("saved", s.read(tx->tx.require("things", "one").get("text")));
                }
            } finally {
                deleteTree(dir);
            }
        });
        test("Workspace file lock rejects a second instance", ()-> {
            Path dir=Files.createTempDirectory("workbench-lock-");
            try(Store s=new FileStore(dir)) {
                raises(IllegalStateException.class, ()->new FileStore(dir));
            } finally {
                deleteTree(dir);
            }
        });
        test("Corrupt workspace fails closed without overwriting", ()-> {
            Path dir=Files.createTempDirectory("workbench-corrupt-");
            try {
                Files.writeString(dir.resolve("workspace.json"), "bad");
                raises(IllegalStateException.class, ()->new FileStore(dir));
                equal("bad", Files.readString(dir.resolve("workspace.json")));
            } finally {
                deleteTree(dir);
            }
        });
        test("Unknown schema version is not silently migrated", ()-> {
            Path dir=Files.createTempDirectory("workbench-schema-");
            try {
                Files.writeString(dir.resolve("workspace.json"), "{\"schemaVersion\":99,\"collections\":{}}");
                raises(IllegalStateException.class, ()->new FileStore(dir));
            } finally {
                deleteTree(dir);
            }
        });
        test("Successful job persists result and hides input", ()-> {
            try(Store s=new MemoryStore(); Jobs jobs=new Jobs(s)) {
                jobs.register("echo", (ctx, p)-> {
                    ctx.progress(1, 1, "complete");
                    return Json.map("answer", 42);
                });
                var j=jobs.enqueue("echo", Json.map("private", "value"), "once");
                truth(!j.containsKey("payload"));
                jobs.start();
                var done=await(jobs, id(j));
                equal("SUCCEEDED", done.get("status"));
                equal(42, object(done.get("result")).get("answer"));
            }
        });
        test("Idempotency returns original job and rejects changed payload", ()-> {
            try(Store s=new MemoryStore(); Jobs jobs=new Jobs(s)) {
                jobs.register("echo", (c, p)->p);
                var first=jobs.enqueue("echo", Json.map("x", 1), "same");
                equal(id(first), id(jobs.enqueue("echo", Json.map("x", 1), "same")));
                problem(409, ()->jobs.enqueue("echo", Json.map("x", 2), "same"));
            }
        });
        test("Queued cancellation never executes handler", ()-> {
            try(Store s=new MemoryStore(); Jobs jobs=new Jobs(s)) {
                jobs.register("echo", (c, p)-> {
                    throw new AssertionError("Cancelled job executed");
                });
                var j=jobs.enqueue("echo", Map.of(), "");
                jobs.cancel(id(j));
                jobs.start();
                equal("CANCELLED", await(jobs, id(j)).get("status"));
                problem(409, ()->jobs.cancel(id(j)));
            }
        });
        test("Running cancellation is cooperative", ()-> {
            try(Store s=new MemoryStore(); Jobs jobs=new Jobs(s)) {
                CountDownLatch entered=new CountDownLatch(1);
                jobs.register("slow", (c, p)-> {
                    entered.countDown();
                    for(int i=0; i<100; i++) {
                        Thread.sleep(5);
                        c.checkCancelled();
                    }
                    return Map.of();
                });
                var j=jobs.enqueue("slow", Map.of(), "");
                jobs.start();
                truth(entered.await(2, TimeUnit.SECONDS));
                jobs.cancel(id(j));
                equal("CANCELLED", await(jobs, id(j)).get("status"));
            }
        });
        test("Unhandled job failure stores safe error instead of private text", ()-> {
            try(Store s=new MemoryStore(); Jobs jobs=new Jobs(s)) {
                jobs.register("bad", (c, p)-> {
                    throw new IllegalStateException("TOP_SECRET_PROMPT");
                });
                var j=jobs.enqueue("bad", Map.of(), "");
                jobs.start();
                var done=await(jobs, id(j));
                equal("FAILED", done.get("status"));
                truth(!Json.string(done, "error", "").contains("TOP_SECRET"));
            }
        });
        test("Restart marks interrupted jobs failed instead of replaying", ()-> {
            try(Store s=new MemoryStore(); Jobs jobs=new Jobs(s)) {
                s.write(tx-> {
                    tx.put("jobs", "interrupted", Json.map("id", "interrupted", "kind", "echo", "status", "RUNNING", "createdAt", Checks.now()));
                    return null;
                });
                jobs.start();
                equal("FAILED", jobs.get("interrupted").get("status"));
            }
        });
        test("Queue capacity is bounded to 64 pending jobs", ()-> {
            try(Store s=new MemoryStore(); Jobs jobs=new Jobs(s)) {
                jobs.register("echo", (c, p)->p);
                for(int i=0; i<64; i++)jobs.enqueue("echo", Map.of(), "");
                problem(429, ()->jobs.enqueue("echo", Map.of(), ""));
            }
        });
        test("Unknown job kinds cannot be scheduled", ()-> {
            try(Store s=new MemoryStore(); Jobs jobs=new Jobs(s)) {
                problem(400, ()->jobs.enqueue("not-registered", Map.of(), ""));
            }
        });
        test("Router binds path and percent-decoded query separately", ()-> {
            var router=new Router().on("GET", "/api/items/{id}", q->Router.Response.json(Json.map("id", q.param("id"), "q", q.query().get("q"))));
            var response=router.handle(new Router.Request("GET", "/api/items/a?q=hello%20world", Map.of(), "", Map.of()));
            var body=object(Json.parse(response.body()));
            equal("a", body.get("id"));
            equal("hello world", body.get("q"));
        });
        test("Router rejects unmatched routes", ()->problem(404, ()->new Router().handle(new Router.Request("GET", "/missing", Map.of(), "", Map.of()))));
        test("Gateway rejects unexpected Host and unauthenticated state access", ()-> {
            try(Application app=factory.get()) {
                var gateway=gateway(app, "correct-key");
                equal(403, gateway.handle(request("GET", "/api/meta", "evil.test:8000", "", Map.of())).status());
                equal(401, gateway.handle(request("GET", "/api/meta", "localhost:8000", "", Map.of())).status());
            }
        });
        test("Health is public, responses carry CSP and request ID", ()-> {
            try(Application app=factory.get()) {
                var r=gateway(app, "key").handle(request("GET", "/health", "localhost:8000", "", Map.of()));
                equal(200, r.status());
                truth(r.headers().containsKey("Content-Security-Policy"));
                truth(r.headers().containsKey("X-Request-Id"));
            }
        });
        test("Authenticated gateway never exposes workspace secret", ()-> {
            try(Application app=factory.get()) {
                var r=gateway(app, "SECRET_KEY").handle(request("GET", "/api/meta", "localhost:8000", "", Map.of("x-workspace-key", "SECRET_KEY")));
                equal(200, r.status());
                truth(!r.body().contains("SECRET_KEY"));
            }
        });
        test("Gateway blocks cross-origin writes before routing", ()-> {
            try(Application app=factory.get()) {
                var r=gateway(app, "").handle(request("POST", "/api/missing", "localhost:8000", "{}", Map.of("origin", "https://evil.example")));
                equal(403, r.status());
            }
        });
        test("Gateway enforces JSON writes and body bounds", ()-> {
            try(Application app=factory.get()) {
                var g=gateway(app, "");
                equal(415, g.handle(request("POST", "/api/missing", "localhost:8000", "{}", Map.of("content-type", "text/plain"))).status());
                equal(413, g.handle(request("POST", "/api/missing", "localhost:8000", "x".repeat(Gateway.MAX_BODY+1), Map.of())).status());
            }
        });
        test("Model provider without a configured model fails explicitly", ()->problem(503, ()->offlineModel().generate("system", "prompt")));
        test("Ollama adapter sends nonstreaming request and parses token counts", ()-> {
            HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/generate", x-> {
                var input=object(Json.parse(new String(x.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
                truth(!Json.bool(input, "stream", true));
                byte[] data="{\"response\":\"hello\",\"prompt_eval_count\":12,\"eval_count\":3}".getBytes(StandardCharsets.UTF_8);
                x.sendResponseHeaders(200, data.length);
                x.getResponseBody().write(data);
                x.close();
            });
            server.start();
            try {
                var client=new ModelClient("http://127.0.0.1:"+server.getAddress().getPort(), "test-model", "");
                var out=client.generate("system", "prompt");
                equal("hello", out.text());
                equal(12, out.inputTokens());
                equal(3, out.outputTokens());
            } finally {
                server.stop(0);
            }
        });
        test("Ollama embedding adapter validates shape and finite values", ()-> {
            HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/embed", x-> {
                byte[] data="{\"embeddings\":[[0.3,0.4,0.5]]}".getBytes(StandardCharsets.UTF_8);
                x.sendResponseHeaders(200, data.length);
                x.getResponseBody().write(data);
                x.close();
            });
            server.start();
            try {
                var client=new ModelClient("http://127.0.0.1:"+server.getAddress().getPort(), "", "test-embed");
                equal(3, client.embed("hello").size());
            } finally {
                server.stop(0);
            }
        });
        test("Ollama provider HTTP failure does not silently fall back", ()-> {
            HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/api/generate", x-> {
                x.sendResponseHeaders(503, 0);
                x.close();
            });
            server.start();
            try {
                var client=new ModelClient("http://127.0.0.1:"+server.getAddress().getPort(), "test", "");
                problem(502, ()->client.generate("s", "p"));
            } finally {
                server.stop(0);
            }
        });
        test("CSV protects spreadsheet formula prefixes", ()-> {
            truth(Text.csv("=SUM(1,2)").contains("'="));
            truth(Text.csv("a\"b").contains("\"\""));
        });
        test("Provider response reader enforces byte budget", ()->problem(502, ()->HttpBodies.read(new java.io.ByteArrayInputStream(new byte[17]), 16, java.time.Duration.ofSeconds(1))));
        test("Provider response reader times out stalled reads", ()-> {
            var input=new java.io.InputStream() {
                public int read() throws java.io.IOException {
                    try {
                        Thread.sleep(2000);
                        return -1;
                    } catch(InterruptedException ex) {
                        throw new java.io.IOException(ex);
                    }
                }
            };
            problem(504, ()->HttpBodies.read(input, 100, java.time.Duration.ofMillis(20)));
        });
        test("XML escaping covers graph export special characters", ()->equal("&lt;a&gt;&amp;&quot;", Text.xml("<a>&\"")));
    }
    private static Gateway gateway(Application app, String token) {
        return new Gateway(app, new Config(8000, "127.0.0.1", token, "", Path.of("unused"), Path.of("web"), "", "", "", "http://localhost:11434", "", ""));
    }
    private static Router.Request request(String method, String path, String host, String body, Map<String, String> extra) {
        Map<String, String> h=new HashMap<>(Map.of("host", host, "content-type", "application/json"));
        h.putAll(extra);
        return new Router.Request(method, path, h, body, Map.of());
    }
}
