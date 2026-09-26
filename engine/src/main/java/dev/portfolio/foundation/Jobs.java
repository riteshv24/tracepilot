package dev.portfolio.foundation;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Durable bounded job state machine. Single application instance per workspace.
 * RUNNING work becomes FAILED after a process restart rather than replaying paid model calls.
 * Cancellation is cooperative at item boundaries; partial evidence is intentionally preserved.
 */
public final class Jobs implements AutoCloseable {
    @FunctionalInterface public interface Handler {
        Map<String, Object> execute(Context context, Map<String, Object> payload) throws Exception;
    }
    public final class Context {
        private final String id;
        Context(String id) {
            this.id = id;
        }
        public String id() {
            return id;
        }
        public void checkCancelled() {
            if (Thread.currentThread().isInterrupted() || store.read(tx -> Json.bool(tx.require("jobs", id), "cancelRequested", false))) throw new CancellationException();
        }
        public void progress(int completed, int total, String message) {
            checkCancelled();
            store.write(tx -> {
                Map<String, Object> job = tx.require("jobs", id);
                job.put("completed", completed);
                job.put("total", total);
                job.put("message", Text.excerpt(message, 180));
                job.put("updatedAt", Checks.now());
                tx.put("jobs", id, job);
                return null;
            });
        }
    }
    private final Store store;
    private final Map<String, Handler> handlers = new ConcurrentHashMap<>();
    private final ScheduledExecutorService dispatcher = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "job-dispatcher"));
    private final ExecutorService workers = Executors.newFixedThreadPool(2, r -> new Thread(r, "job-worker"));
    private final AtomicInteger active = new AtomicInteger();
    private volatile boolean started;
    private volatile boolean stopping;
    public Jobs(Store store) {
        this.store = store;
    }
    public void register(String kind, Handler handler) {
        if (started) throw new IllegalStateException("Register before starting");
        handlers.put(kind, handler);
    }
    public synchronized void start() {
        if (started) return;
        store.write(tx -> {
            for (Map<String, Object> job : tx.scan("jobs")) if (Json.string(job, "status", "").equals("RUNNING")) {
                job.put("status", "FAILED");
                job.put("error", "Process interrupted. Partial evidence was preserved; start a new job to retry.");
                job.put("finishedAt", Checks.now());
                tx.put("jobs", Json.string(job, "id", ""), job);
            }
            return null;
        });
        started = true;
        dispatcher.scheduleWithFixedDelay(this::dispatchSafely, 0, 150, TimeUnit.MILLISECONDS);
    }
    public Map<String, Object> enqueue(String kind, Map<String, Object> payload, String idempotencyKey) {
        return store.write(tx -> enqueue(tx, kind, payload, idempotencyKey));
    }
    public Map<String, Object> enqueue(Store.Tx tx, String kind, Map<String, Object> payload, String idempotencyKey) {
        if (!handlers.containsKey(kind)) throw Problem.invalid("Unknown job type");
        String fingerprint = Checks.sha256(kind + "\n" + Json.write(payload));
        String key = idempotencyKey == null || idempotencyKey.isBlank() ? "" : Checks.sha256(kind + "\n" + Checks.text(idempotencyKey, "Idempotency-Key", 180));
        if (!key.isEmpty()) {
            Optional<Map<String, Object>> previous = tx.get("idempotency", key);
            if (previous.isPresent()) {
                if (!fingerprint.equals(Json.string(previous.get(), "fingerprint", ""))) throw Problem.conflict("Idempotency key was already used with a different request");
                return publicView(tx.require("jobs", Json.string(previous.get(), "jobId", "")));
            }
        }
        long waiting = tx.scan("jobs").stream().filter(j -> Set.of("QUEUED", "RUNNING").contains(Json.string(j, "status", ""))).count();
        if (waiting >= 64) throw new Problem(429, "queue_full", "At most 64 jobs may be queued or running");
        String id = Checks.uuid();
        String now = Checks.now();
        Map<String, Object> job = Json.map("id", id, "kind", kind, "status", "QUEUED", "createdAt", now, "updatedAt", now, "completed", 0, "total", 0, "message", "Waiting for a worker", "cancelRequested", false, "payload", payload);
        tx.put("jobs", id, job);
        if (!key.isEmpty()) tx.put("idempotency", key, Json.map("id", key, "jobId", id, "fingerprint", fingerprint));
        Audit.record(tx, "job.queued", id);
        return publicView(job);
    }
    public Map<String, Object> get(String id) {
        return store.read(tx -> publicView(tx.require("jobs", Checks.id(id))));
    }
    public List<Map<String, Object>> list() {
        return store.read(tx -> tx.scan("jobs").stream().sorted(Comparator.comparing((Map<String, Object> j) -> Json.string(j, "createdAt", "")).reversed()).limit(100).map(Jobs::publicView).toList());
    }
    public Map<String, Object> cancel(String id) {
        return store.write(tx -> {
            Map<String, Object> job = tx.require("jobs", Checks.id(id));
            String state = Json.string(job, "status", "");
            if (!Set.of("QUEUED", "RUNNING").contains(state)) throw Problem.conflict("Only queued or running work can be cancelled");
            job.put("cancelRequested", true);
            if (state.equals("QUEUED")) {
                job.put("status", "CANCELLED");
                job.put("finishedAt", Checks.now());
            }
            tx.put("jobs", id, job);
            Audit.record(tx, "job.cancel_requested", id);
            return publicView(job);
        });
    }
    private void dispatchSafely() {
        if (stopping || active.get() >= 2) return;
        try {
            Map<String, Object> claimed = store.write(tx -> {
                Optional<Map<String, Object>> pending = tx.scan("jobs").stream().filter(j -> Json.string(j, "status", "").equals("QUEUED")) .min(Comparator.comparing(j -> Json.string(j, "createdAt", "")));
                if (pending.isEmpty()) return null;
                Map<String, Object> job = pending.get();
                job.put("status", "RUNNING");
                job.put("startedAt", Checks.now());
                tx.put("jobs", Json.string(job, "id", ""), job);
                return job;
            });
            if (claimed != null) {
                active.incrementAndGet();
                workers.submit(() -> execute(claimed));
            }
        } catch (RuntimeException e) {
            System.err.println("Job dispatcher paused after storage failure: " + e.getClass().getSimpleName());
        }
    }
    private void execute(Map<String, Object> job) {
        String id = Json.string(job, "id", "");
        try {
            Map<String, Object> result = handlers.get(Json.string(job, "kind", "")).execute(new Context(id), Json.object(job.get("payload")));
            finish(id, "SUCCEEDED", result, null);
        } catch (CancellationException | InterruptedException e) {
            finish(id, "CANCELLED", Map.of(), null);
        } catch (Exception e) {
            // Avoid leaking prompts, code or model credentials in job errors.
            String message = e instanceof Problem ? e.getMessage() : "Job failed (" + e.getClass().getSimpleName() + "). Inspect local logs and retry with a smaller fixture.";
            finish(id, "FAILED", Map.of(), message);
        } finally {
            active.decrementAndGet();
        }
    }
    private void finish(String id, String status, Map<String, Object> result, String error) {
        try {
            store.write(tx -> {
                Map<String, Object> job = tx.require("jobs", id);
                if (Json.bool(job, "cancelRequested", false)) statusHolder(job, "CANCELLED");
                else statusHolder(job, status);
                job.put("result", result);
                job.put("error", error);
                job.put("finishedAt", Checks.now());
                job.put("updatedAt", Checks.now());
                tx.put("jobs", id, job);
                Audit.record(tx, "job."+Json.string(job, "status", "").toLowerCase(Locale.ROOT), id);
                return null;
            });
        } catch (RuntimeException e) {
            System.err.println("Unable to finalize job " + id + ": " + e.getClass().getSimpleName());
        }
    }
    private static void statusHolder(Map<String, Object> job, String status) {
        job.put("status", status);
    }
    private static Map<String, Object> publicView(Map<String, Object> job) {
        Map<String, Object> copy=Json.copyMap(job);
        copy.remove("payload");
        return copy;
    }
    @Override public void close() {
        stopping=true;
        dispatcher.shutdownNow();
        workers.shutdownNow();
        try {
            workers.awaitTermination(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
