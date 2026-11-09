package lab;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Properties;

/** Small local storage helpers. No Java object deserialization is used. */
final class LocalFiles {
    private LocalFiles() {}
    static void privateDirectory(Path path) throws IOException {
        Files.createDirectories(path);
        if (Files.getFileStore(path).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rwx------"));
    }
    static void privateFile(Path path) throws IOException {
        if (Files.getFileStore(path).supportsFileAttributeView("posix"))
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
    }
    static void writeProperties(Path path, Properties values) throws IOException {
        path = path.toAbsolutePath();
        privateDirectory(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), ".write-", ".tmp");
        try {
            privateFile(temporary);
            try (Writer writer = Files.newBufferedWriter(temporary)) { values.store(writer, "Directory Lab local state — contains salted password hashes"); }
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) { channel.force(true); }
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }
    static final class Lock implements AutoCloseable {
        private final FileChannel channel;
        private final FileLock lock;
        Lock(Path folder) throws IOException {
            privateDirectory(folder);
            channel = FileChannel.open(folder.resolve("runtime.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            privateFile(folder.resolve("runtime.lock"));
            FileLock acquired;
            try { acquired = channel.tryLock(); }
            catch (OverlappingFileLockException e) { channel.close(); throw new IOException("This lab data folder is already in use."); }
            if (acquired == null) { channel.close(); throw new IOException("This lab data folder is already in use."); }
            lock = acquired;
        }
        @Override public void close() throws IOException { try { lock.release(); } finally { channel.close(); } }
    }
}
