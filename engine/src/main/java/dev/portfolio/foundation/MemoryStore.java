package dev.portfolio.foundation;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

/** Copy-on-write isolation, rollback on exceptions, defensive copies at every aggregate boundary. */
public class MemoryStore implements Store {
    protected Map<String, Object> state = new LinkedHashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock(true);
    @Override public String mode() {
        return "memory";
    }
    @Override public <T> T read(Function<Tx, T> work) {
        lock.readLock().lock();
        try {
            return work.apply(new View(state, false));
        } finally {
            lock.readLock().unlock();
        }
    }
    @Override public <T> T write(Function<Tx, T> work) {
        lock.writeLock().lock();
        try {
            Map<String, Object> candidate = Json.copyMap(state);
            T result = work.apply(new View(candidate, true));
            if (!candidate.equals(state)) {
                persist(candidate);
                state = candidate;
            }
            return result;
        } finally {
            lock.writeLock().unlock();
        }
    }
    protected void persist(Map<String, Object> candidate) {
    }
    private static final class View implements Tx {
        private final Map<String, Object> state;
        private final boolean writable;
        View(Map<String, Object> state, boolean writable) {
            this.state = state;
            this.writable = writable;
        }
        private Map<String, Object> bucket(String key) {
            Checks.id(key);
            Object bucket = state.get(key);
            return bucket == null ? Map.of() : Json.object(bucket);
        }
        public Optional<Map<String, Object>> get(String bucket, String id) {
            Checks.id(id);
            Object result = bucket(bucket).get(id);
            return result == null ? Optional.empty() : Optional.of(Json.copyMap(Json.object(result)));
        }
        public List<Map<String, Object>> scan(String bucket) {
            return bucket(bucket).values().stream().map(v -> Json.copyMap(Json.object(v))).toList();
        }
        public void put(String bucket, String id, Map<String, Object> record) {
            if (!writable) throw new IllegalStateException("Read-only transaction");
            Checks.id(bucket);
            Checks.id(id);
            Map<String, Object> values = Json.object(state.computeIfAbsent(bucket, k -> new LinkedHashMap<>()));
            values.put(id, Json.copyMap(record));
        }
        public void delete(String bucket, String id) {
            if (!writable) throw new IllegalStateException("Read-only transaction");
            if (state.containsKey(bucket)) bucket(bucket).remove(id);
        }
    }
}
