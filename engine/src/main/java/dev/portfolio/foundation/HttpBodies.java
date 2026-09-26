package dev.portfolio.foundation;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.*;

/** Bounded streaming-body read with a deadline; HttpRequest timeouts alone do not bound an InputStream read. */
public final class HttpBodies {
    private HttpBodies() {
    }
    public static byte[] read(InputStream stream, int limit, Duration budget) throws IOException, InterruptedException {
        ExecutorService reader = Executors.newVirtualThreadPerTaskExecutor();
        Future<byte[]> task = reader.submit(() -> stream.readNBytes(limit + 1));
        try {
            byte[] bytes = task.get(Math.max(1, budget.toMillis()), TimeUnit.MILLISECONDS);
            if (bytes.length > limit) throw new Problem(502, "provider_response_large", "Provider response exceeded its size budget");
            return bytes;
        } catch (TimeoutException error) {
            task.cancel(true);
            throw new Problem(504, "provider_timeout", "Provider response body exceeded its time budget");
        } catch (ExecutionException error) {
            if (error.getCause() instanceof IOException io) throw io;
            throw new IOException("Unable to read provider response", error.getCause());
        } finally {
            reader.shutdownNow();
            stream.close();
        }
    }
}
