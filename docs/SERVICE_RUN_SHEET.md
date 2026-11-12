# Service Run of Show

The Run of Show summary helps worship leaders estimate how long a service setlist will take.

## Set item durations

1. Open **Services** and select a service.
2. Find a song, prayer, reading, announcement, or other setlist item.
3. Select **Set duration** or **Edit duration**.
4. Enter a duration from 0 to 600 minutes and save.
5. Leave the field blank to remove the duration.

Only administrators and worship leaders can edit durations. Completed and cancelled services remain read-only. Team members and viewers can see the summary without edit controls.

## Read the summary

- The total includes only durations that have been entered.
- The summary reports how many items still need a duration.
- An estimated finish time appears only when every setlist item has a duration and the service start time is set.
- Finish times that pass midnight include the number of days elapsed.
- The summary is calculated from the ordered setlist and does not change the service start time or saved items.

Duration values use the existing `service_plan_items.duration_minutes` field. No new table or migration is required. Values are included in the existing user-scoped offline service cache and remain preserved when setlists are copied or saved as templates.
