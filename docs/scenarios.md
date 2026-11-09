# Help desk ticket walkthroughs

The dashboard evaluates four tickets from current directory state and saved application history. A ticket can reopen when its required state changes. Its checks are learning progress, not proof of Windows logon or SMB access.

| Ticket | Request | Actions and expected evidence |
| --- | --- | --- |
| HD-101 | Onboard Sam Rivera | Create Helpdesk, GG_Helpdesk, enabled sam.rivera with password change required; add membership. All four checks should be satisfied. |
| HD-102 | Recover Taylor's locked account | Reset taylor.reed, then unlock separately. Saved success events and current enabled/unlocked/forced-change state are required. |
| HD-103 | Transfer Jordan to HR | Move jordan.lee to HR, add GG_HR, remove GG_IT. Review the OU and both memberships. |
| HD-104 | Offboard Casey | Disable casey.brooks and remove GG_Finance. Confirm disabled state and removed assignment. |

Use **Manage** beside a user for password reset, unlock, move and account status. Use **Groups & permissions** for membership. Each action records a start and an outcome. Do not treat an unresolved STARTED or PARTIAL event as an invitation to retry; inspect the object first.

The demo begins with IT, HR and Finance. In a real domain, run `Initialize-ScenarioLab.ps1` after provisioning sample users to prepare the department OUs without changing existing passwords or memberships. Its Helpdesk OU/group preparation will satisfy those first onboarding checks; this does not claim you created them through Java.

Disabling an AD account blocks new account authentication. Existing sessions/tickets and all other access paths require separate review. This small offboarding scenario is not a complete enterprise deprovisioning workflow.

Export **Results & evidence → Download results** and **Activity → Export CSV**. Keep mode labels visible and add independent Windows screenshots when using a real domain.
