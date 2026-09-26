package dev.portfolio.foundation;

import java.util.*;

/** Metadata-only audit trail: never stores API keys, raw documents, prompts or patches. */
public final class Audit {
    private Audit() {
    }
    public static void record(Store.Tx tx, String action, String target) {
        String id = Checks.uuid();
        tx.put("audit", id, Json.map("id", id, "action", action, "target", target, "at", Checks.now()));
        List<Map<String, Object>> all = tx.scan("audit");
        if (all.size() > 500) all.stream().sorted(Comparator.comparing(a -> Json.string(a, "at", ""))) .limit(all.size() - 500).forEach(a -> tx.delete("audit", Json.string(a, "id", "")));
    }
    public static List<Map<String, Object>> recent(Store store) {
        return store.read(tx -> tx.scan("audit").stream().sorted(Comparator.comparing((Map<String, Object> a) -> Json.string(a, "at", "")).reversed()).limit(100).toList());
    }
}
