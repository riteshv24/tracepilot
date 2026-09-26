package dev.portfolio.tests;

import dev.portfolio.foundation.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.function.*;

/** Dependency-free contract runner, so the same engine tests run without Maven or network access. */
public final class TestSupport {
    @FunctionalInterface public interface Checked {
        void run() throws Exception;
    }
    private static int passed, failed;
    private TestSupport() {
    }
    public static void test(String name, Checked task) {
        try {
            task.run();
            passed++;
            System.out.println("PASS " + name);
        } catch (Throwable error) {
            failed++;
            System.err.println("FAIL " + name + ": " + error);
            error.printStackTrace(System.err);
        }
    }
    public static void truth(boolean condition) {
        if (!condition) throw new AssertionError("Expected true");
    }
    public static void equal(Object expected, Object actual) {
        if (expected instanceof Number a && actual instanceof Number b) {
            if (Math.abs(a.doubleValue() - b.doubleValue()) < 1e-9) return;
        }
        if (!Objects.equals(expected, actual)) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    public static void near(double expected, double actual, double tolerance) {
        if (!Double.isFinite(actual) || Math.abs(expected-actual)>tolerance) throw new AssertionError("Expected approximately " + expected + ", got " + actual);
    }
    public static <T extends Throwable> T raises(Class<T> type, Checked action) {
        try {
            action.run();
        } catch (Throwable error) {
            if (type.isInstance(error)) return type.cast(error);
            throw new AssertionError("Wrong exception", error);
        }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
    public static void problem(int status, Checked action) {
        equal(status, raises(Problem.class, action).status());
    }
    public static ModelClient offlineModel() {
        return new ModelClient("http://127.0.0.1:11434", "", "");
    }
    public static Map<String, Object> object(Object value) {
        return Json.object(value);
    }
    public static String id(Map<String, Object> value) {
        return Json.string(value, "id", "");
    }
    public static Map<String, Object> call(Application app, String method, String path, Object input) {
        Object parsed = callAny(app, method, path, input);
        return Json.object(parsed);
    }
    public static Object callAny(Application app, String method, String path, Object input) {
        Router.Response response=app.router().handle(new Router.Request(method, path, Map.of("content-type", "application/json"), input==null?"":Json.write(input), Map.of()));
        return Json.parse(response.body());
    }
    public static List<Map<String, Object>> list(Application app, String path) {
        return Json.array(callAny(app, "GET", path, null)).stream().map(Json::object).toList();
    }
    public static Map<String, Object> await(Jobs jobs, String id) throws InterruptedException {
        long deadline=System.nanoTime()+Duration.ofSeconds(15).toNanos();
        while (System.nanoTime()<deadline) {
            Map<String, Object> job=jobs.get(id);
            String state=Json.string(job, "status", "");
            if (Set.of("SUCCEEDED", "FAILED", "CANCELLED").contains(state)) return job;
            Thread.sleep(25);
        }
        throw new AssertionError("Job did not terminate: " + id);
    }
    public static void allJobs(Application app) throws InterruptedException {
        for (Map<String, Object> job:app.jobs().list()) equal("SUCCEEDED", await(app.jobs(), id(job)).get("status"));
    }
    public static void deleteTree(Path directory) throws Exception {
        if (!Files.exists(directory)) return;
        try (var stream=Files.walk(directory)) {
            for (Path p:stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
        }
    }
    public static void finish() {
        System.out.printf("RESULT: %d passed; %d failed%n", passed, failed);
        if (failed!=0) throw new AssertionError(failed + " tests failed");
    }
}
