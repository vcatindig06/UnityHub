# Offline member responses

UnityHub supports two narrowly scoped offline-first member actions: confirming or declining an assigned service role, and marking availability for an invited rehearsal. These are member responses only; they do not grant offline editing or administration access.

## Service assignment responses

1. The Home dashboard displays upcoming assignments from Supabase or the user's last saved offline snapshot.
2. When a member chooses **Confirm** or **Can't serve** while offline, UnityHub stores that response in the local Room database. A decline may include an explanation of up to 300 characters.
3. The assignment card shows **Saved · waiting to sync**. This is a local save, not a server-confirmed response.
4. WorkManager submits queued service responses before downloading fresh offline snapshots.
5. The queue entry is removed after Supabase confirms the update. A leader can then see the response and any decline note.

## Rehearsal availability

1. Rehearsal schedules and invited rosters are saved in the signed-in user's organization-scoped Room cache when they are loaded online or refreshed by background sync.
2. An invited member can open the last synced rehearsal details and choose **Available** or **Can't attend** when there is no validated internet connection.
3. UnityHub stores the choice locally and overlays it on the cached roster. The member sees **Saved on this device · waiting to sync**; other members and leaders do not see it until Supabase accepts it.
4. WorkManager submits the response when a network connection is available, before refreshing rehearsal snapshots. Choosing another response before sync replaces the earlier queued choice for that invitation.
5. If the invitation was deleted, the member no longer belongs to the organization, or the rehearsal was completed/cancelled before sync, the existing database policy rejects the update. UnityHub discards that stale queue entry and reports that a saved response could not be sent.

Offline rehearsal availability is limited to invitations and rehearsal details that were previously synced to this device. A response note is not collected in this offline flow. Scheduling, editing, cancelling, completing, inviting members, changing the roster, and recording attendance remain online-only.

## Authorization and data scope

Every queued response stores its owner user ID, organization ID, assignment ID, member-membership ID, response value, and queue timestamp. Rehearsal responses also store the rehearsal ID.

Before an upload, the worker confirms that the active Supabase account is the queue owner, that the active membership is still in the requested organization, and that the stored membership ID matches the live membership. The rehearsal update is filtered by assignment ID, rehearsal ID, organization ID, and membership ID.

Supabase Row Level Security remains the authorization boundary. Its rehearsal self-response policy requires the assignment to belong to the authenticated member in the same organization, the rehearsal to remain scheduled, and the new response to be Available or Can't attend. Local queue contents and hidden UI controls do not grant permission to update another member's response. A zero-row update is treated as stale and is discarded, not retried indefinitely.

## Local database and sign-out

Room database version 6 adds user-scoped rehearsal and invitation snapshots plus a separate pending rehearsal availability queue. The migration is additive and preserves all existing cached songs, services, assignments, and pending service responses.

Signing out clears the current user's queued service and rehearsal responses, cached rehearsal details and rosters, and the existing offline copies. Another account on the same device cannot submit the previous user's queue.

## Sync behavior and limitations

WorkManager requires a validated network connection and retries temporary upload or download failures. The worker submits queued service and rehearsal responses before refreshing songs, service plans, setlists, assignments, rehearsals, and rehearsal rosters.

Offline response status is local until the server accepts it. If access or the underlying assignment changes before synchronization, UnityHub discards the stale response rather than replaying it later. Offline song editing, service-plan editing, assignment creation/removal, rehearsal management, attendance recording, member administration, and other writes are not enabled.
