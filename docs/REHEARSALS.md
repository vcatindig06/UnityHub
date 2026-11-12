# Rehearsal Management

UnityHub rehearsals coordinate practice sessions separately from Sunday service plans while optionally linking each rehearsal to a service.

## Schedule and invite a team

1. Open **Services → Rehearsals**.
2. Select **Schedule rehearsal**.
3. Enter a title, date (YYYY-MM-DD), 24-hour start time, optional end time, location, and rehearsal notes.
4. Optionally link the rehearsal to an existing service plan.
5. Select the team members to invite and save.

Each selected member receives an in-app notification and, when their Android push notifications are enabled, a push invitation. The invitation opens the matching rehearsal in UnityHub.

Only administrators and worship leaders can schedule, edit, cancel, complete, or delete rehearsals and manage the invited roster.

## Member availability

Invited members open the rehearsal and select **Available** or **Can't attend**. A member can only update the response attached to their own organization-membership record. When a response changes, administrators and worship leaders receive a notification.

Members who were not invited can view the rehearsal schedule and the invited roster, but cannot respond on behalf of somebody else.

## Attendance

When a leader marks a rehearsal **Complete**, the team list exposes the **Attended** and **Absent** actions. These are leader-only attendance outcomes and are separate from the member's earlier availability response.

Rehearsals can also be cancelled. Completed and cancelled rehearsals no longer accept member availability responses.

## Data and security

The migration supabase/migrations/20261112022019_create_rehearsal_management.sql creates:

- public.rehearsals: organization-scoped rehearsal details and an optional service-plan reference.
- public.rehearsal_assignments: the invited team members, each member's availability, and the leader-recorded attendance outcome.
- Notification columns and database triggers for invitation and availability-response messages.

Both tables have row-level security enabled. Organization members can read their team's rehearsal schedule. Only administrators and worship leaders can create and manage rehearsal records, invite members, and record attendance. A member can update only their own availability. Composite foreign keys prevent an assignment from referencing a member or rehearsal in a different organization. The optional service link is also checked against the active organization.

The new public tables use explicit authenticated Data API grants; no anonymous or service-role credential is added to the mobile app. Invitation and response alerts use the existing server-side signed notification webhook.
