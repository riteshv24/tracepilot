package dev.portfolio.foundation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

/** Bounded local workspace store. Atomic replacement plus fsync; never silently resets corrupt data.
 * A lifetime OS file lock prevents two JVMs from using the same local workspace.
 */
public final class FileStore extends MemoryStore {
    private static final int MAX_BYTES = 32 * 1024 * 1024;
    private final Path directory;
    private final Path snapshot;
    private final FileChannel lockChannel;
    private final FileLock fileLock;
    public FileStore(Path directory) {
        try {
            this.directory = directory.toAbsolutePath().normalize();
            Files.createDirectories(this.directory);
            snapshot = this.directory.resolve("workspace.json");
            lockChannel = FileChannel.open(this.directory.resolve("workspace.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock acquired;
            try {
                acquired = lockChannel.tryLock();
            } catch (OverlappingFileLockException e) {
                acquired = null;
            }
            if (acquired == null) {
                lockChannel.close();
                throw new IllegalStateException("Workspace is already open in another process");
            }
            fileLock = acquired;
            try {
                if (Files.exists(snapshot)) {
                    if (Files.size(snapshot) > MAX_BYTES) throw new IllegalStateException("Workspace exceeds its 32 MiB limit");
                    Map<String, Object> envelope = Json.object(Json.parse(Files.readString(snapshot)));
                    if (Json.integer(envelope, "schemaVersion", 0) != 1) throw new IllegalStateException("Unsupported workspace schema version");
                    state = Json.copyMap(Json.object(envelope.get("collections")));
                }
            } catch (RuntimeException | IOException e) {
                fileLock.release();
                lockChannel.close();
                throw new IllegalStateException("Workspace cannot be read. Preserve the file and restore a verified backup; no data was reset.", e);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Unable to open workspace directory", e);
        }
    }
    @Override public String mode() {
        return "atomic-file";
    }
    @Override protected void persist(Map<String, Object> candidate) {
        byte[] bytes = Json.write(Json.map("schemaVersion", 1, "collections", candidate)).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_BYTES) throw new Problem(413, "workspace_full", "Local workspace limit reached; export or remove old work");
        Path staging = directory.resolve("workspace.next");
        try {
            try (FileChannel channel = FileChannel.open(staging, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.move(staging, snapshot, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            // Directory fsync is supported on common Unix filesystems, but not by every Windows provider.
            try (FileChannel parent = FileChannel.open(directory, StandardOpenOption.READ)) {
                parent.force(true);
            } catch (IOException | UnsupportedOperationException ignored) {
                /* Atomic replacement still succeeded. */
            }
        } catch (AtomicMoveNotSupportedException e) {
            throw new IllegalStateException("Use a local filesystem with atomic moves or PostgreSQL; unsafe fallback is intentionally disabled", e);
        } catch (IOException e) {
            throw new IllegalStateException("Workspace commit failed; previous snapshot remains intact", e);
        }
    }
    @Override public void close() {
        try {
            fileLock.release();
            lockChannel.close();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to release workspace lock", e);
        }
    }
}
