# Ministry Position Management

UnityHub 0.29.0 lets administrators and worship leaders manage the reusable ministry-position catalogue from the Team screen.

## Create and rename

- Position names must contain 2–60 characters after trimming.
- Names are checked case-insensitively so the same role cannot be added under a different capitalization.
- Renaming an existing position updates the name shown for that position throughout the team directory and member profile views. The position ID remains unchanged, so member assignments stay connected.
- The selected ministry-position filter follows the new name after a rename.

## Delete

Before deletion, the Team screen displays the number of member profiles currently assigned to that position. The confirmation explains that those profile assignments will also be removed. The existing database foreign key cascades the delete from `ministry_positions` to the corresponding `member_positions` rows.

Deleting a ministry position does not delete service assignments. Service assignment role labels are stored as their own text values, so prior service records retain their saved role text.

## Authorization

Only administrators and worship leaders see the catalogue management controls. The Android client still relies on Supabase Row Level Security for authorization: the existing `ministry_positions_admin_manage` policy allows changes only for those roles within the active organization. Authenticated organization members retain read access; anonymous users have no table access. No database policy or grant expansion is introduced by this feature.