package dev.portfolio.foundation;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

/** Optional Ollama adapter. Base URL is operator configuration, never accepted from API requests.
 * No silent fallback: provider failures remain visible. No unbounded response buffering.
 */
public final class ModelClient {
    private final HttpClient http;
    private final URI base;
    private final String model;
    private final String embeddingModel;
    public record Generation(String text, long inputTokens, long outputTokens, long elapsedMillis) {
    }
    public ModelClient(String base, String model, String embeddingModel) {
        this(base, model, embeddingModel, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build());
    }
    public ModelClient(String base, String model, String embeddingModel, HttpClient client) {
        this.base=URI.create(base.endsWith("/")?base:base+"/");
        this.model=model;
        this.embeddingModel=embeddingModel;
        this.http=client;
        if (!Set.of("http", "https").contains(this.base.getScheme()) || this.base.getHost()==null || this.base.getUserInfo()!=null) throw new IllegalArgumentException("Invalid model service URL");
    }
    public boolean available() {
        return model!=null && !model.isBlank();
    }
    public boolean embeddingsAvailable() {
        return embeddingModel!=null && !embeddingModel.isBlank();
    }
    public String model() {
        return model==null?"":model;
    }
    public String embeddingModel() {
        return embeddingModel==null?"":embeddingModel;
    }
    public Generation generate(String system, String prompt) throws IOException, InterruptedException {
        if (!available()) throw new Problem(503, "model_unconfigured", "Set OLLAMA_MODEL to an installed model to enable generation");
        long start=System.nanoTime();
        Map<String, Object> response=post("api/generate", Json.map("model", model, "system", system, "prompt", prompt, "stream", false, "options", Json.map("temperature", 0, "num_predict", 900)));
        String text=Json.string(response, "response", "");
        if (text.isBlank()) throw new Problem(502, "model_empty", "Model returned no text");
        return new Generation(text, Json.integer(response, "prompt_eval_count", 0), Json.integer(response, "eval_count", 0), (System.nanoTime()-start)/1_000_000);
    }
    public List<Double> embed(String text) throws IOException, InterruptedException {
        if (!embeddingsAvailable()) throw new Problem(503, "embedding_unconfigured", "Set OLLAMA_EMBED_MODEL to enable semantic embeddings");
        Map<String, Object> response=post("api/embed", Json.map("model", embeddingModel, "input", text, "truncate", false));
        List<Object> outer=Json.list(response, "embeddings");
        if (outer.size()!=1) throw new Problem(502, "embedding_shape", "Embedding provider returned an unexpected vector count");
        List<Object> values=Json.array(outer.get(0));
        if (values.isEmpty() || values.size()>8192) throw new Problem(502, "embedding_shape", "Embedding dimension is outside supported limits");
        List<Double> vector=new ArrayList<>();
        for(Object value:values) {
            if(!(value instanceof Number n)||!Double.isFinite(n.doubleValue())) throw new Problem(502, "embedding_shape", "Embedding contains invalid values");
            vector.add(n.doubleValue());
        }
        return vector;
    }
    private Map<String, Object> post(String path, Map<String, Object> payload) throws IOException, InterruptedException {
        HttpRequest request=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(90)).header("Content-Type", "application/json") .POST(HttpRequest.BodyPublishers.ofString(Json.write(payload))).build();
        HttpResponse<java.io.InputStream> response=http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try(var body=response.body()) {
            byte[] bytes=HttpBodies.read(body, 1_048_576, Duration.ofSeconds(30));
            if(bytes.length>1_048_576) throw new Problem(502, "provider_response_large", "Model response exceeded 1 MiB");
            if(response.statusCode()!=200) throw new Problem(502, "provider_error", "Model service returned HTTP "+response.statusCode());
            try {
                return Json.object(Json.parse(new String(bytes, StandardCharsets.UTF_8)));
            } catch(IllegalArgumentException e) {
                throw new Problem(502, "provider_json", "Model returned invalid JSON");
            }
        }
    }
}
