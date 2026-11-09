package lab;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Append-only application journal, separate from authoritative Windows auditing. */
final class AuditLog {
    record Event(String id, String time, String mode, String action, String target, String outcome, String detail) {
        Map<String, Object> json() {
            return Map.of("id", id, "time", time, "mode", mode, "action", action, "target", target, "outcome", outcome, "detail", detail);
        }
    }
    private final Path file;
    private final List<Event> events = new ArrayList<>();
    AuditLog() { file = null; }
    AuditLog(Path file) throws IOException {
        this.file = file.toAbsolutePath();
        LocalFiles.privateDirectory(this.file.getParent());
        if (Files.exists(file)) {
            try (BufferedReader reader = Files.newBufferedReader(file)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] fields = line.split("\\t", -1);
                    if (fields.length != 7) throw new IOException("Audit journal is damaged; preserve it and inspect before restarting.");
                    try {
                        for (int i = 0; i < fields.length; i++) fields[i] = new String(Base64.getDecoder().decode(fields[i]), StandardCharsets.UTF_8);
                        UUID.fromString(fields[0]); Instant.parse(fields[1]);
                        if (!Set.of("demo", "ldap").contains(fields[2]) || !Set.of("STARTED", "SUCCEEDED", "FAILED", "REJECTED", "PARTIAL").contains(fields[5])) throw new IllegalArgumentException();
                        events.add(new Event(fields[0], fields[1], fields[2], fields[3], fields[4], fields[5], fields[6]));
                    } catch (IllegalArgumentException e) { throw new IOException("Audit journal is damaged; preserve it and inspect before restarting.", e); }
                }
            }
            LocalFiles.privateFile(this.file);
        }
    }
    synchronized void record(String id, String mode, String action, String target, String outcome, String detail) throws IOException {
        Event event = new Event(id, Instant.now().toString(), mode, action, target, outcome, detail);
        if (file != null) {
            List<String> fields = List.of(event.id(), event.time(), mode, action, target, outcome, detail);
            String line = String.join("\t", fields.stream().map(s -> Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8))).toList()) + "\n";
            try (FileChannel channel = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
                LocalFiles.privateFile(file);
                ByteBuffer buffer = ByteBuffer.wrap(line.getBytes(StandardCharsets.UTF_8));
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
        }
        events.add(event);
    }
    synchronized List<Event> all() { return List.copyOf(events); }
    synchronized List<Map<String, Object>> recent() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = events.size() - 1; i >= Math.max(0, events.size() - 200); i--) out.add(events.get(i).json());
        return out;
    }
    synchronized String csv() {
        StringBuilder out = new StringBuilder("id,time,mode,action,target,outcome,detail\r\n");
        for (Event e : events) out.append(String.join(",", List.of(e.id(), e.time(), e.mode(), e.action(), e.target(), e.outcome(), e.detail()).stream().map(AuditLog::cell).toList())).append("\r\n");
        return out.toString();
    }
    private static String cell(String value) {
        if (!value.isEmpty() && "=+-@\t\r\n".indexOf(value.charAt(0)) >= 0) value = "'" + value;
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
