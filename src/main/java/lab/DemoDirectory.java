package lab;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.io.*;
import java.nio.file.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Fictional directory, optionally backed by an atomic local state file; never a Windows domain. */
public final class DemoDirectory implements Directory {
    private record Credential(byte[] salt, byte[] hash) {}
    private final Map<String, User> users = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, Group> groups = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Set<String> ous = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, Credential> credentials = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private Path file;

    public DemoDirectory(Path file) throws Exception {
        this();
        this.file = file.toAbsolutePath();
        if (Files.exists(this.file)) load(); else persist();
    }

    public DemoDirectory() throws Exception {
        for (String ou : List.of("IT", "HR", "Finance")) createOu(ou);
        createGroup("GG_IT", "IT");
        createGroup("GG_HR", "HR");
        createGroup("GG_Finance", "Finance");
        seed("alex.morgan", "Alex Morgan", "IT", "GG_IT");
        seed("jordan.lee", "Jordan Lee", "IT", "GG_IT");
        seed("taylor.reed", "Taylor Reed", "HR", "GG_HR");
        seed("casey.brooks", "Casey Brooks", "Finance", "GG_Finance");
        simulateLock("taylor.reed");
    }

    private void seed(String name, String display, String ou, String group) throws Exception {
        char[] random = ("Seed!1aA" + UUID.randomUUID()).toCharArray();
        try { createUser(name, display, ou, random); }
        finally { Arrays.fill(random, '\0'); }
        setMembership(name, group, true);
    }

    @Override public String mode() { return "demo"; }
    @Override public synchronized Snapshot snapshot() {
        return new Snapshot(List.copyOf(users.values()), List.copyOf(groups.values()), List.copyOf(ous));
    }
    @Override public synchronized void createOu(String name) throws Exception {
        name = Validation.label(name);
        if (!ous.add(name)) throw new IllegalArgumentException("Organizational unit already exists.");
        persist();
    }
    private String ou(String name) {
        return ous.stream().filter(x -> x.equalsIgnoreCase(name)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Select an existing organizational unit."));
    }
    private User user(String username) {
        User user = users.get(Validation.account(username));
        if (user == null) throw new IllegalArgumentException("User not found.");
        return user;
    }
    @Override public synchronized void createGroup(String name, String unit) throws Exception {
        name = Validation.account(name);
        if (groups.containsKey(name) || users.containsKey(name)) throw new IllegalArgumentException("Account name already exists.");
        groups.put(name, new Group(name, ou(unit), List.of()));
        persist();
    }
    @Override public synchronized void createUser(String username, String displayName, String unit, char[] password) throws Exception {
        username = Validation.account(username);
        displayName = Validation.label(displayName);
        unit = ou(unit);
        Validation.password(password);
        if (users.containsKey(username) || groups.containsKey(username)) throw new IllegalArgumentException("Account name already exists.");
        Credential credential = encode(password);
        users.put(username, new User(username, displayName, unit, true, false, true));
        credentials.put(username, credential);
        persist();
    }
    @Override public synchronized void resetPassword(String username, char[] password) throws Exception {
        User u = user(username);
        Validation.password(password);
        credentials.put(u.username(), encode(password));
        users.put(u.username(), new User(u.username(), u.displayName(), u.ou(), u.enabled(), u.locked(), true));
        persist();
    }
    @Override public synchronized void unlock(String username) throws Exception {
        User u = user(username);
        users.put(u.username(), new User(u.username(), u.displayName(), u.ou(), u.enabled(), false, u.mustChangePassword()));
        persist();
    }
    @Override public synchronized void simulateLock(String username) throws Exception {
        User u = user(username);
        users.put(u.username(), new User(u.username(), u.displayName(), u.ou(), u.enabled(), true, u.mustChangePassword()));
        persist();
    }
    @Override public synchronized void setMembership(String username, String group, boolean member) throws Exception {
        User u = user(username);
        Group g = groups.get(Validation.account(group));
        if (g == null) throw new IllegalArgumentException("Group not found.");
        Set<String> members = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        members.addAll(g.members());
        if (member) members.add(u.username()); else members.remove(u.username());
        groups.put(g.name(), new Group(g.name(), g.ou(), List.copyOf(members)));
        persist();
    }

    @Override public synchronized void moveUser(String username, String unit) throws Exception {
        User u = user(username);
        users.put(u.username(), new User(u.username(), u.displayName(), ou(unit), u.enabled(), u.locked(), u.mustChangePassword()));
        persist();
    }
    @Override public synchronized void setEnabled(String username, boolean enabled) throws Exception {
        User u = user(username);
        users.put(u.username(), new User(u.username(), u.displayName(), u.ou(), enabled, u.locked(), u.mustChangePassword()));
        persist();
    }

    private void persist() throws Exception {
        if (file == null) return;
        Properties p = new Properties(); p.setProperty("version", "1");
        p.setProperty("ou.count", "" + ous.size()); int i = 0;
        for (String ou : ous) p.setProperty("ou." + i++, ou);
        p.setProperty("user.count", "" + users.size()); i = 0;
        for (User u : users.values()) {
            String key = "user." + i++ + ".";
            p.setProperty(key + "username", u.username()); p.setProperty(key + "name", u.displayName());
            p.setProperty(key + "ou", u.ou()); p.setProperty(key + "enabled", "" + u.enabled());
            p.setProperty(key + "locked", "" + u.locked()); p.setProperty(key + "mustChange", "" + u.mustChangePassword());
            Credential c = credentials.get(u.username());
            p.setProperty(key + "salt", Base64.getEncoder().encodeToString(c.salt()));
            p.setProperty(key + "hash", Base64.getEncoder().encodeToString(c.hash()));
        }
        p.setProperty("group.count", "" + groups.size()); i = 0;
        for (Group g : groups.values()) {
            String key = "group." + i++ + ".";
            p.setProperty(key + "name", g.name()); p.setProperty(key + "ou", g.ou());
            p.setProperty(key + "members", String.join(",", g.members()));
        }
        try { LocalFiles.writeProperties(file, p); }
        catch (Exception failure) {
            // The old file remains authoritative if an atomic write fails.
            if (Files.exists(file)) load();
            throw new IOException("Demo data could not be saved; the previous saved state was restored.", failure);
        }
    }
    private void load() throws Exception {
        Properties p = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) { p.load(reader); }
        if (!"1".equals(p.getProperty("version"))) throw new IOException("Unsupported or damaged demo state; it was not reset.");
        Map<String, User> loadedUsers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, Group> loadedGroups = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, Credential> loadedCredentials = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> loadedOus = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (int i = 0; i < count(p, "ou.count"); i++) {
            if (!loadedOus.add(Validation.label(p.getProperty("ou." + i)))) throw new IOException("Duplicate saved OU.");
        }
        for (int i = 0; i < count(p, "user.count"); i++) {
            String k = "user." + i + ".", username = Validation.account(p.getProperty(k + "username"));
            String unit = p.getProperty(k + "ou");
            if (!loadedOus.contains(unit) || loadedUsers.containsKey(username)) throw new IOException("Invalid saved user.");
            loadedUsers.put(username, new User(username, Validation.label(p.getProperty(k + "name")), unit,
                bool(p, k + "enabled"), bool(p, k + "locked"), bool(p, k + "mustChange")));
            byte[] salt = Base64.getDecoder().decode(p.getProperty(k + "salt", ""));
            byte[] hash = Base64.getDecoder().decode(p.getProperty(k + "hash", ""));
            if (salt.length != 16 || hash.length != 32) throw new IOException("Invalid saved password hash.");
            loadedCredentials.put(username, new Credential(salt, hash));
        }
        for (int i = 0; i < count(p, "group.count"); i++) {
            String k = "group." + i + ".", name = Validation.account(p.getProperty(k + "name")), unit = p.getProperty(k + "ou");
            List<String> members = p.getProperty(k + "members", "").isEmpty() ? List.of() : List.of(p.getProperty(k + "members").split(","));
            if (!loadedOus.contains(unit) || loadedGroups.containsKey(name) || loadedUsers.containsKey(name) || !loadedUsers.keySet().containsAll(members)) throw new IOException("Invalid saved group.");
            loadedGroups.put(name, new Group(name, unit, members));
        }
        users.clear(); users.putAll(loadedUsers); groups.clear(); groups.putAll(loadedGroups);
        ous.clear(); ous.addAll(loadedOus); credentials.clear(); credentials.putAll(loadedCredentials);
    }
    private static int count(Properties p, String key) throws IOException {
        try { int n = Integer.parseInt(p.getProperty(key)); if (n < 0 || n > 10_000) throw new NumberFormatException(); return n; }
        catch (Exception e) { throw new IOException("Invalid saved object count.", e); }
    }
    private static boolean bool(Properties p, String key) throws IOException {
        String value = p.getProperty(key);
        if (!"true".equals(value) && !"false".equals(value)) throw new IOException("Invalid saved account flag.");
        return Boolean.parseBoolean(value);
    }

    // Test helper: the dashboard does not offer a Windows or application login service.
    synchronized boolean matchesPassword(String username, char[] password) throws Exception {
        Credential credential = credentials.get(user(username).username());
        return MessageDigest.isEqual(credential.hash(), hash(password, credential.salt()));
    }
    private static Credential encode(char[] password) throws Exception {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        return new Credential(salt, hash(password, salt));
    }
    private static byte[] hash(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, 210_000, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
}
