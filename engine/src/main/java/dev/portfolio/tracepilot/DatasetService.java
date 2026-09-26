package dev.portfolio.tracepilot;

import dev.portfolio.foundation.*;
import java.util.*;

/** Versioned benchmark and prompt aggregates. Run snapshots are not changed by edits here. */
public final class DatasetService {
    private final Store store;
    public DatasetService(Store store) {
        this.store=store;
    }
    public List<Map<String, Object>> list() {
        return store.read(tx->tx.scan("datasets").stream().map(d->summary(d)).toList());
    }
    private Map<String, Object> summary(Map<String, Object> d) {
        Map<String, Object> copy=Json.copyMap(d);
        copy.put("caseCount", Json.list(d, "cases").size());
        copy.remove("cases");
        return copy;
    }
    public Map<String, Object> get(String id) {
        return store.read(tx->tx.require("datasets", Checks.id(id)));
    }
    public Map<String, Object> create(Map<String, Object> input) {
        return save(null, input);
    }
    public Map<String, Object> update(String id, Map<String, Object> input) {
        return save(Checks.id(id), input);
    }
    private Map<String, Object> save(String id, Map<String, Object> input) {
        String name=Checks.text(Json.string(input, "name", ""), "name", 100);
        String description=Checks.optional(Json.string(input, "description", ""), "description", 1200);
        List<Object> raw=Json.list(input, "cases");
        if(raw.isEmpty()||raw.size()>200)throw Problem.invalid("Dataset requires between 1 and 200 cases");
        List<Map<String, Object>> cases=raw.stream().map(Json::object).map(CaseSpec::from).map(CaseSpec::json).toList();
        Set<String> seen=new HashSet<>();
        for(var item:cases)if(!seen.add(Json.string(item, "id", "")))throw Problem.invalid("Duplicate case identifier");
        return store.write(tx-> {
            int version=1;
            String key=id==null?Checks.uuid():id;
            String created=Checks.now();
            if(id!=null) {
                Map<String, Object> old=tx.require("datasets", id);
                long expected=Json.integer(input, "expectedVersion", -1);
                if(expected!=Json.integer(old, "version", 0))throw Problem.conflict("Dataset changed; reload before saving");
                version=(int)expected+1;
                created=Json.string(old, "createdAt", created);
            } else if(tx.scan("datasets").size()>=30)throw Problem.invalid("Workspace supports at most 30 datasets");
            Map<String, Object> result=Json.map("id", key, "name", name, "description", description, "cases", cases, "version", version, "createdAt", created, "updatedAt", Checks.now());
            tx.put("datasets", key, result);
            tx.put("dataset_versions", key+"-v"+version, result);
            Audit.record(tx, id==null?"dataset.created":"dataset.versioned", key);
            return result;
        });
    }
    public List<Map<String, Object>> versions(String id) {
        Checks.id(id);
        return store.read(tx-> {
            tx.require("datasets", id);
            return tx.scan("dataset_versions").stream().filter(d->id.equals(Json.string(d, "id", ""))).sorted(Comparator.comparingLong(d->Json.integer(d, "version", 0))).toList();
        });
    }
    public List<Map<String, Object>> prompts() {
        return store.read(tx->tx.scan("prompts"));
    }
    public Map<String, Object> savePrompt(String id, Map<String, Object> input) {
        String name=Checks.text(Json.string(input, "name", ""), "name", 100);
        String template=PromptTemplate.validate(Json.string(input, "template", ""));
        String system=Checks.optional(Json.string(input, "system", "Use only the provided context. State when information is missing."), "system", 4000);
        return store.write(tx-> {
            String key=id==null?Checks.uuid():Checks.id(id);
            long version=1;
            if(id!=null) {
                Map<String, Object> old=tx.require("prompts", id);
                if(Json.integer(input, "expectedVersion", -1)!=Json.integer(old, "version", 0))throw Problem.conflict("Prompt changed; reload before saving");
                version=Json.integer(old, "version", 0)+1;
            }
            Map<String, Object> prompt=Json.map("id", key, "name", name, "template", template, "system", system, "version", version, "updatedAt", Checks.now());
            tx.put("prompts", key, prompt);
            tx.put("prompt_versions", key+"-v"+version, prompt);
            Audit.record(tx, "prompt.versioned", key);
            return prompt;
        });
    }
}
