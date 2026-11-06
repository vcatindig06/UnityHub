# UnityHub mobile UI/UX checklist

This checklist records the app-wide design baseline introduced in UnityHub 0.21.0. It is intended to keep new screens consistent instead of treating each screen as a separate visual system.

## Implemented in 0.21.0

- [x] **Brand consistency:** retain UnityHub's blue, emerald, and crimson identity with coordinated light and dark palettes.
- [x] **Light and dark appearance:** follow the Android system appearance automatically; the Account settings screen explains this behavior.
- [x] **System chrome:** status-bar and navigation-bar colors and icon contrast follow the active app theme.
- [x] **Readable type hierarchy:** apply consistent headline, title, body, and label weights; use scalable typography and comfortable body line heights.
- [x] **Consistent control shapes:** establish shared Material 3 shape tokens for fields, menus, dialogs, and controls that use theme defaults.
- [x] **Navigation clarity:** use a surface-backed bottom bar, persistent text labels, a clear selected indicator, and non-duplicative icon semantics.
- [x] **Text contrast regression checks:** verify normal text color pairs in both palettes against the WCAG AA 4.5:1 contrast threshold.
- [x] **State feedback baseline:** preserve visible loading, empty, offline, and retry/error states already implemented across the dashboard, songs, services, notifications, and team workflows.
- [x] **Form feedback baseline:** preserve Material 3 labels, supporting text, invalid states, disabled submit states, and progress feedback in existing forms.

## Required for every new or substantially revised screen

- [ ] Keep the primary task and next action visually obvious.
- [ ] Use a consistent spacing rhythm; group related controls and separate unrelated sections.
- [ ] Keep all interactive targets comfortably tappable (at least 48 dp where practical).
- [ ] Give icon-only actions a meaningful content description or tooltip; do not duplicate a visible label in TalkBack.
- [ ] Never use color alone to communicate status; pair color with text or an icon.
- [ ] Support scrolling and Android's font scaling without clipping essential content.
- [ ] Provide loading, empty, failure, success, and offline feedback appropriate to the screen.
- [ ] Validate input before submission and retain form values when an action fails.
- [ ] Keep destructive actions separate and confirm irreversible operations.
- [ ] Check keyboard/IME behavior so primary form actions remain reachable.
- [ ] Avoid unnecessary animation; animations must not block input or essential information.

## Release validation

- [x] Automated unit tests and the debug APK build run in GitHub Actions.
- [ ] Perform a manual TalkBack pass.
- [ ] Check the smallest supported phone width and larger font scale.
- [ ] Verify light and dark appearance on a physical Android device.
- [ ] Verify the keyboard and navigation gestures on a physical Android device.

The unchecked release checks require physical-device or accessibility-service testing; a successful CI build does not replace them.
