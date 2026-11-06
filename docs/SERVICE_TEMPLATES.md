# Service templates

Service templates let a team reuse a proven order of worship without rebuilding it for every gathering.

## Workflow

1. Open a service with a setlist.
2. Choose **Save current setlist as a template**.
3. Enter a team-recognizable name and optional description.
4. Open **Templates** from the Services screen to review, rename, or delete saved templates.
5. Open another draft or published service, choose **Apply a saved setlist template**, and confirm the action.

Applying a template appends its items after the target service's current items. It never replaces or deletes the existing setlist.

## Preserved information

- Item order
- Song references while the source song exists
- Saved ChordPro snapshots (the chart content as it appeared when added)

If a source song is deleted from the library, its template item keeps the saved ChordPro snapshot and its live song reference is set to null. This prevents a reusable template from blocking song deletion or losing the saved chart.
- Custom prayer, reading, announcement, and other items
- Duration and notes

## Permissions and data protection

- Any authenticated member of an organization can read that organization's templates.
- Only administrators and worship leaders can create, rename, delete, or apply templates.
- Both tables have row-level security enabled.
- Template items must reference a template and, when a song ID is present, a song from the same organization.
- Public/anonymous access is revoked. Only the required authenticated Data API privileges are granted.

Templates are not available in offline/read-only mode. Applying one requires an active connection.
