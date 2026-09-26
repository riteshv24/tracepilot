package dev.portfolio.foundation;

import java.util.*;
import java.util.function.Function;

/** Aggregate-oriented persistence. Transactions must be short and must not perform network calls. */
public interface Store extends AutoCloseable {
    interface Tx {
        Optional<Map<String, Object>> get(String bucket, String id);
        List<Map<String, Object>> scan(String bucket);
        void put(String bucket, String id, Map<String, Object> record);
        void delete(String bucket, String id);
        default Map<String, Object> require(String bucket, String id) {
            return get(bucket, id).orElseThrow(() -> Problem.missing(bucket + "/" + id));
        }
    }
    <T> T read(Function<Tx, T> work);
    <T> T write(Function<Tx, T> work);
    String mode();
    @Override default void close() {
    }
}
