# UnityHub accessibility validation

Use this checklist on a physical Android device before release. Automated compilation and unit tests do not replace these checks.

## TalkBack

1. Enable TalkBack in Android Accessibility settings.
2. Open Rehearsals, open a rehearsal editor, and move focus to **Invite team members**.
3. Confirm TalkBack announces it as a button and reports **Collapsed**.
4. Activate it and confirm the state changes to **Expanded**.
5. Move through the member list and confirm each member's name and selection state are announced once, without duplicate nested click targets.
6. Confirm icon-only controls elsewhere announce an action rather than an icon name.
7. Open Notifications and confirm the page title and each notification title are announced as headings.
8. Move through the All/Unread filters and Mark all read action; confirm each control is announced once with its visible label and selected/disabled state.
9. Open the Offline workspace and confirm Try online and Sign out remain separately reachable after the offline explanation.
10. On Home, confirm the Next rehearsal and upcoming service cards are announced as buttons. On an assignment card, confirm View service, Confirm, and Can't serve/Edit note are separate actions, not nested inside a card-level button.
11. Open Assignment overview and confirm the page title and each assignment service title are announced as headings; confirm each assignment card is announced as one button.

## Font scaling and narrow layouts

1. Test the smallest supported phone width.
2. Set Android display/font size to at least 1.3x, then repeat at 2.0x where the device permits.
3. Check long member names, rehearsal titles, notification titles/messages, organization names, service titles, locations, and translated-length labels.
4. Confirm text wraps, controls remain reachable, and no essential content is clipped or hidden behind the keyboard or system navigation.
5. Confirm the screen can scroll to every action and dialog button.
6. In Notifications, confirm All/Unread filters and Mark all read do not compete for the same horizontal space.
7. In Offline workspace, confirm the organization title and both account actions remain visible and usable at the smallest width.

## Touch and status feedback

- Confirm primary controls and row actions have at least a 48 dp touch target where practical.
- Confirm status is communicated with text or an icon as well as color.
- Confirm loading, empty, error, and success states remain understandable with TalkBack enabled.
