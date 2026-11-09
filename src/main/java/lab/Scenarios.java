package lab;

import java.util.*;

/** Current-state checks, not self-attested completion buttons or claims of Windows proof. */
final class Scenarios {
    private Scenarios() {}
    private static Map<String, Object> step(String instruction, boolean complete) {
        return Map.of("instruction", instruction, "complete", complete);
    }
    private static Map<String, Object> ticket(String id, String title, String priority, String request, List<Map<String, Object>> steps) {
        long done = steps.stream().filter(s -> Boolean.TRUE.equals(s.get("complete"))).count();
        return Map.of("id", id, "title", title, "priority", priority, "request", request, "steps", steps,
            "completed", done, "total", steps.size(), "status", done == steps.size() ? "Checks satisfied" : "Open");
    }
    private static boolean member(Directory.Snapshot s, String group, String user) {
        return s.groups().stream().anyMatch(g -> g.name().equalsIgnoreCase(group) && g.members().stream().anyMatch(m -> m.equalsIgnoreCase(user)));
    }
    private static Directory.User user(Directory.Snapshot s, String username) {
        return s.users().stream().filter(u -> u.username().equalsIgnoreCase(username)).findFirst().orElse(null);
    }
    static List<Map<String, Object>> describe(Directory.Snapshot s, List<AuditLog.Event> events) {
        Directory.User sam = user(s, "sam.rivera"), taylor = user(s, "taylor.reed"), jordan = user(s, "jordan.lee"), casey = user(s, "casey.brooks");
        boolean resetTaylor = events.stream().anyMatch(e -> e.action().equals("reset-password") && e.target().equalsIgnoreCase("taylor.reed") && e.outcome().equals("SUCCEEDED"));
        boolean unlockedTaylor = events.stream().anyMatch(e -> e.action().equals("unlock") && e.target().equalsIgnoreCase("taylor.reed") && e.outcome().equals("SUCCEEDED"));
        return List.of(
            ticket("HD-101", "Onboard a new employee", "Normal", "Sam Rivera joins the Helpdesk team. Create the identity and assign the team's access.", List.of(
                step("Create a Helpdesk organizational unit.", s.ous().stream().anyMatch(o -> o.equalsIgnoreCase("Helpdesk"))),
                step("Create GG_Helpdesk in Helpdesk.", s.groups().stream().anyMatch(g -> g.name().equalsIgnoreCase("GG_Helpdesk") && g.ou().equalsIgnoreCase("Helpdesk"))),
                step("Create enabled user sam.rivera in Helpdesk and require a password change.", sam != null && sam.enabled() && sam.ou().equalsIgnoreCase("Helpdesk") && sam.mustChangePassword()),
                step("Add sam.rivera to GG_Helpdesk.", member(s, "GG_Helpdesk", "sam.rivera")))),
            ticket("HD-102", "Recover a locked account", "High", "Taylor Reed cannot sign in. Reset the password and unlock the account as separate operations.", List.of(
                step("Reset taylor.reed's password through the app.", resetTaylor),
                step("Unlock taylor.reed through the app and confirm the account is unlocked.", unlockedTaylor && taylor != null && !taylor.locked()),
                step("Keep the account enabled with password change required.", taylor != null && taylor.enabled() && taylor.mustChangePassword()))),
            ticket("HD-103", "Transfer an employee to HR", "Normal", "Jordan Lee moves from IT to HR. Update the directory location and remove old access.", List.of(
                step("Create an HR organizational unit if it does not exist.", s.ous().stream().anyMatch(o -> o.equalsIgnoreCase("HR"))),
                step("Move jordan.lee to HR using Account tools.", jordan != null && jordan.ou().equalsIgnoreCase("HR")),
                step("Add jordan.lee to GG_HR.", member(s, "GG_HR", "jordan.lee")),
                step("Remove jordan.lee from GG_IT.", jordan != null && !member(s, "GG_IT", "jordan.lee")))),
            ticket("HD-104", "Offboard a departing employee", "High", "Casey Brooks has left. Disable the account and remove the Finance group assignment.", List.of(
                step("Disable casey.brooks using Account tools.", casey != null && !casey.enabled()),
                step("Remove casey.brooks from GG_Finance.", casey != null && !member(s, "GG_Finance", "casey.brooks")))))
        ;
    }
}
