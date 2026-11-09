package lab;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;
import javax.naming.ldap.LdapName;
import java.nio.file.*;
import java.lang.reflect.*;
import java.lang.reflect.Proxy;

/** Dependency-free behavioral tests: run with tools/test.sh or tools/Build.ps1 -Test. */
public final class LabTests {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    @FunctionalInterface interface Checked { void run() throws Exception; }
    private static void rejects(Checked action, String message) throws Exception {
        try { action.run(); } catch (IllegalArgumentException expected) { checks++; return; }
        throw new AssertionError(message);
    }
    private static char[] password(String value) { return value.toCharArray(); }
    private static String form(String... pairs) {
        StringJoiner out = new StringJoiner("&");
        for (int i = 0; i < pairs.length; i += 2)
            out.add(URLEncoder.encode(pairs[i], StandardCharsets.UTF_8) + "=" + URLEncoder.encode(pairs[i + 1], StandardCharsets.UTF_8));
        return out.toString();
    }
    private static HttpResponse<String> post(HttpClient client, URI uri, String token, String body) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).header("Content-Type", "application/x-www-form-urlencoded");
        if (token != null) request.header("X-Lab-Token", token);
        return client.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    public static void main(String[] args) throws Exception {
        DemoDirectory demo = new DemoDirectory();
        check(demo.snapshot().users().size() == 4, "Four initial users");
        check(demo.snapshot().groups().size() == 3, "Three initial groups");
        check(demo.snapshot().users().stream().anyMatch(u -> u.username().equals("taylor.reed") && u.locked()), "Locked seed account");
        demo.createOu("Helpdesk"); demo.createGroup("GG_Helpdesk", "Helpdesk");
        demo.createUser("sam.rivera", "Sam Rivera", "Helpdesk", password("Fictional!Pass123"));
        check(demo.matchesPassword("sam.rivera", password("Fictional!Pass123")), "User password hashed correctly");
        rejects(() -> demo.createUser("SAM.RIVERA", "Duplicate", "Helpdesk", password("Fictional!Pass123")), "Case-insensitive duplicate rejected");
        rejects(() -> demo.createGroup("SAM.RIVERA", "Helpdesk"), "User/group SAM collision rejected");
        rejects(() -> demo.createOu("HELPDESK"), "Case-insensitive duplicate OU rejected");
        rejects(() -> demo.createUser("bad*)(", "Bad", "Helpdesk", password("Fictional!Pass123")), "Invalid username rejected");
        rejects(() -> demo.createUser("missing.ou", "Missing", "Unknown", password("Fictional!Pass123")), "Missing OU rejected");
        rejects(() -> demo.resetPassword("sam.rivera", password("short")), "Short password rejected");
        rejects(() -> demo.resetPassword("sam.rivera", password("lowercaseonly123")), "Complexity enforced");
        check(demo.matchesPassword("sam.rivera", password("Fictional!Pass123")), "Failed reset preserves previous password");
        demo.simulateLock("sam.rivera"); demo.resetPassword("sam.rivera", password("Another!Demo456"));
        check(!demo.matchesPassword("sam.rivera", password("Fictional!Pass123")), "Old password invalidated");
        check(demo.matchesPassword("sam.rivera", password("Another!Demo456")), "New password accepted");
        check(demo.snapshot().users().stream().anyMatch(u -> u.username().equals("sam.rivera") && u.locked() && u.mustChangePassword()), "Reset preserves lock and requires change");
        demo.unlock("SAM.RIVERA");
        check(demo.snapshot().users().stream().noneMatch(u -> u.username().equals("sam.rivera") && u.locked()), "Unlock works case-insensitively");
        demo.setMembership("sam.rivera", "GG_Helpdesk", true); demo.setMembership("SAM.RIVERA", "gg_helpdesk", true);
        check(demo.snapshot().groups().stream().anyMatch(g -> g.name().equals("GG_Helpdesk") && g.members().equals(List.of("sam.rivera"))), "Membership add is idempotent");
        demo.setMembership("sam.rivera", "GG_Helpdesk", false);
        check(demo.snapshot().groups().stream().anyMatch(g -> g.name().equals("GG_Helpdesk") && g.members().isEmpty()), "Membership removal");
        rejects(() -> demo.unlock("unknown"), "Unknown user rejected");
        rejects(() -> demo.setMembership("sam.rivera", "missing", true), "Unknown group rejected");
        demo.moveUser("sam.rivera", "HR"); demo.setEnabled("sam.rivera", false);
        check(demo.snapshot().users().stream().anyMatch(u -> u.username().equals("sam.rivera") && u.ou().equals("HR") && !u.enabled()), "Transfer and disable update account");
        rejects(() -> demo.moveUser("sam.rivera", "missing"), "Move to nonexistent OU rejected");
        check(demo.snapshot().users().stream().anyMatch(u -> u.username().equals("sam.rivera") && u.ou().equals("HR")), "Failed transfer preserves location");
        check(Json.encode(Map.of("quote", "\"\\\n\u0001")).contains("\\u0001"), "JSON escapes control characters");
        String dn = LdapDirectory.child("OU", "IT,DC=outside", "OU=Lab,DC=example,DC=test");
        LdapName parsed = new LdapName(dn);
        check(parsed.size() == 4 && parsed.getRdn(3).getValue().equals("IT,DC=outside"), "DN escaping prevents container injection");
        check(new String(LdapDirectory.passwordBytes(password("Test!Password1")), StandardCharsets.UTF_16LE).equals("\"Test!Password1\""), "AD password uses quoted UTF-16LE");
        Properties config = new Properties(); config.setProperty("ldap.url", "ldap://localhost:389");
        rejects(() -> new LdapDirectory(config, password("unused")), "Plaintext LDAP rejected before connection");
        config.setProperty("ldap.url", "ldaps://localhost:636/path");
        rejects(() -> new LdapDirectory(config, password("unused")), "LDAP URL path rejected");
        config.setProperty("ldap.url", "ldaps://localhost:636"); config.setProperty("ldap.rootDn", "DC=example,DC=test");
        rejects(() -> new LdapDirectory(config, password("unused")), "Domain root cannot be managed");

        try (Main app = new Main(new DemoDirectory(), 0); HttpClient client = HttpClient.newHttpClient()) {
            app.start(); URI base = URI.create("http://127.0.0.1:" + app.port());
            var response = client.send(HttpRequest.newBuilder(base.resolve("/")).GET().build(), HttpResponse.BodyHandlers.ofString());
            check(response.statusCode() == 200 && response.body().contains("Directory Lab"), "Dashboard served");
            check(response.headers().firstValue("Content-Security-Policy").orElse("").contains("frame-ancestors 'none'"), "CSP present");
            for (String resource : List.of("/app.js", "/style.css")) {
                var resourceResponse = client.send(HttpRequest.newBuilder(base.resolve(resource)).GET().build(), HttpResponse.BodyHandlers.ofString());
                check(resourceResponse.statusCode() == 200 && !resourceResponse.body().isBlank(), "Bundled asset served: " + resource);
            }
            response = client.send(HttpRequest.newBuilder(base.resolve("/api/state")).GET().build(), HttpResponse.BodyHandlers.ofString());
            Matcher matcher = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(response.body());
            check(matcher.find(), "CSRF token provided"); String token = matcher.group(1);
            URI action = base.resolve("/api/action");
            check(post(client, action, null, form("action", "unlock", "username", "taylor.reed")).statusCode() == 403, "Mutation without token rejected");
            check(post(client, action, "invalid", form("action", "unlock", "username", "taylor.reed")).statusCode() == 403, "Wrong token rejected");
            check(post(client, action, token, form("action", "unlock", "username", "taylor.reed")).statusCode() == 200, "HTTP unlock succeeds");
            check(post(client, action, token, form("action", "reset-password", "username", "taylor.reed", "password", "short")).statusCode() == 400, "HTTP invalid password rejected");
            check(post(client, action, token, form("action", "reset-password", "username", "taylor.reed", "password", "Testing!Reset789")).statusCode() == 200, "HTTP password reset succeeds");
            check(post(client, action, token, form("action", "create-ou", "name", "Support")).statusCode() == 200, "HTTP OU creation succeeds");
            check(post(client, action, token, form("action", "create-group", "name", "GG_Support", "ou", "Support")).statusCode() == 200, "HTTP group creation succeeds");
            check(post(client, action, token, form("action", "create-user", "username", "sam.rivera", "displayName", "Sam <Rivera>", "ou", "Support", "password", "Testing!Create789")).statusCode() == 200, "HTTP user creation succeeds");
            check(post(client, action, token, form("action", "membership", "username", "sam.rivera", "group", "GG_Support", "operation", "add")).statusCode() == 200, "HTTP membership succeeds");
            check(post(client, action, token, form("action", "membership", "username", "sam.rivera", "group", "GG_Support", "operation", "invalid")).statusCode() == 400, "Invalid membership action rejected");
            check(post(client, action, token, "action=unlock&action=create-user").statusCode() == 400, "Duplicate form fields rejected");
            HttpRequest crossOrigin = HttpRequest.newBuilder(action).header("X-Lab-Token", token).header("Content-Type", "application/x-www-form-urlencoded").header("Origin", "https://untrusted.example").POST(HttpRequest.BodyPublishers.ofString(form("action", "unlock", "username", "taylor.reed"))).build();
            check(client.send(crossOrigin, HttpResponse.BodyHandlers.ofString()).statusCode() == 403, "Cross-origin mutation rejected");
            check(post(client, action, token, "x=" + "a".repeat(17_000)).statusCode() == 413, "Oversized body rejected");
            check(client.send(HttpRequest.newBuilder(base.resolve("/../README.md")).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode() == 404, "Arbitrary local files not served");
            response = client.send(HttpRequest.newBuilder(base.resolve("/api/state")).GET().build(), HttpResponse.BodyHandlers.ofString());
            check(response.body().contains("GG_Support") && response.body().contains("Sam <Rivera>"), "Created objects appear in directory");
            check(!response.body().contains("Testing!") && !response.body().contains("Fictional!"), "No passwords in state or audit");
            check(response.body().contains("reset-password") && response.body().contains("membership"), "Successful changes audited");
            check(response.body().contains("FAILED") && response.body().contains("REJECTED") && response.body().contains("STARTED"), "Failures and rejected writes audited alongside attempts");
            check(post(client, action, token, form("action", "move-user", "username", "sam.rivera", "ou", "HR")).statusCode() == 200, "HTTP department move succeeds");
            check(post(client, action, token, form("action", "set-enabled", "username", "sam.rivera", "enabled", "false")).statusCode() == 200, "HTTP offboarding disable succeeds");
            check(post(client, action, token, form("action", "set-enabled", "username", "sam.rivera", "enabled", "maybe")).statusCode() == 400, "Ambiguous enable/disable rejected");
            response = client.send(HttpRequest.newBuilder(base.resolve("/api/report")).GET().build(), HttpResponse.BodyHandlers.ofString());
            check(response.statusCode() == 200 && response.body().contains("DEMO") && response.body().contains("HD-101") && response.body().contains("require separate evidence"), "Report labels simulated evidence honestly");
            response = client.send(HttpRequest.newBuilder(base.resolve("/api/audit.csv")).GET().build(), HttpResponse.BodyHandlers.ofString());
            check(response.statusCode() == 200 && response.body().contains("SUCCEEDED") && response.body().contains("FAILED") && !response.body().contains("Testing!"), "Audit export includes outcomes without passwords");
        }
        persistence();
        partialAndDenied();
        unavailableJournal();
        checks += LdapAdapterTests.run();
        System.out.println("PASS: " + checks + " behavioral checks. Live AD integration requires the documented Windows lab.");
    }

    private static void persistence() throws Exception {
        Path folder = Files.createTempDirectory("adlab-test-");
        try {
            Path data = folder.resolve("demo.properties");
            DemoDirectory saved = new DemoDirectory(data);
            saved.createOu("Support"); saved.createGroup("GG_Support", "Support");
            saved.createUser("sam.rivera", "Sam Rivera", "Support", password("Persistent!Pass123"));
            saved.setMembership("sam.rivera", "GG_Support", true); saved.simulateLock("sam.rivera");
            saved.resetPassword("sam.rivera", password("Persistent!Reset456")); saved.setEnabled("casey.brooks", false);
            Directory.Snapshot before = saved.snapshot(); DemoDirectory reloaded = new DemoDirectory(data);
            check(before.equals(reloaded.snapshot()), "All demo objects, flags and memberships survive reload");
            check(reloaded.matchesPassword("sam.rivera", password("Persistent!Reset456")), "Password hash survives reload");
            check(!reloaded.matchesPassword("sam.rivera", password("Persistent!Pass123")), "Old password stays invalid after restart");
            check(!Files.readString(data).contains("Persistent!"), "No plaintext password in saved state");
            try (var children = Files.list(folder)) { check(children.noneMatch(p -> p.getFileName().toString().startsWith(".write-")), "Atomic saves leave no temporary file"); }
            Path journal = folder.resolve("audit.tsv"); AuditLog audit = new AuditLog(journal);
            String id = UUID.randomUUID().toString();
            audit.record(id, "demo", "reset-password", "sam.rivera", "STARTED", "LocalOperator");
            audit.record(id, "demo", "reset-password", "sam.rivera", "FAILED", "NoPermissionException");
            audit.record(UUID.randomUUID().toString(), "demo", "create-ou", "=FORMULA()", "REJECTED", "Validation");
            AuditLog loaded = new AuditLog(journal);
            check(loaded.all().equals(audit.all()), "Audit outcomes and correlation IDs survive reload");
            check(loaded.csv().contains("'=FORMULA()"), "Audit CSV neutralizes formula injection");
            try (LocalFiles.Lock held = new LocalFiles.Lock(folder)) {
                try (LocalFiles.Lock duplicate = new LocalFiles.Lock(folder)) { throw new AssertionError("Concurrent data folder lock acquired"); }
                catch (java.io.IOException expected) { checks++; }
            }
            try (LocalFiles.Lock released = new LocalFiles.Lock(folder)) { checks++; }
            Files.writeString(data, "version=damaged\n");
            try { new DemoDirectory(data); throw new AssertionError("Damaged data silently reset"); }
            catch (java.io.IOException expected) { checks++; }
            check(Files.readString(data).contains("damaged"), "Damaged state preserved for inspection");
            Files.writeString(journal, "damaged\n");
            try { new AuditLog(journal); throw new AssertionError("Damaged audit silently reset"); }
            catch (java.io.IOException expected) { checks++; }
        } finally {
            try (var files = Files.walk(folder)) { for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
        }
    }
    private static void partialAndDenied() throws Exception {
        DemoDirectory demo = new DemoDirectory(); AuditLog audit = new AuditLog();
        Directory faulty = (Directory)Proxy.newProxyInstance(Directory.class.getClassLoader(), new Class[]{Directory.class}, (proxy, method, args) -> {
            if (method.getName().equals("resetPassword")) throw new PartialOperationException("Password was set, but requiring a change at next sign-in failed. Inspect the account in ADUC.");
            if (method.getName().equals("unlock")) throw new javax.naming.NoPermissionException("Private server diagnostic");
            if (method.getName().equals("moveUser")) throw new javax.naming.CommunicationException("Private connection diagnostic");
            try { return method.invoke(demo, args); } catch (InvocationTargetException e) { throw e.getCause(); }
        });
        try (Main app = new Main(faulty, 0, audit); HttpClient client = HttpClient.newHttpClient()) {
            app.start(); URI base = URI.create("http://127.0.0.1:" + app.port());
            String body = client.send(HttpRequest.newBuilder(base.resolve("/api/state")).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
            Matcher matcher = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(body); check(matcher.find(), "Fault-test token"); String token = matcher.group(1);
            var response = post(client, base.resolve("/api/action"), token, form("action", "reset-password", "username", "taylor.reed", "password", "Partial!Reset789"));
            check(response.statusCode() == 502 && response.body().contains("Password was set"), "Partial AD outcome requires inspection");
            check(audit.all().stream().anyMatch(e -> e.outcome().equals("PARTIAL")), "Partial operation distinctly audited");
            response = post(client, base.resolve("/api/action"), token, form("action", "unlock", "username", "taylor.reed"));
            check(response.statusCode() == 502 && !response.body().contains("Private server diagnostic"), "Denied AD action does not leak raw server diagnostics");
            check(audit.all().stream().anyMatch(e -> e.outcome().equals("FAILED") && e.detail().equals("NoPermissionException")), "Denied operation recorded without sensitive diagnostic");
            check(!audit.csv().contains("Partial!Reset789") && !audit.csv().contains("Private server diagnostic"), "Failure journal excludes credentials and raw diagnostics");
            response = post(client, base.resolve("/api/action"), token, form("action", "move-user", "username", "taylor.reed", "ou", "IT"));
            check(response.statusCode() == 502 && response.body().contains("may have completed"), "Connection loss reports uncertain outcome");
            check(audit.all().stream().anyMatch(e -> e.action().equals("move-user") && e.outcome().equals("PARTIAL")), "Connection loss audited as uncertain, not definitive failure");
            for (AuditLog.Event event : audit.all()) {
                if (Set.of("PARTIAL", "FAILED", "SUCCEEDED").contains(event.outcome()))
                    check(audit.all().stream().anyMatch(start -> start.id().equals(event.id()) && start.outcome().equals("STARTED")), "Final outcome correlates with its saved attempt");
            }
        }
        DemoDirectory scenario = new DemoDirectory();
        scenario.createOu("Helpdesk"); scenario.createGroup("GG_Helpdesk", "Helpdesk");
        scenario.createUser("sam.rivera", "Sam Rivera", "Helpdesk", password("Scenario!Pass123"));
        scenario.setMembership("sam.rivera", "GG_Helpdesk", true);
        check(Scenarios.describe(scenario.snapshot(), List.of()).get(0).get("status").equals("Checks satisfied"), "Onboarding checks reflect actual directory state");
        scenario.setMembership("sam.rivera", "GG_Helpdesk", false);
        check(Scenarios.describe(scenario.snapshot(), List.of()).get(0).get("status").equals("Open"), "Ticket reopens when required access is removed");
    }
    private static void unavailableJournal() throws Exception {
        Path folder = Files.createTempDirectory("adlab-journal-outage-");
        AuditLog audit = new AuditLog(folder.resolve("audit.tsv"));
        Files.delete(folder);
        DemoDirectory directory = new DemoDirectory();
        try (Main app = new Main(directory, 0, audit); HttpClient client = HttpClient.newHttpClient()) {
            app.start(); URI base = URI.create("http://127.0.0.1:" + app.port());
            String body = client.send(HttpRequest.newBuilder(base.resolve("/api/state")).GET().build(), HttpResponse.BodyHandlers.ofString()).body();
            Matcher matcher = Pattern.compile("\"token\":\"([^\"]+)\"").matcher(body); check(matcher.find(), "Journal-outage token");
            var response = post(client, base.resolve("/api/action"), matcher.group(1), form("action", "create-ou", "name", "MustNotExist"));
            check(response.statusCode() == 500, "Journal outage refuses the action");
            check(!directory.snapshot().ous().contains("MustNotExist"), "No mutation if the attempt cannot be saved");
        }
    }
}
