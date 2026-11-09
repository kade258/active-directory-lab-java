package lab;

import java.time.Instant;
import java.util.*;

final class Reports {
    private Reports() {}
    static String markdown(String mode, Directory.Snapshot snapshot, AuditLog audit) {
        StringBuilder out = new StringBuilder("# Directory Lab results\n\nGenerated: " + Instant.now() + "\n\nMode: **" + (mode.equals("demo") ? "DEMO — simulated directory" : "LDAP — connected directory") + "**\n\n");
        out.append("Java: ").append(System.getProperty("java.version")).append("\n\n");
        out.append("Objects observed: ").append(snapshot.users().size()).append(" users, ").append(snapshot.groups().size()).append(" groups, ").append(snapshot.ous().size()).append(" OUs.\n\n");
        out.append("These are application observations. Windows sign-in, SMB access, certificate trust and delegated-rights validation require separate evidence.\n\n");
        for (Map<String, Object> ticket : Scenarios.describe(snapshot, audit.all())) {
            out.append("## ").append(ticket.get("id")).append(" — ").append(ticket.get("title")).append("\n\nStatus: ").append(ticket.get("status")).append("\n\n");
            @SuppressWarnings("unchecked") List<Map<String, Object>> steps = (List<Map<String, Object>>) ticket.get("steps");
            for (Map<String, Object> step : steps) out.append(Boolean.TRUE.equals(step.get("complete")) ? "- [x] " : "- [ ] ").append(step.get("instruction")).append("\n");
            out.append("\n");
        }
        long successful = audit.all().stream().filter(e -> e.outcome().equals("SUCCEEDED")).count();
        long failed = audit.all().stream().filter(e -> Set.of("FAILED", "PARTIAL", "REJECTED").contains(e.outcome())).count();
        out.append("## Activity\n\n").append(successful).append(" successful operations; ").append(failed).append(" failed, partial or rejected operations.\n\nDownload activity.csv separately for event details. No passwords or password hashes are included.\n");
        return out.toString();
    }
}
