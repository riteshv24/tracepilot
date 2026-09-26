package dev.portfolio.foundation;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

/** Framework-independent HTTP contract. Both Spring MVC and the JDK adapter execute these handlers. */
public final class Router {
    public record Request(String method, String path, Map<String, String> headers, String body, Map<String, String> params) {
        public Map<String, Object> json() {
            try {
                return body.isBlank()?new LinkedHashMap<>():Json.object(Json.parse(body));
            } catch(IllegalArgumentException e) {
                throw Problem.invalid(e.getMessage());
            }
        }
        public String header(String name) {
            return headers.getOrDefault(name.toLowerCase(Locale.ROOT), "");
        }
        public String param(String name) {
            String result=params.get(name);
            if(result==null)throw Problem.invalid("Missing path parameter");
            return result;
        }
        public Map<String, String> query() {
            Map<String, String> result=new LinkedHashMap<>();
            int q=path.indexOf('?');
            if(q<0)return result;
            for(String pair:path.substring(q+1).split("&")) {
                String[] kv=pair.split("=", 2);
                if(!kv[0].isBlank())result.put(decode(kv[0]), kv.length==2?decode(kv[1]):"");
            }
            return result;
        }
        private static String decode(String value) {
            try {
                return URLDecoder.decode(value, StandardCharsets.UTF_8);
            } catch(IllegalArgumentException e) {
                throw Problem.invalid("Malformed URL encoding");
            }
        }
    }
    public record Response(int status, String contentType, String body, Map<String, String> headers) {
        public static Response json(Object body) {
            return new Response(200, "application/json; charset=utf-8", Json.write(body), Map.of());
        }
        public static Response created(Object body) {
            return new Response(201, "application/json; charset=utf-8", Json.write(body), Map.of());
        }
        public static Response accepted(Object body) {
            return new Response(202, "application/json; charset=utf-8", Json.write(body), Map.of());
        }
        public static Response download(String name, String type, String body) {
            return new Response(200, type, body, Map.of("Content-Disposition", "attachment; filename=\""+name+"\""));
        }
    }
    private record Route(String method, String pattern, Function<Request, Response> handler) {
    }
    private final List<Route> routes=new ArrayList<>();
    public Router on(String method, String pattern, Function<Request, Response> handler) {
        routes.add(new Route(method, pattern, handler));
        return this;
    }
    public List<Map<String, Object>> contract() {
        return routes.stream().map(r->Json.map("method", r.method, "path", r.pattern)).toList();
    }
    public Response handle(Request request) {
        String clean=request.path().split("\\?", 2)[0];
        for(Route route:routes) {
            if(!route.method.equals(request.method()))continue;
            Map<String, String> params=match(route.pattern, clean);
            if(params!=null)return route.handler.apply(new Request(request.method, request.path, request.headers, request.body, params));
        }
        throw Problem.missing("API route");
    }
    private static Map<String, String> match(String pattern, String path) {
        String[] expected=pattern.split("/", -1), actual=path.split("/", -1);
        if(expected.length!=actual.length)return null;
        Map<String, String> params=new LinkedHashMap<>();
        for(int i=0; i<expected.length; i++) {
            if(expected[i].startsWith("{")&&expected[i].endsWith("}"))params.put(expected[i].substring(1, expected[i].length()-1), Request.decode(actual[i]));
            else if(!expected[i].equals(actual[i]))return null;
        }
        return params;
    }
}
