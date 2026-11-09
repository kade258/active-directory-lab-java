package lab;

import com.sun.net.httpserver.*;
import javax.naming.NamingException;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.Executors;

/** Single-operator, loopback-only dashboard. Start from a trusted local terminal. */
public final class Main implements AutoCloseable {
    private final Directory directory;
    private final HttpServer server;
    private final String token;
    private final AuditLog audit;

    public Main(Directory directory, int port) throws IOException {
        this(directory, port, new AuditLog());
    }
    Main(Directory directory, int port, AuditLog audit) throws IOException {
        this.directory = directory;
        this.audit = audit;
        byte[] random = new byte[32]; new SecureRandom().nextBytes(random);
        token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 32);
        server.createContext("/", this::handle);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }
    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }
    @Override public void close() throws Exception { server.stop(0); directory.close(); }

    public static void main(String[] args) throws Exception {
        List<String> arguments = new ArrayList<>(List.of(args));
        Path dataFolder = Path.of(".adlab").toAbsolutePath();
        int option = arguments.indexOf("--data-dir");
        if (option >= 0) {
            if (option + 1 >= arguments.size()) throw new IllegalArgumentException("--data-dir requires a folder.");
            dataFolder = Path.of(arguments.remove(option + 1)).toAbsolutePath(); arguments.remove(option);
        }
        LocalFiles.Lock dataLock = new LocalFiles.Lock(dataFolder);
        try { launch(arguments.toArray(String[]::new), dataFolder, dataLock); }
        catch (Exception e) { dataLock.close(); throw e; }
    }
    private static void launch(String[] args, Path dataFolder, LocalFiles.Lock dataLock) throws Exception {
        Directory directory;
        if (args.length == 0) directory = new DemoDirectory(dataFolder.resolve("demo.properties"));
        else if (args.length == 2 && args[0].equals("--ldap")) {
            Properties config = new Properties();
            try (Reader reader = Files.newBufferedReader(Path.of(args[1]))) { config.load(reader); }
            Console console = System.console();
            if (console == null) throw new IllegalArgumentException("LDAP mode requires a terminal with an interactive password prompt.");
            char[] password = console.readPassword("Lab LDAP bind password: ");
            if (password == null || password.length == 0) throw new IllegalArgumentException("A bind password is required.");
            try { directory = new LdapDirectory(config, password); }
            finally { Arrays.fill(password, '\0'); }
        } else throw new IllegalArgumentException("Usage: java -jar active-directory-lab.jar [--ldap config/ldap.properties]");
        Main app;
        try { app = new Main(directory, 8080, new AuditLog(dataFolder.resolve("audit-" + directory.mode() + ".tsv"))); }
        catch (Exception e) { directory.close(); throw e; }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { app.close(); dataLock.close(); } catch (Exception ignored) { /* Process is shutting down. */ }
        }));
        app.start();
        System.out.println("Active Directory Lab — " + directory.mode().toUpperCase(Locale.ROOT));
        System.out.println("Open http://127.0.0.1:" + app.port() + " — Ctrl+C to stop.");
        System.out.println("Local data and activity are saved in " + dataFolder);
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String host = exchange.getRequestHeaders().getFirst("Host");
            if (!("127.0.0.1:" + port()).equals(host) && !("localhost:" + port()).equals(host)) {
                rejected("InvalidHost");
                respond(exchange, 403, "application/json", Json.encode(Map.of("error", "Invalid local host."))); return;
            }
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
            exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; connect-src 'self'; object-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'");
            String path = exchange.getRequestURI().getPath();
            try {
                if (path.equals("/api/state") && exchange.getRequestMethod().equals("GET")) {
                    respond(exchange, 200, "application/json", state());
                } else if (path.equals("/api/report") && exchange.getRequestMethod().equals("GET")) {
                    exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=directory-lab-results.md");
                    respond(exchange, 200, "text/markdown", Reports.markdown(directory.mode(), directory.snapshot(), audit));
                } else if (path.equals("/api/audit.csv") && exchange.getRequestMethod().equals("GET")) {
                    exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=directory-lab-activity.csv");
                    respond(exchange, 200, "text/csv", audit.csv());
                } else if (path.equals("/api/action") && exchange.getRequestMethod().equals("POST")) {
                    String origin = exchange.getRequestHeaders().getFirst("Origin");
                    if (!token.equals(exchange.getRequestHeaders().getFirst("X-Lab-Token")) ||
                        (origin != null && !origin.equals("http://" + host))) {
                        rejected("InvalidRequestTokenOrOrigin");
                        respond(exchange, 403, "application/json", Json.encode(Map.of("error", "Refresh this dashboard before making changes."))); return;
                    }
                    String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
                    if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/x-www-form-urlencoded")) {
                        rejected("UnsupportedContentType");
                        respond(exchange, 415, "application/json", Json.encode(Map.of("error", "Use URL-encoded form data."))); return;
                    }
                    byte[] body = exchange.getRequestBody().readNBytes(16_385);
                    if (body.length > 16_384) { Arrays.fill(body, (byte)0); rejected("RequestTooLarge"); respond(exchange, 413, "application/json", "{\"error\":\"Request is too large.\"}"); return; }
                    Map<String, String> values;
                    try { values = form(new String(body, StandardCharsets.UTF_8)); }
                    catch (IllegalArgumentException e) { rejected("MalformedForm"); throw e; }
                    finally { Arrays.fill(body, (byte) 0); }
                    synchronized (directory) { action(values); }
                    respond(exchange, 200, "application/json", "{\"message\":\"Directory updated successfully.\"}");
                } else if (exchange.getRequestMethod().equals("GET") && Set.of("/", "/app.js", "/style.css").contains(path)) {
                    String resource = path.equals("/") ? "/web/index.html" : "/web" + path;
                    try (InputStream stream = Main.class.getResourceAsStream(resource)) {
                        if (stream == null) throw new IOException("Missing bundled dashboard resource.");
                        String type = path.endsWith(".js") ? "text/javascript" : path.endsWith(".css") ? "text/css" : "text/html";
                        respond(exchange, 200, type, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                    }
                } else respond(exchange, 404, "application/json", "{\"error\":\"Route not found.\"}");
            } catch (IllegalArgumentException e) {
                respond(exchange, 400, "application/json", Json.encode(Map.of("error", e.getMessage())));
            } catch (NamingException e) {
                String message = e instanceof PartialOperationException ? e.getMessage()
                    : e instanceof javax.naming.CommunicationException || e instanceof javax.naming.InterruptedNamingException
                    ? "The connection was interrupted; the operation may have completed. Inspect ADUC before retrying."
                    : "Active Directory rejected the operation. Check connectivity, delegated rights, naming conflicts and domain password policy in ADUC. No automatic retry was made.";
                respond(exchange, 502, "application/json", Json.encode(Map.of("error", message)));
            } catch (Exception e) {
                System.err.println("Directory operation failed (" + e.getClass().getSimpleName() + ").");
                respond(exchange, 500, "application/json", "{\"error\":\"The operation could not finish. Refresh and inspect the directory before retrying.\"}");
            }
        }
    }
    private synchronized String state() throws Exception {
        Directory.Snapshot snapshot = directory.snapshot();
        List<Object> users = new ArrayList<>(), groups = new ArrayList<>();
        for (Directory.User u : snapshot.users()) users.add(Map.of("username", u.username(), "displayName", u.displayName(),
            "ou", u.ou(), "enabled", u.enabled(), "locked", u.locked(), "mustChangePassword", u.mustChangePassword()));
        for (Directory.Group g : snapshot.groups()) groups.add(Map.of("name", g.name(), "ou", g.ou(), "members", g.members()));
        return Json.encode(Map.of("mode", directory.mode(), "token", token, "users", users, "groups", groups, "ous", snapshot.ous(),
            "audit", audit.recent(), "tickets", Scenarios.describe(snapshot, audit.all())));
    }
    private void rejected(String reason) throws IOException {
        audit.record(UUID.randomUUID().toString(), directory.mode(), "request", "[request]", "REJECTED", reason);
    }
    private void action(Map<String, String> form) throws Exception {
        String action = form.getOrDefault("action", "");
        String username = form.getOrDefault("username", "");
        String name = form.getOrDefault("name", "");
        char[] password = form.getOrDefault("password", "").toCharArray();
        form.remove("password");
        Set<String> known = Set.of("create-ou", "create-group", "create-user", "reset-password", "unlock", "simulate-lock", "membership", "move-user", "set-enabled");
        if (!known.contains(action)) { Arrays.fill(password, '\0'); rejected("UnknownOperation"); throw new IllegalArgumentException("Unknown operation."); }
        String target = username.isBlank() ? name : username;
        target = target.length() > 64 || target.chars().anyMatch(Character::isISOControl) ? "[invalid target]" : target;
        if (action.equals("membership")) {
            String group = form.getOrDefault("group", "");
            target += group.matches("[a-zA-Z][a-zA-Z0-9._-]{0,19}") ? " / " + group + " / " + ("add".equals(form.get("operation")) ? "add" : "remove") : " / [invalid group]";
        }
        String id = UUID.randomUUID().toString();
        try {
            audit.record(id, directory.mode(), action, target, "STARTED", "LocalOperator");
            switch (action) {
                case "create-ou" -> directory.createOu(name);
                case "create-group" -> directory.createGroup(name, form.get("ou"));
                case "create-user" -> directory.createUser(username, form.get("displayName"), form.get("ou"), password);
                case "reset-password" -> directory.resetPassword(username, password);
                case "unlock" -> directory.unlock(username);
                case "simulate-lock" -> directory.simulateLock(username);
                case "move-user" -> directory.moveUser(username, form.get("ou"));
                case "set-enabled" -> {
                    if (!Set.of("true", "false").contains(form.getOrDefault("enabled", ""))) throw new IllegalArgumentException("Select enable or disable.");
                    directory.setEnabled(username, Boolean.parseBoolean(form.get("enabled")));
                }
                case "membership" -> {
                    String operation = form.getOrDefault("operation", "");
                    if (!Set.of("add", "remove").contains(operation)) throw new IllegalArgumentException("Select add or remove.");
                    directory.setMembership(username, form.get("group"), operation.equals("add"));
                }
                default -> throw new IllegalArgumentException("Unknown operation.");
            }
        } catch (Exception failure) {
            boolean uncertain = failure instanceof PartialOperationException || failure instanceof javax.naming.CommunicationException || failure instanceof javax.naming.InterruptedNamingException;
            try { audit.record(id, directory.mode(), action, target, uncertain ? "PARTIAL" : "FAILED", failure.getClass().getSimpleName()); }
            catch (IOException auditFailure) { failure.addSuppressed(auditFailure); }
            throw failure;
        } finally { Arrays.fill(password, '\0'); }
        // Failure here means the change may have succeeded: leave its STARTED record unresolved.
        try { audit.record(id, directory.mode(), action, target, "SUCCEEDED", "OperationCompleted"); }
        catch (IOException failure) { throw new PartialOperationException("The change completed, but its final audit record could not be saved. Inspect the directory and local storage before retrying."); }
    }
    private static Map<String, String> form(String body) {
        Map<String, String> values = new HashMap<>();
        for (String item : body.split("&")) {
            String[] pair = item.split("=", 2);
            String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
            if (values.putIfAbsent(key, URLDecoder.decode(pair.length == 2 ? pair[1] : "", StandardCharsets.UTF_8)) != null)
                throw new IllegalArgumentException("Duplicate form field.");
        }
        return values;
    }
    private static void respond(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }
}
