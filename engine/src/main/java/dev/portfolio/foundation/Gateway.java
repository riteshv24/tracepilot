package dev.portfolio.foundation;

import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.LongAdder;

/** Shared security and error boundary for both HTTP adapters. Static pages are public;
 * API state is local-only by default or protected by an operator-configured workspace key.
 */
public final class Gateway {
    public static final int MAX_BODY=1_048_576;
    private final Application app;
    private final Config config;
    private final LongAdder requests=new LongAdder();
    private final LongAdder failures=new LongAdder();
    public Gateway(Application app, Config config) {
        this.app=app;
        this.config=config;
    }
    public Router.Response handle(Router.Request request) {
        String requestId=Checks.uuid();
        requests.increment();
        try {
            validateHost(request.header("host"));
            if(request.path().equals("/health"))return attach(Router.Response.json(Json.map("status", "UP", "application", app.name())), requestId);
            boolean webhook=request.path().split("\\?", 2)[0].equals("/api/integrations/github/webhook");
            if(!webhook && !config.token().isEmpty() && !Checks.sameSecret(config.token(), request.header("x-workspace-key"))) throw new Problem(401, "unauthorized", "A valid workspace key is required");
            if(!Set.of("GET", "HEAD").contains(request.method())) {
                String origin=request.header("origin");
                if(!origin.isBlank()) {
                    URI value;
                    try {
                        value=URI.create(origin);
                    } catch(IllegalArgumentException e) {
                        throw new Problem(403, "origin_rejected", "Origin is not allowed");
                    }
                    if(value.getRawAuthority()==null||!value.getRawAuthority().equalsIgnoreCase(request.header("host")))throw new Problem(403, "origin_rejected", "Cross-origin writes are not allowed");
                }
                if(!request.body().isBlank()&&!request.header("content-type").toLowerCase(Locale.ROOT).startsWith("application/json"))throw new Problem(415, "content_type", "Use application/json");
            }
            if(request.body().length()>MAX_BODY)throw new Problem(413, "body_too_large", "Request exceeds 1 MiB");
            return attach(app.router().handle(request), requestId);
        } catch(Problem p) {
            failures.increment();
            return error(p.status(), p.code(), p.getMessage(), requestId);
        } catch(IllegalArgumentException e) {
            failures.increment();
            return error(400, "invalid_request", Text.excerpt(e.getMessage()==null?"Invalid request":e.getMessage(), 240), requestId);
        } catch(Exception e) {
            failures.increment();
            System.err.println("Request "+requestId+" failed: "+e.getClass().getSimpleName());
            return error(500, "internal_error", "Unexpected failure; use the request ID to inspect local logs", requestId);
        }
    }
    private void validateHost(String host) {
        Set<String> allowed=Set.of("localhost:"+config.port(), "127.0.0.1:"+config.port(), "[::1]:"+config.port());
        if(!allowed.contains(host.toLowerCase(Locale.ROOT)) && (config.allowedHost().isBlank()||!config.allowedHost().equalsIgnoreCase(host)))throw new Problem(403, "host_rejected", "Host is not in the allowlist");
    }
    public static Router.Response error(int status, String code, String detail, String requestId) {
        return attach(new Router.Response(status, "application/problem+json; charset=utf-8", Json.write(Json.map("type", "urn:workbench:"+code, "title", code, "status", status, "detail", detail, "requestId", requestId)), Map.of()), requestId);
    }
    public static Router.Response attach(Router.Response response, String requestId) {
        Map<String, String> headers=new LinkedHashMap<>(response.headers());
        headers.put("X-Request-Id", requestId);
        headers.put("X-Content-Type-Options", "nosniff");
        headers.put("Cache-Control", "no-store");
        headers.put("Referrer-Policy", "no-referrer");
        headers.put("X-Frame-Options", "DENY");
        headers.put("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'");
        return new Router.Response(response.status(), response.contentType(), response.body(), headers);
    }
}
