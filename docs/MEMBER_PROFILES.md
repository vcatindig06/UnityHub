# Member Profiles and Participation

## Open a member profile

Open **Team** and select **View profile** on a member card. The profile is read-only. Use the back arrow to return to the Team directory without losing the current search or ministry-position filter.

## Profile information

A profile shows the member's current organization access role, the date they joined the organization, and their assigned ministry positions. It also summarizes upcoming service assignments, confirmed or declined service responses, upcoming rehearsals, and completed rehearsal attendance.

The recent activity lists show up to eight service assignments and eight rehearsal invitations, newest first. Service activity includes the service title, date/time, assigned role, response status, and service status. Rehearsal activity includes the rehearsal title, date/time, location when available, and availability or attendance outcome.

## Data scope and privacy

Profile reads are scoped to the active organization and the selected organization-membership ID. The app reads existing organization_members, member_positions, ministry_positions, service_plan_assignments, service_plans, rehearsal_assignments, and rehearsals records.

No new table, database migration, Data API grant, RLS policy, or server-side function is introduced. Existing Supabase row-level security remains the authorization boundary. The profile does not fetch or display account email addresses, rehearsal notes, or service/rehearsal response notes.

The member profile is an activity summary, not an attendance certification or performance score. Counts reflect the latest records available from the server.
