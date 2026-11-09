package lab;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** In-memory learning environment. No connection to Windows or an AD domain. */
public final class DemoDirectory implements Directory {
    private record Credential(byte[] salt, byte[] hash) {}
    private final Map<String, User> users = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, Group> groups = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final Set<String> ous = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    private final Map<String, Credential> credentials = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);

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
    @Override public synchronized void createOu(String name) {
        name = Validation.label(name);
        if (!ous.add(name)) throw new IllegalArgumentException("Organizational unit already exists.");
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
    @Override public synchronized void createGroup(String name, String unit) {
        name = Validation.account(name);
        if (groups.containsKey(name) || users.containsKey(name)) throw new IllegalArgumentException("Account name already exists.");
        groups.put(name, new Group(name, ou(unit), List.of()));
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
    }
    @Override public synchronized void resetPassword(String username, char[] password) throws Exception {
        User u = user(username);
        Validation.password(password);
        credentials.put(u.username(), encode(password));
        users.put(u.username(), new User(u.username(), u.displayName(), u.ou(), u.enabled(), u.locked(), true));
    }
    @Override public synchronized void unlock(String username) {
        User u = user(username);
        users.put(u.username(), new User(u.username(), u.displayName(), u.ou(), u.enabled(), false, u.mustChangePassword()));
    }
    @Override public synchronized void simulateLock(String username) {
        User u = user(username);
        users.put(u.username(), new User(u.username(), u.displayName(), u.ou(), u.enabled(), true, u.mustChangePassword()));
    }
    @Override public synchronized void setMembership(String username, String group, boolean member) {
        User u = user(username);
        Group g = groups.get(Validation.account(group));
        if (g == null) throw new IllegalArgumentException("Group not found.");
        Set<String> members = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        members.addAll(g.members());
        if (member) members.add(u.username()); else members.remove(u.username());
        groups.put(g.name(), new Group(g.name(), g.ou(), List.copyOf(members)));
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
