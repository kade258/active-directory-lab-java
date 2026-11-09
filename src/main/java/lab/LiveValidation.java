package lab;

import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;

/** Explicit, terminal-only live integration runner. Never run automatically in public CI. */
public final class LiveValidation {
    private LiveValidation() {}
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3 || (args.length == 3 && !args[2].equals("--exercise")))
            throw new IllegalArgumentException("Usage: java -cp app.jar lab.LiveValidation config/ldap.properties reports/live-ad.md [--exercise]");
        Console console = System.console();
        if (console == null) throw new IllegalArgumentException("Use an interactive terminal for the masked bind-password prompt.");
        Properties config = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of(args[0]))) { config.load(reader); }
        char[] secret = console.readPassword("Lab operator bind password: ");
        if (secret == null || secret.length == 0) throw new IllegalArgumentException("A password is required.");
        StringBuilder report = new StringBuilder("# Live Active Directory validation\n\nDate: " + Instant.now() + "\n\n");
        Exception failure = null;
        try (LdapDirectory directory = new LdapDirectory(config, secret)) {
            report.append("- PASS: LDAPS bind and configured root OU lookup (normal JDK certificate validation).\n");
            Directory.Snapshot snapshot = directory.snapshot();
            report.append("- PASS: Read directory: ").append(snapshot.users().size()).append(" users, ").append(snapshot.groups().size()).append(" groups.\n");
            if (args.length == 3) {
                String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
                String unit = "Verify-" + suffix, moved = "Moved-" + suffix, user = "verify." + suffix, group = "GG_Verify_" + suffix;
                report.append("\nDisposable objects are retained for inspection in ").append(unit).append(" and ").append(moved).append(".\n\n");
                directory.createOu(unit); directory.createOu(moved);
                report.append("- PASS: Create dedicated validation OUs.\n");
                directory.createGroup(group, unit);
                report.append("- PASS: Create global security group.\n");
                char[] initial = ("Verify!A1" + UUID.randomUUID()).toCharArray();
                char[] replacement = ("Reset!B2" + UUID.randomUUID()).toCharArray();
                try {
                    directory.createUser(user, "Disposable validation user", unit, initial);
                    report.append("- PASS: Create, set password, require password change, and enable user.\n");
                    directory.resetPassword(user, replacement);
                    report.append("- PASS: Reset password and require change (independent Windows sign-in still required).\n");
                } finally { Arrays.fill(initial, '\0'); Arrays.fill(replacement, '\0'); }
                directory.unlock(user); directory.setMembership(user, group, true);
                if (directory.snapshot().groups().stream().noneMatch(g -> g.name().equals(group) && g.members().contains(user))) throw new IllegalStateException("Membership read-back failed.");
                report.append("- PASS: Unlock operation accepted and added membership verified by read-back. This user was not deliberately locked.\n");
                directory.setMembership(user, group, false); directory.moveUser(user, moved); directory.setEnabled(user, false);
                Directory.Snapshot after = directory.snapshot();
                if (after.users().stream().noneMatch(u -> u.username().equals(user) && u.ou().equals(moved) && !u.enabled())) throw new IllegalStateException("Move/disable read-back failed.");
                if (after.groups().stream().anyMatch(g -> g.name().equals(group) && g.members().contains(user))) throw new IllegalStateException("Membership removal read-back failed.");
                report.append("- PASS: Remove membership, move user and disable; verified by read-back.\n");
            } else report.append("\nRead-only checks only. Add --exercise to create disposable lab objects and test write operations.\n");
        } catch (Exception e) {
            failure = e; report.append("\n- FAIL/INCOMPLETE: ").append(e.getClass().getSimpleName()).append(". Inspect ADUC for partial objects; do not retry blindly.\n");
        } finally { Arrays.fill(secret, '\0'); }
        report.append("\nNOT VERIFIED by this runner: Windows sign-in/password change, a genuine lockout recovery, SMB allow/deny behavior, and effective rights outside the root. Complete the Windows evidence checks separately.\n");
        Path output = Path.of(args[1]).toAbsolutePath(); LocalFiles.privateDirectory(output.getParent());
        Files.writeString(output, report); LocalFiles.privateFile(output);
        System.out.println("Saved " + output);
        if (failure != null) throw new IllegalStateException("Live validation did not finish. Review the local report and AD objects.", failure);
    }
}
