package dev.portfolio.launcher;

import com.sun.net.httpserver.*;
import dev.portfolio.foundation.*;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

/** Same API as Spring MVC, runnable with an unmodified JDK 21 and no package downloads. */
public final class HttpRuntime implements AutoCloseable {
    private final HttpServer server;
    private final Gateway gateway;
    private final Config config;
    private final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore permits=new Semaphore(32);
    public HttpRuntime(Application app, Config config) throws IOException {
        this.config=config;
        gateway=new Gateway(app, config);
        server=HttpServer.create(new InetSocketAddress(config.bind(), config.port()), 64);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }
    public void start() {
        server.start();
    }
    private void handle(HttpExchange exchange) throws IOException {
        if(!permits.tryAcquire()) {
            send(exchange, Gateway.error(503, "busy", "Too many concurrent requests", Checks.uuid()));
            return;
        }
        try {
            String path=exchange.getRequestURI().toString();
            if(path.startsWith("/api/")||path.equals("/health")) {
                byte[] bytes=exchange.getRequestBody().readNBytes(Gateway.MAX_BODY+1);
                if(bytes.length>Gateway.MAX_BODY) {
                    send(exchange, Gateway.error(413, "body_too_large", "Request exceeds 1 MiB", Checks.uuid()));
                    return;
                }
                Map<String, String> headers=new HashMap<>();
                exchange.getRequestHeaders().forEach((k, v)->headers.put(k.toLowerCase(Locale.ROOT), v.get(0)));
                send(exchange, gateway.handle(new Router.Request(exchange.getRequestMethod(), path, headers, new String(bytes, StandardCharsets.UTF_8), Map.of())));
            } else staticFile(exchange);
        } catch(Exception e) {
            send(exchange, Gateway.error(500, "internal_error", "Request could not be handled", Checks.uuid()));
        } finally {
            permits.release();
            exchange.close();
        }
    }
    private void staticFile(HttpExchange exchange) throws IOException {
        if(!exchange.getRequestMethod().equals("GET")) {
            send(exchange, Gateway.error(405, "method_not_allowed", "Use GET", Checks.uuid()));
            return;
        }
        String path=exchange.getRequestURI().getPath();
        if(path.equals("/"))path="/index.html";
        Path root=config.web().toAbsolutePath().normalize();
        Path file=root.resolve(path.substring(1)).normalize();
        if(!file.startsWith(root)||!Files.isRegularFile(file)||Files.isSymbolicLink(file)||Files.size(file)>3_000_000) {
            send(exchange, Gateway.error(404, "not_found", "Asset not found", Checks.uuid()));
            return;
        }
        String type=path.endsWith(".css")?"text/css":path.endsWith(".js")||path.endsWith(".mjs")?"text/javascript":path.endsWith(".svg")?"image/svg+xml":path.endsWith(".json")?"application/json":path.endsWith(".md")?"text/plain":"text/html";
        send(exchange, Gateway.attach(new Router.Response(200, type+"; charset=utf-8", Files.readString(file), Map.of()), Checks.uuid()));
    }
    private static void send(HttpExchange exchange, Router.Response response) throws IOException {
        byte[] bytes=response.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", response.contentType());
        response.headers().forEach((k, v)->exchange.getResponseHeaders().set(k, v));
        exchange.sendResponseHeaders(response.status(), bytes.length);
        exchange.getResponseBody().write(bytes);
    }
    public void close() {
        server.stop(1);
        executor.close();
    }
}
