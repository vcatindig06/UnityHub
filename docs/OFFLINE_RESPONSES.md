# Offline service responses

UnityHub 0.28.0 adds a deliberately narrow first step toward offline-first interactions: a team member can confirm or decline their own service assignment even when the device has no validated internet connection.

## Member workflow

1. The dashboard displays the member's upcoming assignments from the server or the last user-scoped offline snapshot.
2. When the member chooses Confirm or Can't serve offline, UnityHub stores that selected response in the local Room database.
3. The assignment card changes to **Saved · waiting to sync**. This describes a local save, not a server-confirmed response.
4. UnityHub schedules the existing network-constrained WorkManager sync. When connectivity returns, the worker submits the queued response before downloading fresh assignment snapshots.
5. After Supabase returns the updated assignment, UnityHub removes the queue entry and refreshes the offline snapshot.

If the member changes their choice again before synchronization, the local queue replaces the earlier choice. The most recent response is the one submitted.

## Authorization and data scope

Each queued record stores the signed-in user ID, organization ID, assignment ID, member-membership ID, response, and queue timestamp. Before submission, the worker checks that the active Supabase user is the original user, that the requested organization still matches the active membership, and that the assignment's member ID matches that membership. The update also filters by assignment ID, organization ID, and member ID.

The existing Supabase Row Level Security policies remain the authorization boundary. Local queue contents or hidden UI controls do not grant permission to change another member's assignment. An update that affects no row is treated as stale and discarded; the sync status card reports that the assignment or team access changed. If the account no longer belongs to the organization, unsent responses for that organization are discarded.

## Sign-out behavior

Signing out deletes the current user's unsent response queue along with the existing user-scoped offline copies. The Account screen explicitly warns about this before the user confirms sign-out. Another account on the same device cannot submit the previous user's queue.

## Scope and limitations

This release queues only member responses (confirmed / declined) to service assignments. It does not enable offline song editing, service-plan editing, assignment creation/removal, rehearsal changes, attendance recording, member administration, or other writes. Those actions continue to require an online request.

A queued response is not visible to other team members or leaders until the server accepts it. Users should treat the waiting-to-sync label as local confirmation only. WorkManager requires a network connection and retries temporary upload or download failures. If a saved assignment was deleted or the member's access changed before synchronization, the stale response is discarded rather than replayed later.
