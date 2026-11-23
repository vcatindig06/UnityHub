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
12. In Song Library, confirm the song title/chart area is one navigation action and favorite/edit/delete controls are separate actions.
13. In Service Planning, confirm each service plan card is announced as a button and can be activated without relying on color or icon-only cues.
14. In Account Settings, confirm the Automatically download updates label and switch state are announced together as one control and the full row can be activated.
15. In Song Chart, confirm the song title and Performance settings are headings; confirm A− and A+ announce decrease/increase chart text size.
16. In Live Stage, confirm LIVE STAGE is announced as a heading and all text-size, chord-visibility, auto-scroll, transpose, and navigation controls have understandable labels.

## Font scaling and narrow layouts

1. Test the smallest supported phone width.
2. Set Android display/font size to at least 1.3x, then repeat at 2.0x where the device permits.
3. Check long member names, rehearsal titles, notification titles/messages, organization names, song/service titles, locations, and translated-length labels.
4. Confirm text wraps, controls remain reachable, and no essential content is clipped or hidden behind the keyboard or system navigation.
5. Confirm the screen can scroll to every action and dialog button.
6. In Notifications, confirm All/Unread filters and Mark all read do not compete for the same horizontal space.
7. In Offline workspace, confirm the organization title and both account actions remain visible and usable at the smallest width.
8. In Live Stage, confirm text-size controls and chord visibility occupy separate rows and remain reachable without horizontal scrolling.
9. In Account Settings and Song Chart, confirm section titles, metadata, and adjustment controls wrap without overlapping at large font sizes.

## Touch and status feedback

- Confirm primary controls and row actions have at least a 48 dp touch target where practical.
- Confirm status is communicated with text or an icon as well as color.
- Confirm loading, empty, error, and success states remain understandable with TalkBack enabled.

## Validation record

After each major UI release, record the physical device model, Android version, display/font scale, tested screens, and any follow-up issues in the release checklist. Do not mark the physical-device checks complete based only on a successful CI build.
