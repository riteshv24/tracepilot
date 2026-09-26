package dev.portfolio.spring;

import dev.portfolio.foundation.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController public class ApiController {
    private final Gateway gateway;
    public ApiController(Gateway gateway) {
        this.gateway=gateway;
    }
    @RequestMapping(value= {
        "/api/**", "/health"
    }) public ResponseEntity<String> dispatch(HttpServletRequest request) throws IOException {
        Map<String, String> headers=new HashMap<>();
        Collections.list(request.getHeaderNames()).forEach(k->headers.put(k.toLowerCase(Locale.ROOT), request.getHeader(k)));
        byte[] bytes=request.getInputStream().readNBytes(Gateway.MAX_BODY+1);
        Router.Response response;
        if(bytes.length>Gateway.MAX_BODY)response=Gateway.error(413, "body_too_large", "Request exceeds 1 MiB", Checks.uuid());
        else response=gateway.handle(new Router.Request(request.getMethod(), request.getRequestURI()+(request.getQueryString()==null?"":"?"+request.getQueryString()), headers, new String(bytes, StandardCharsets.UTF_8), Map.of()));
        HttpHeaders output=new HttpHeaders();
        output.setContentType(MediaType.parseMediaType(response.contentType()));
        response.headers().forEach(output::set);
        return new ResponseEntity<>(response.body(), output, HttpStatusCode.valueOf(response.status()));
    }
}
