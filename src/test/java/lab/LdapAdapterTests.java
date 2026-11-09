package lab;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.*;
import javax.naming.*;
import javax.naming.directory.*;
import javax.naming.ldap.LdapName;

/** Contract tests with an instrumented DirContext. These do not replace a real AD run. */
final class LdapAdapterTests {
    private static int checks;
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
    private static final String ROOT = "OU=Lab,DC=example,DC=test";
    static int run() throws Exception {
        Fake fake = new Fake();
        try (LdapDirectory ad = new LdapDirectory(fake.context(), ROOT, "example.test")) {
            ad.createOu("Helpdesk"); ad.createOu("HR"); ad.createGroup("GG_Helpdesk", "Helpdesk");
            String groupDn = "CN=GG_Helpdesk,OU=Helpdesk," + ROOT;
            check(fake.entries.get(groupDn).get("groupType").get().equals("-2147483646"), "Global security group flags");
            ad.createUser("sam.rivera", "Sam Rivera", "Helpdesk", "Fictional!Pass123".toCharArray());
            String userDn = "CN=sam.rivera,OU=Helpdesk," + ROOT;
            check(fake.entries.get(userDn).get("userAccountControl").get().equals("512"), "User enabled after password setup");
            check(new String((byte[])fake.entries.get(userDn).get("unicodePwd").get(), StandardCharsets.UTF_16LE).equals("\"Fictional!Pass123\""), "Adapter wrote quoted binary password");
            check(fake.modifications.indexOf("unicodePwd") < fake.modifications.indexOf("userAccountControl"), "Password precedes enable");
            ad.setMembership("sam.rivera", "GG_Helpdesk", true);
            check(fake.entries.get(groupDn).get("member").contains(userDn), "Membership write uses full user DN");
            ad.setMembership("sam.rivera", "GG_Helpdesk", false);
            check(fake.entries.get(groupDn).get("member") == null, "Membership removal");
            fake.entries.get(userDn).put("userAccountControl", "66048");
            ad.setEnabled("sam.rivera", false);
            check(fake.entries.get(userDn).get("userAccountControl").get().equals("66050"), "Disable preserves unrelated UAC flags");
            ad.setEnabled("sam.rivera", true);
            check(fake.entries.get(userDn).get("userAccountControl").get().equals("66048"), "Enable preserves unrelated UAC flags");
            ad.moveUser("sam.rivera", "HR"); userDn = "CN=sam.rivera,OU=HR," + ROOT;
            check(fake.entries.containsKey(userDn), "Move remains under configured root");
            int writes = fake.writeCount;
            fake.forcedResult = "CN=Administrator,CN=Users,DC=example,DC=test";
            try { ad.unlock("Administrator"); throw new AssertionError("Outside-root target accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
            check(fake.writeCount == writes, "Out-of-scope result rejected before any write");
            fake.forcedResult = null; fake.failure = new NoPermissionException("Sensitive server diagnostic must not be audited");
            try { ad.unlock("sam.rivera"); throw new AssertionError("Denied write accepted"); }
            catch (NoPermissionException expected) { checks++; }
            fake.failure = new CommunicationException("Lost connection");
            try { ad.unlock("sam.rivera"); throw new AssertionError("Connection loss hidden"); }
            catch (CommunicationException expected) { checks++; }
            fake.failure = null; fake.failAttribute = "pwdLastSet";
            try { ad.resetPassword("sam.rivera", "Another!Demo456".toCharArray()); throw new AssertionError("Partial password reset hidden"); }
            catch (PartialOperationException expected) { checks++; }
            check(new String((byte[])fake.entries.get(userDn).get("unicodePwd").get(), StandardCharsets.UTF_16LE).contains("Another!Demo456"), "Partial operation really changed the password stage");
            try { ad.createUser("partial.user", "Partial User", "Helpdesk", "Fictional!Pass123".toCharArray()); throw new AssertionError("Partial create hidden"); }
            catch (PartialOperationException expected) { checks++; }
            check(fake.entries.get("CN=partial.user,OU=Helpdesk," + ROOT).get("userAccountControl").get().equals("514"), "Failed setup leaves created user disabled");
            fake.failAttribute = null;
            try { ad.createGroup("GG_Helpdesk", "Helpdesk"); throw new AssertionError("Duplicate create accepted"); }
            catch (NameAlreadyBoundException expected) { checks++; }
        }
        return checks;
    }

    private static final class Fake {
        final Map<String, Attributes> entries = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        final List<String> modifications = new ArrayList<>();
        String forcedResult, failAttribute;
        NamingException failure;
        int writeCount;
        DirContext context() {
            return (DirContext)Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{DirContext.class}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "close": return null;
                    case "createSubcontext": {
                        String dn = args[0].toString();
                        if (entries.containsKey(dn)) throw new NameAlreadyBoundException();
                        entries.put(dn, (Attributes)((Attributes)args[1]).clone()); writeCount++; return proxy;
                    }
                    case "getAttributes": {
                        Attributes a = entries.get(args[0].toString());
                        if (a == null) throw new NameNotFoundException(); return a;
                    }
                    case "search": {
                        if (failure instanceof CommunicationException) throw failure;
                        String base = args[0].toString(); Object[] values = (Object[])args[2];
                        List<SearchResult> found = new ArrayList<>();
                        if (forcedResult != null) found.add(result(forcedResult, new BasicAttributes()));
                        else for (var e : entries.entrySet()) {
                            if (!new LdapName(e.getKey()).startsWith(new LdapName(base))) continue;
                            Attribute account = e.getValue().get("sAMAccountName");
                            if (values.length == 1 && account != null && String.valueOf(account.get()).equalsIgnoreCase(String.valueOf(values[0]))) found.add(result(e.getKey(), e.getValue()));
                        }
                        Iterator<SearchResult> iterator = found.iterator();
                        return new NamingEnumeration<SearchResult>() {
                            public boolean hasMore() { return iterator.hasNext(); }
                            public SearchResult next() { return iterator.next(); }
                            public boolean hasMoreElements() { return hasMore(); }
                            public SearchResult nextElement() { return next(); }
                            public void close() {}
                        };
                    }
                    case "modifyAttributes": {
                        if (failure != null) throw failure;
                        Attributes a = entries.get(args[0].toString()); if (a == null) throw new NameNotFoundException();
                        for (ModificationItem item : (ModificationItem[])args[1]) {
                            String key = item.getAttribute().getID(); if (key.equals(failAttribute)) throw new NoPermissionException();
                            Object value = item.getAttribute().get(); if (value instanceof byte[] bytes) value = bytes.clone();
                            if (item.getModificationOp() == DirContext.REMOVE_ATTRIBUTE) a.remove(key);
                            else a.put(key, value);
                            writeCount++; modifications.add(key);
                        }
                        return null;
                    }
                    case "rename": {
                        Attributes a = entries.remove(args[0].toString()); if (a == null) throw new NameNotFoundException();
                        entries.put(args[1].toString(), a); writeCount++; return null;
                    }
                    default: throw new UnsupportedOperationException(method.getName());
                }
            });
        }
        private static SearchResult result(String dn, Attributes attributes) {
            SearchResult result = new SearchResult(dn, null, attributes); result.setNameInNamespace(dn); return result;
        }
    }
}
