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

## Roster insights

The rehearsal detail roster summarizes invitee responses. Scheduled rehearsals show Available, Can't attend, and Awaiting counts. Once a rehearsal is completed, the summary changes to Attended, Absent, and Not recorded. Use the roster search and status filters to find individual members without changing the saved invitation list.

## Member availability

Invited members open the rehearsal and select **Available** or **Can't attend**. A member can only update the response attached to their own organization-membership record. When a response changes, administrators and worship leaders receive a notification.

UnityHub saves the last synced rehearsal schedule and invited rosters on the device. When offline, an invited member can submit an availability choice from a previously synced rehearsal. The response is labelled **Saved on this device · waiting to sync** and is visible only on that device until the server accepts it. The next network-constrained background sync uploads the choice before refreshing the rehearsal data. The most recent unsent choice replaces the previous one.

Offline responses are available only for cached invitations. If the rehearsal has been completed or cancelled, the member's organization membership has changed, or the invitation no longer exists when the queued response is uploaded, Supabase rejects the update and UnityHub discards the stale response. The app does not treat a local save as a server-confirmed response.

Members who were not invited can view the rehearsal schedule and the invited roster, but cannot respond on behalf of somebody else.

## Calendar integration

Open a rehearsal and select **Add to calendar** to hand the event to the device's calendar app. UnityHub pre-fills the rehearsal title, date, start/end time, location, and notes. The calendar app opens its normal event editor so the user can review and save the event. If no end time was set, UnityHub suggests a one-hour duration.

This uses Android's calendar insert intent and does not read the user's calendar or request calendar-provider permissions. See the Android Developers guide for [adding a calendar event](https://developer.android.com/guide/components/intents-common.html#AddEvent).

## Local reminders

Select **Set reminder** in rehearsal details to schedule a reminder on this device. Available choices are 1 day, 1 hour, or 15 minutes before the rehearsal starts. WorkManager delivers the local notification; Android may delay it under battery and background-execution restrictions, so it is not an exact alarm.

Reminders are private to the device and do not send a message to other invited members. The reminder opens the matching rehearsal in UnityHub. Updating the rehearsal date or start time reschedules an existing reminder. Completing, cancelling, or deleting the rehearsal removes it, and signing out clears all reminders belonging to that account. Android notification permission and the **Rehearsal reminders** notification channel must be enabled for the notification to appear.

## Availability overview

The roster summary displays the number of Available, Can't attend, and Awaiting responses, along with a visual breakdown and availability response rate. After completion, it changes to Attended, Absent, and Not recorded and shows the attendance recording rate.

## Attendance

When a leader marks a rehearsal **Complete**, the team list exposes the **Attended** and **Absent** actions. These are leader-only attendance outcomes and are separate from the member's earlier availability response.

Rehearsals can also be cancelled. Completed and cancelled rehearsals no longer accept member availability responses. Scheduling, editing, cancelling, completing, roster changes, and attendance recording require an online request.

## Data and security

The migration supabase/migrations/20261112022019_create_rehearsal_management.sql creates:

- public.rehearsals: organization-scoped rehearsal details and an optional service-plan reference.
- public.rehearsal_assignments: the invited team members, each member's availability, and the leader-recorded attendance outcome.
- Notification columns and database triggers for invitation and availability-response messages.

Both tables have row-level security enabled. Organization members can read their team's rehearsal schedule. Only administrators and worship leaders can create and manage rehearsal records, invite members, and record attendance. A member can update only their own availability, and only while the rehearsal remains scheduled. Composite foreign keys prevent an assignment from referencing a member or rehearsal in a different organization. The optional service link is also checked against the active organization.

The new public tables use explicit authenticated Data API grants; no anonymous or service-role credential is added to the mobile app. Invitation and response alerts use the existing server-side signed notification webhook. Offline queue entries do not bypass these policies; the server remains authoritative when each response is submitted.
