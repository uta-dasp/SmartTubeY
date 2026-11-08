# Personal settings and accounts

The `stpersonal` browse factory creates `PersonalSettingsFragment`; all other flavors keep
`SettingsGridFragment`. Personal browse and search avatars open the same native
`PersonalAccountChooserFragment`.
Both surfaces use existing SmartTube data and presenters. They do not use accounts or
credentials from the installed Google YouTube application.

## Settings

The page uses a scrolling category list and detail pane. The navigation rail expands when
focused and collapses when Settings content receives focus. Down on the pinned Settings
rail row stays on that row. The Settings root clips its ScrollView children to their own
viewports so scrolled rows cannot paint over the fixed page heading.
At density 2 / 1920 x 1080, the intended category left edge is 525 px, its width is 420 px,
the divider is 967 px, and the detail pane begins at 998 px. The pane retains the browse
host's translated virtual width: the captured expanded-rail reference also shifts the right
details partly off-screen while the rail is focused; content focus removes the 378 px rail
translation. Expected collapsed positions are x165 for category labels and x589 for the divider.
The host
must hide its generic browse header and set content
top margin to zero for `MediaGroup.TYPE_SETTINGS`.

`SettingsGroup.getItems()` supplies the entire category list in source order. No category
is filtered or capped. A future category receives a generic description and still runs
its original `SettingsItem.onClick`. The known source categories are:

| Drawable key | Existing action |
| --- | --- |
| `settings_account` | `AccountSettingsPresenter.show()` |
| `settings_cast` | `RemoteControlSettingsPresenter.show()` |
| `settings_language` | `LanguageSettingsPresenter.show()` |
| `settings_app` | `GeneralSettingsPresenter.show()` |
| `settings_main_ui` | `MainUISettingsPresenter.show()` |
| `settings_player` | `PlayerSettingsPresenter.show()` |
| `settings_afr` | `AutoFrameRateSettingsPresenter.show()` |
| `settings_subtitles` | `SubtitleSettingsPresenter.show()` |
| `settings_search` | `SearchSettingsPresenter.show()` |
| `settings_block` | `SponsorBlockSettingsPresenter.show()` |
| `settings_dearrow` | `DeArrowSettingsPresenter.show()` |
| `settings_backup` | `BackupSettingsPresenter.show()` |
| `settings_about` | Source-selected `AboutSettingsPresenter` or `AboutSimpleSettingsPresenter` |

Focusing a category updates its actual description. Clicking the category, **Open options**,
or Right first applies the original settings-password comparison. Language, General,
Interface, Player, Auto frame rate, Subtitles, Search, SponsorBlock and DeArrow then build
their original synchronous presenter models into native detail-pane option rows. No option
callback runs on category focus. The complete source order is retained, with selected
radio/check values and direct single-switch toggles. Accounts, pairing, backup, about/update
and unknown categories continue to run their original action and lifecycle.

`AppDialogPresenter.captureDialog` temporarily collects only that explicit synchronous
model-building action. It restores pending dialog data and callbacks in `finally`; active
dialogs and background-thread callers cannot enter capture. Captured models retain the
original `OptionCategory`/`OptionItem` objects, start and finish callbacks. Transparent,
overlay, timed or identified presentations replay their original dialog instead of using
the native pane. The shared presenter takes the original path for every non-capture call,
including all other flavors and all player dialogs.

Selecting a radio/check category opens its original renderer, retaining dependency rules,
mutually exclusive checkbox behavior and nested special dialogs. A button category keeps
its original preference screen before executing its action. **All options** replays the
complete original root, including its presentation flags and callbacks. Source finish
callbacks run once per captured model when its original dialog closes or the inline pane
is left; this can show a source restart notice after returning from a child option dialog.
The pane rebuilds from saved values on return and restores its option-row focus. Native
single switches use the original `OptionItem.onSelect` callback and refresh in place.

Right or OK enters the native options; Left or Back from an option returns to the selected
category. Selecting another category finishes the prior inline model. The detail pane
continues to show actual account, language and version status on its category landing view.
Reentering content targets the remembered category, and Back from settings content focuses
the rail. Back from the rail continues through the existing browse activity behavior.

## Accounts

The carousel reads `MediaServiceManager.loadAccounts()` and `getSelectedAccount()`, which
delegate to the app's existing sign-in service. It renders only those saved accounts, an
Add account card, and a Watch as guest button. Avatar images come from those account models;
an original initial-based placeholder handles unavailable images. No Google application
storage, tokens, passwords, or private network traffic are read.

| Control | Existing route |
| --- | --- |
| Saved account card | `AccountSelectionPresenter.selectAccount(account)` |
| Watch as guest | `AccountSelectionPresenter.selectAccount(null)` |
| Add account | `YTSignInPresenter.start()` |
| Focused saved account's manage gear | `AccountSettingsPresenter.show()` |

Selection retains the source channel refresh, history setting, account protection, and
profile behavior. Add and Manage first apply the same settings-password gate as the
settings page. The source account manager retains its removal confirmation, password
controls, separate-profile settings, and selection-on-boot option. These remain reachable
through the Accounts settings category even with no saved accounts.

The focused card grows its circular avatar and exposes the management control. Down goes
to that control, then guest; Up returns to the associated card. Focus identity is remembered
across view recreation using the model email/name and avatar URL without logging it. Closing
the chooser refreshes the browse badge and any visible account detail pane. Add and Manage
continue into the existing app flows after dismissing the chooser.

Search refreshes its badge when the chooser closes. An actual account change clears and
reloads the current query through `SearchPresenter`; protected accounts still require the
source password acceptance before results remain accessible. Canceling without changing
accounts leaves the current search results in place.

## Validation and remaining gaps

Integrated debug assemble and lint passed at `f8a15d7` on 2026-11-08 (0 lint
errors, 305 retained warnings, no new issues), and its TV upgrade retained the
signed-in account. Earlier integrated debug sessions verified normal sign-in,
saved-account state and real subscribed-channel/feed data. The 31-assertion
physical-TV navigation smoke passed, including account chooser cancellation,
Settings category/options focus, opening the original Accounts manager and Back
restoration. It did not mutate account settings. Settings full-page content mode
is integrated. Final signed-release QA remains pending.

The native shell approximates the measured reference geometry. Ordinary settings now list
their source options inside the detail pane; radio/check choices and special workflows
continue to use the existing functional dialogs. They do not yet reproduce the official
application's complete inline choice layout. The new inline pane requires integrated build,
unit-test and TV validation. `AppDialogCaptureTest` covers original option identity and
callbacks, callback isolation, exception cleanup and active/special-dialog fallback.
Typography uses Android fonts, the management
glyph uses the system font, and the backdrop is an original gradient drawing. Exact focus
motion, long-carousel scrolling, edge clipping, and restoration after sign-in/manage
still need complete TV coverage. Multiple/protected account switching, account
passwords, profile-specific state and activity recreation remain manual checks;
the established sign-in and saved-account read checks do not cover those flows.
