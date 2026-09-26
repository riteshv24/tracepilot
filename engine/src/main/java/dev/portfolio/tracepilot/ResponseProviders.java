package dev.portfolio.tracepilot;

import dev.portfolio.foundation.*;
import java.util.*;

public final class ResponseProviders {
    public record Output(String text, long inputTokens, long outputTokens, String tokenAccounting, long latencyMillis, String provider) {
    }
    private final ModelClient model;
    public ResponseProviders(ModelClient model) {
        this.model=model;
    }
    public Output response(String provider, CaseSpec item, Map<String, Object> prompt) throws Exception {
        String rendered=PromptTemplate.render(Json.string(prompt, "template", "{{question}}"), item);
        long start=System.nanoTime();
        if(provider.equals("ollama")) {
            ModelClient.Generation generated=model.generate(Json.string(prompt, "system", ""), rendered);
            return new Output(generated.text(), generated.inputTokens(), generated.outputTokens(), "provider-reported", generated.elapsedMillis(), model.model());
        }
        String response=provider.equals("recorded")?item.recordedResponse():extractive(item);
        return new Output(response, Text.tokens(rendered).size(), Text.tokens(response).size(), "whitespace/word estimate", Math.max(0, (System.nanoTime()-start)/1_000_000), provider);
    }
    public String extractive(CaseSpec item) {
        if(item.context().isBlank())return "Not enough information is available in the provided context.";
        Set<String> question=Text.uniqueTerms(item.question());
        return Text.sentences(item.context()).stream() .max(Comparator.comparingDouble(s->relevance(question, s))).filter(s->relevance(question, s)>0) .orElse("Not enough information is available in the provided context.");
    }
    private static double relevance(Set<String> question, String sentence) {
        if(question.isEmpty())return 0;
        Set<String> terms=Text.uniqueTerms(sentence);
        return question.stream().filter(terms::contains).count()/(double)question.size();
    }
}
