# UnityHub accessibility validation

Use this checklist on a physical Android device before release. Automated compilation and unit tests do not replace these checks.

## TalkBack

1. Enable TalkBack in Android Accessibility settings.
2. Open Rehearsals, open a rehearsal editor, and move focus to **Invite team members**.
3. Confirm TalkBack announces it as a button and reports **Collapsed**.
4. Activate it and confirm the state changes to **Expanded**.
5. Move through the member list and confirm each member's name and selection state are announced once, without duplicate nested click targets.
6. Confirm icon-only controls elsewhere announce an action rather than an icon name.

## Font scaling and narrow layouts

1. Test the smallest supported phone width.
2. Set Android display/font size to at least 1.3x, then repeat at 2.0x where the device permits.
3. Check long member names, rehearsal titles, locations, and translated-length labels.
4. Confirm text wraps, controls remain reachable, and no essential content is clipped or hidden behind the keyboard or system navigation.
5. Confirm the screen can scroll to every action and dialog button.

## Touch and status feedback

- Confirm primary controls and row actions have at least a 48 dp touch target where practical.
- Confirm status is communicated with text or an icon as well as color.
- Confirm loading, empty, error, and success states remain understandable with TalkBack enabled.
