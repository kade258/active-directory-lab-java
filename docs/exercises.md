# Guided exercises

Start with the Java demo. In real AD mode, stay inside the dedicated lab OU and independently confirm changes in ADUC.

1. **Create an OU.** Add `Helpdesk`, then try `HELPDESK` again. Expect one OU and a duplicate error. In real mode, the OU is a direct child of the configured lab root.
2. **Create a group and user.** Create `GG_Helpdesk`, then `sam.rivera` / `Sam Rivera` inside Helpdesk. Choose a unique password with at least 12 characters, uppercase, lowercase, a number and a symbol. Expect the user to require a password change. The demo records this state but has no Windows sign-in service.
3. **Manage permissions through membership.** Add Sam to the group, repeat the add, then remove Sam. Expect one member after adding and none after removal. In real mode, resource permissions must separately reference the group in Windows ACLs. Sign out/in to refresh a Windows user's access token after membership changes.
4. **Reset a password.** Open **Manage** and try a short password, then a compliant unique one. Expect invalid input to fail, a valid password to replace the old credential, and a forced-change flag. Domain policy may reject passwords accepted by the app's minimum rule.
5. **Unlock an account.** Taylor begins locked in demo mode. Reset Taylor's password: the lock remains. Unlock Taylor: status becomes Active. Use **Simulate lock** to repeat. In real mode, use a disposable account locked under your domain policy; simulation is disabled.
6. **Review activity and persistence.** Check the feed, then restart using the same data folder. Expect your objects, account flags and activity to remain. Try creating a duplicate OU and find its FAILED outcome. Export the CSV and mode-labeled Markdown results.

The dashboard displays the latest 200 saved events, including starts, success, failure, partial and rejected outcomes. The full CSV exports all recorded events. Use [help desk tickets](scenarios.md) for transfers and offboarding, [storage documentation](storage-and-audit.md) for journal limits, and [validation.md](validation.md) to record actual results.
