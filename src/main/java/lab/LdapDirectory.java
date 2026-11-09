package lab;

import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.ldap.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Uses standard JNDI to manage objects only beneath one explicitly configured lab OU. */
public final class LdapDirectory implements Directory {
    private final DirContext context;
    private final String root;
    private final String upnSuffix;

    public LdapDirectory(Properties config, char[] password) throws Exception {
        URI uri = URI.create(required(config, "ldap.url"));
        if (!"ldaps".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null ||
            (uri.getPath() != null && !uri.getPath().isEmpty()) || uri.getQuery() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("ldap.url must be an ldaps://hostname:636 URL with no path or credentials.");
        root = required(config, "ldap.rootDn");
        LdapName rootName = new LdapName(root);
        if (rootName.size() < 3 || !rootName.getRdn(rootName.size() - 1).getType().equalsIgnoreCase("OU"))
            throw new IllegalArgumentException("ldap.rootDn must name an existing dedicated lab OU, not a domain root.");
        upnSuffix = required(config, "ldap.upnSuffix");
        if (!upnSuffix.matches("[a-zA-Z0-9.-]+")) throw new IllegalArgumentException("Invalid UPN suffix.");
        Hashtable<String, Object> env = new Hashtable<>();
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, uri.toString());
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, required(config, "ldap.bindDn"));
        env.put(Context.SECURITY_CREDENTIALS, password);
        env.put(Context.REFERRAL, "throw");
        env.put("com.sun.jndi.ldap.connect.timeout", "5000");
        env.put("com.sun.jndi.ldap.read.timeout", "10000");
        DirContext opened = new InitialDirContext(env);
        try {
            Attributes attributes = opened.getAttributes(root, new String[]{"objectClass"});
            if (!attributes.get("objectClass").contains("organizationalUnit"))
                throw new IllegalArgumentException("Configured lab root is not an organizational unit.");
        } catch (Exception e) { opened.close(); throw e; }
        context = opened;
    }

    private static String required(Properties config, String key) {
        String value = config.getProperty(key, "").strip();
        if (value.isEmpty()) throw new IllegalArgumentException("Missing configuration: " + key);
        return value;
    }
    @Override public String mode() { return "ldap"; }
    static String child(String attribute, String value, String parent) throws InvalidNameException {
        return new LdapName(parent).add(new Rdn(attribute, value)).toString();
    }
    private String ouDn(String ou) throws Exception {
        String dn = child("OU", Validation.label(ou), root);
        Attributes attributes = context.getAttributes(dn, new String[]{"objectClass"});
        if (!attributes.get("objectClass").contains("organizationalUnit")) throw new IllegalArgumentException("OU not found.");
        return dn;
    }
    private List<SearchResult> search(String base, String filter, Object[] values, int scope, String... attributes) throws Exception {
        SearchControls controls = new SearchControls();
        controls.setSearchScope(scope);
        controls.setReturningAttributes(attributes);
        controls.setCountLimit(500);
        controls.setTimeLimit(10_000);
        List<SearchResult> results = new ArrayList<>();
        NamingEnumeration<SearchResult> found = context.search(base, filter, values, controls);
        try { while (found.hasMore()) results.add(found.next()); }
        finally { found.close(); }
        return results;
    }
    private String find(String username, String objectClass) throws Exception {
        Validation.account(username);
        String filter = objectClass.equals("user")
            ? "(&(objectCategory=person)(objectClass=user)(sAMAccountName={0}))"
            : "(&(objectClass=group)(sAMAccountName={0}))";
        List<SearchResult> results = search(root, filter, new Object[]{username}, SearchControls.SUBTREE_SCOPE, "distinguishedName");
        if (results.size() != 1) throw new IllegalArgumentException("Expected exactly one lab " + objectClass + " with that account name.");
        return scoped(results.get(0).getNameInNamespace());
    }
    private String scoped(String dn) throws InvalidNameException {
        LdapName name = new LdapName(dn), base = new LdapName(root);
        if (name.size() <= base.size() || !name.startsWith(base)) throw new IllegalArgumentException("Object is outside the lab OU.");
        return dn;
    }
    private static String text(Attributes attributes, String key, String fallback) throws NamingException {
        Attribute attribute = attributes.get(key);
        return attribute == null ? fallback : String.valueOf(attribute.get());
    }
    private String unit(String dn) throws InvalidNameException {
        LdapName name = new LdapName(dn);
        return name.size() > new LdapName(root).size() + 1 ? String.valueOf(name.getRdn(name.size() - 2).getValue()) : "Lab root";
    }
    @Override public synchronized Snapshot snapshot() throws Exception {
        List<String> ous = new ArrayList<>();
        for (SearchResult result : search(root, "(objectClass=organizationalUnit)", new Object[0], SearchControls.ONELEVEL_SCOPE, "ou"))
            ous.add(text(result.getAttributes(), "ou", ""));
        List<User> users = new ArrayList<>();
        Map<String, String> userNames = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (SearchResult result : search(root, "(&(objectCategory=person)(objectClass=user))", new Object[0], SearchControls.SUBTREE_SCOPE,
                "sAMAccountName", "displayName", "userAccountControl", "msDS-User-Account-Control-Computed", "pwdLastSet")) {
            Attributes a = result.getAttributes();
            String username = text(a, "sAMAccountName", "");
            int flags = Integer.parseInt(text(a, "userAccountControl", "0"));
            int computed = Integer.parseInt(text(a, "msDS-User-Account-Control-Computed", "0"));
            users.add(new User(username, text(a, "displayName", username), unit(result.getNameInNamespace()),
                (flags & 2) == 0, (computed & 16) != 0, text(a, "pwdLastSet", "1").equals("0")));
            userNames.put(new LdapName(result.getNameInNamespace()).toString(), username);
        }
        List<Group> groups = new ArrayList<>();
        for (SearchResult result : search(root, "(objectClass=group)", new Object[0], SearchControls.SUBTREE_SCOPE, "sAMAccountName", "member")) {
            List<String> members = new ArrayList<>();
            Attribute member = result.getAttributes().get("member");
            if (member != null) for (int i = 0; i < member.size(); i++) {
                String dn = String.valueOf(member.get(i));
                members.add(userNames.getOrDefault(new LdapName(dn).toString(), dn));
            }
            Collections.sort(members);
            groups.add(new Group(text(result.getAttributes(), "sAMAccountName", ""), unit(result.getNameInNamespace()), List.copyOf(members)));
        }
        users.sort(Comparator.comparing(User::username));
        groups.sort(Comparator.comparing(Group::name));
        Collections.sort(ous);
        return new Snapshot(List.copyOf(users), List.copyOf(groups), List.copyOf(ous));
    }
    @Override public synchronized void createOu(String name) throws Exception {
        name = Validation.label(name);
        Attributes attrs = new BasicAttributes(true);
        attrs.put("objectClass", "organizationalUnit"); attrs.put("ou", name);
        context.createSubcontext(child("OU", name, root), attrs).close();
    }
    @Override public synchronized void createGroup(String name, String ou) throws Exception {
        name = Validation.account(name);
        Attributes attrs = new BasicAttributes(true);
        attrs.put("objectClass", "group"); attrs.put("cn", name);
        attrs.put("sAMAccountName", name); attrs.put("groupType", "-2147483646");
        context.createSubcontext(child("CN", name, ouDn(ou)), attrs).close();
    }
    @Override public synchronized void createUser(String username, String displayName, String ou, char[] password) throws Exception {
        username = Validation.account(username); displayName = Validation.label(displayName); Validation.password(password);
        String dn = child("CN", username, ouDn(ou));
        Attributes attrs = new BasicAttributes(true);
        BasicAttribute classes = new BasicAttribute("objectClass");
        for (String value : List.of("top", "person", "organizationalPerson", "user")) classes.add(value);
        attrs.put(classes); attrs.put("cn", username); attrs.put("sn", displayName);
        attrs.put("displayName", displayName); attrs.put("sAMAccountName", username);
        attrs.put("userPrincipalName", username + "@" + upnSuffix); attrs.put("userAccountControl", "514");
        context.createSubcontext(dn, attrs).close();
        try {
            writePassword(dn, password);
            replace(dn, "userAccountControl", "512");
        } catch (Exception e) {
            throw new NamingException("The user object was created but setup did not finish. Inspect the disabled account in ADUC before retrying.");
        }
    }
    private void replace(String dn, String key, Object value) throws Exception {
        context.modifyAttributes(scoped(dn), new ModificationItem[]{
            new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute(key, value))});
    }
    static byte[] passwordBytes(char[] password) {
        return ("\"" + new String(password) + "\"").getBytes(StandardCharsets.UTF_16LE);
    }
    private void writePassword(String dn, char[] password) throws Exception {
        byte[] bytes = passwordBytes(password);
        try { replace(dn, "unicodePwd", bytes); }
        finally { Arrays.fill(bytes, (byte) 0); }
        try { replace(dn, "pwdLastSet", "0"); }
        catch (Exception e) { throw new NamingException("Password was set, but requiring a change at next sign-in failed. Inspect the account in ADUC."); }
    }
    @Override public synchronized void resetPassword(String username, char[] password) throws Exception {
        Validation.password(password); writePassword(find(username, "user"), password);
    }
    @Override public synchronized void unlock(String username) throws Exception { replace(find(username, "user"), "lockoutTime", "0"); }
    @Override public synchronized void setMembership(String username, String group, boolean member) throws Exception {
        String userDn = find(username, "user"), groupDn = find(group, "group");
        try {
            context.modifyAttributes(groupDn, new ModificationItem[]{new ModificationItem(
                member ? DirContext.ADD_ATTRIBUTE : DirContext.REMOVE_ATTRIBUTE, new BasicAttribute("member", userDn))});
        } catch (AttributeInUseException e) { if (!member) throw e; }
        catch (NoSuchAttributeException e) { if (member) throw e; }
    }
    @Override public synchronized void close() throws NamingException { context.close(); }
}
