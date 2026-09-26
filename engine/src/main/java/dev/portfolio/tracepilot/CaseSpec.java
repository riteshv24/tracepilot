package dev.portfolio.tracepilot;

import dev.portfolio.foundation.*;
import java.util.*;

/** Immutable case contract. A recorded response is supplied evidence, not a generated benchmark result. */
public record CaseSpec(String id, String question, String context, String reference, String recordedResponse, List<String> requiredPhrases, String format, boolean expectRefusal) {
    public static CaseSpec from(Map<String, Object> value) {
        String id=Json.string(value, "id", Checks.uuid());
        Checks.id(id);
        List<String> phrases=Json.strings(value, "requiredPhrases");
        if(phrases.size()>20)throw Problem.invalid("A case can contain at most 20 required phrases");
        phrases=phrases.stream().map(p->Checks.text(p, "required phrase", 120)).toList();
        return new CaseSpec(id, Checks.text(Json.string(value, "question", ""), "question", 2000), Checks.optional(Json.string(value, "context", ""), "context", 16000), Checks.optional(Json.string(value, "reference", ""), "reference", 8000), Checks.optional(Json.string(value, "recordedResponse", ""), "recordedResponse", 8000), phrases, Checks.oneOf(Json.string(value, "format", "text"), "format", "text", "json"), Json.bool(value, "expectRefusal", false));
    }
    public Map<String, Object> json() {
        return Json.map("id", id, "question", question, "context", context, "reference", reference, "recordedResponse", recordedResponse, "requiredPhrases", requiredPhrases, "format", format, "expectRefusal", expectRefusal);
    }
}
