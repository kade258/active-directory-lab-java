package lab;

import java.util.List;

/** Operations shared by the local simulator and the real AD adapter. */
public interface Directory extends AutoCloseable {
    record User(String username, String displayName, String ou, boolean enabled,
                boolean locked, boolean mustChangePassword) {}
    record Group(String name, String ou, List<String> members) {}
    record Snapshot(List<User> users, List<Group> groups, List<String> ous) {}

    String mode();
    Snapshot snapshot() throws Exception;
    void createOu(String name) throws Exception;
    void createGroup(String name, String ou) throws Exception;
    void createUser(String username, String displayName, String ou, char[] password) throws Exception;
    void resetPassword(String username, char[] password) throws Exception;
    void unlock(String username) throws Exception;
    void setMembership(String username, String group, boolean member) throws Exception;
    void moveUser(String username, String ou) throws Exception;
    void setEnabled(String username, boolean enabled) throws Exception;
    default void simulateLock(String username) throws Exception {
        throw new IllegalArgumentException("Account lock simulation is available only in demo mode.");
    }
    @Override default void close() throws Exception {}
}
