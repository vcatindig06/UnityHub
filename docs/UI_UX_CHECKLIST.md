# UnityHub mobile UI/UX checklist

This checklist records the app-wide design baseline introduced in UnityHub 0.21.0. It is intended to keep new screens consistent instead of treating each screen as a separate visual system.

## Implemented in 0.21.0

- [x] **Brand consistency:** use the butter-cream canvas, forest-green primary, terracotta emphasis, muted-gold details, scalable logo mark, and coordinated light/dark palettes.
- [x] **Light and dark appearance:** follow the Android system appearance automatically; the Account settings screen explains this behavior.
- [x] **System chrome:** status-bar and navigation-bar colors and icon contrast follow the active app theme.
- [x] **Readable type hierarchy:** apply consistent headline, title, body, and label weights; use scalable typography and comfortable body line heights.
- [x] **Consistent control shapes:** establish shared Material 3 shape tokens for fields, menus, dialogs, and controls that use theme defaults.
- [x] **Navigation clarity:** use a surface-backed bottom bar, persistent text labels, a clear selected indicator, and non-duplicative icon semantics.
- [x] **Text contrast regression checks:** verify normal text color pairs in both palettes against the WCAG AA 4.5:1 contrast threshold.
- [x] **State feedback baseline:** preserve visible loading, empty, offline, and retry/error states already implemented across the dashboard, songs, services, notifications, and team workflows.
- [x] **Form feedback baseline:** preserve Material 3 labels, supporting text, invalid states, disabled submit states, and progress feedback in existing forms.

## Visual restraint and product credibility

- Prefer clear hierarchy, alignment, and restrained surfaces over stacked cards, floating tiles, glass effects, or decorative gradients.
- Do not use emoji, unicode stars, fake ratings, avatar clusters, mock activity, testimonials, or placeholder dashboard artwork as decoration.
- Use Material vector icons for recognisable actions and navigation only; avoid repeated decorative icons in content rows.
- Remove helper copy when the heading, field label, button, or current state already explains the same thing.
- Avoid uppercase micro-labels, artificial slogans, redundant badges, and repeated calls to action.
- Keep empty states factual and brief. Do not add paragraphs that explain obvious next steps.
- Keep motion purposeful; avoid ambient animated backgrounds and effects that do not communicate state.

## Required for every new or substantially revised screen

- [ ] Keep the primary task and next action visually obvious.
- [ ] Use a consistent spacing rhythm; group related controls and separate unrelated sections.
- [ ] Keep all interactive targets comfortably tappable (at least 48 dp where practical).
- [ ] Give icon-only actions a meaningful content description or tooltip; do not duplicate a visible label in TalkBack.
- [ ] Never use color alone to communicate status; pair color with text or an icon.
- [ ] Support scrolling and Android's font scaling without clipping essential content.
- [ ] Keep expandable rows and row-level selection controls at least 48 dp high, expose their role and expanded/selected state to TalkBack, and avoid duplicate nested click semantics.
- [ ] Check narrow-width layouts with long names, translated labels, and 1.3x–2.0x font scale; allow text to wrap rather than forcing fixed-height rows.
- [ ] Provide loading, empty, failure, success, and offline feedback appropriate to the screen.
- [ ] Validate input before submission and retain form values when an action fails.
- [ ] Keep destructive actions separate and confirm irreversible operations.
- [ ] Check keyboard/IME behavior so primary form actions remain reachable.
- [ ] Avoid decorative animation, excessive shadows, gradients, and corner radii; use them only when they support hierarchy or feedback.

## Navigation and refresh

- [x] Keep four core bottom destinations: Home, Songs, Services, and Community.
- [x] Emphasize the center Song / Setlist action and change its action based on the selected destination.
- [x] Keep app-bar icon and Back actions at 48 dp and ellipsize screen titles on narrow layouts.
- [x] Allow long Team member names to wrap and expose member cards as accessible buttons.
- [x] Expose Community shortcut cards as accessible buttons for Team, Chat, Shared Files, and Profile.
- [x] Keep Team member administrative actions at a minimum 48 dp touch height.
- [ ] Run Android Compose emulator regression tests for visible workspace content, compact bottom-navigation height, bottom anchoring, and the Song-to-Setlist action.
- [x] Keep Notifications in the top-right app-bar action with an unread badge.
- [x] Keep Team management and Team Chat reachable without consuming a core bottom-navigation slot.
- [x] Support pull-to-refresh from the top of the Home dashboard.
- [x] Support pull-to-refresh from Songs and Services instead of repeating footer refresh actions; keep offline cached reads and read-only permissions intact.
- [x] Remove the dashboard offline-sync status card while preserving local queues, offline caches, WorkManager retries, and background uploads.
- [ ] Manually verify the center action, notification deep links, and pull-to-refresh on Home, Songs, and Services on a physical Android phone.

## Song library and ChordPro experience

- [x] Search covers song titles, artists, keys, time signatures, ChordPro lyrics and chords, and song notes.
- [x] Run full-text filtering off the UI thread and show progress while results are refreshed.
- [x] Bound manual/imported ChordPro input and limit the live editor preview to a sample of large charts.
- [x] Parse saved song charts and Live Stage snapshots away from the UI thread; cancel obsolete work when the selected song or setlist item changes.
- [x] Bound saved chart display parsing to 12,000 rows and 8,192 characters per row; show when the rendered copy is shortened without changing the original ChordPro source.
- [x] Keep the original ChordPro text unchanged when showing a preview sample or transposed chart.
- [ ] Manually test a long imported chart, rapid song/setlist switching, and auto-scroll on a physical Android device.

## Team Chat

- [x] Load conversation history in stable 50-message pages using a timestamp-plus-ID cursor; retain the reader's position when older pages are prepended.
- [x] Merge Realtime inserts into paginated history without duplicates and follow new messages only when the reader is already near the bottom.
- [x] Show the unread Team Chat notification count on the active workspace's Team tab and clear the signed-in member's alerts through the validated viewport read cursor, including older messages outside the loaded page.
- [x] Store one organization-scoped read cursor per member, prevent backward cursor movement, and show the current-member read count on each sent message.
- [x] Restrict read-state visibility to current organization members and updates to the signed-in member's own cursor.
- [x] Support the Android keyboard Send action as well as the visible Send button.
- [x] Apply IME-aware layout padding to keep the Team Chat composer above the software keyboard.
- [x] Retain an unsent draft after a message-send failure and keep the Send action at least 48 dp high.
- [x] Keep the organization shared-files library in its own Community destination instead of embedding it in Team Chat.
- [x] Separate organization reference-file uploads from conversation attachment uploads.
- [x] Keep the four core bottom-navigation destinations unchanged when opening Community resources.
- [x] Assign persistent categories when uploading files to the organization library.
- [x] Search shared files by filename, MIME type, or uploader and filter by category.
- [x] Sort shared files by newest, oldest, or filename without changing their access scope.
- [x] Keep conversation attachments out of the organization-library query.
- [x] Preview supported images, PDFs, plain text, and CSV files in-app; retain external viewing for other file types.
- [x] Let uploaders rename their own library files and change categories with column-scoped database permissions.
- [x] Let uploaders delete their own library files with confirmation and uploader-scoped Storage deletion.
- [ ] Manually verify category selection, search, filtering, sorting, previews, rename, deletion, and uploads on a physical Android phone.
- [ ] Manually verify shared-file upload, preview/open, rename, deletion, refresh, and compact Team member cards on a physical Android phone.
- [ ] Manually verify keyboard behavior, TalkBack announcements, read receipts between two accounts, and incoming-message scrolling on physical Android devices.

## Release validation

- [x] Automated JVM unit tests, Android Compose emulator UI tests, and the debug APK build run in GitHub Actions.
- [ ] Perform a manual TalkBack pass.
- [ ] Check the smallest supported phone width and larger font scale (at least 1.3x and 2.0x).
- [ ] Verify light and dark appearance on a physical Android device.
- [ ] Verify the keyboard and navigation gestures on a physical Android device.

The unchecked release checks require physical-device or accessibility-service testing; a successful CI build does not replace them.
