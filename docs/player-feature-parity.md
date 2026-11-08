# SmartTube player feature parity

Audit date: 2026-11-08. Upstream reference: `a945135eee6387cd6549b87604ad673944c06b5d`.
The starting personal UI was `7b5a2846f70615299f58d6c9bfb163105892944c`.
This is source and route evidence, not a claim that every effect has been tested on a TV.
The requirement is to retain the base SmartTube player functionality in both regular playback
and native Shorts. The device checks below must accompany the final APK's build report.

## Findings and fixes

The personal bars retain their original `Action` objects and the original controller dispatch.
Their smaller layout does not remove the action map. All 24 mapped upstream actions are in
Settings → Advanced; its scrollable preference list is independent of the control bar's
12-item limit. The fallback `actions.addAll(mActions.values())` also includes future mapped
upstream additions.

The player also exposes the 16 non-core SmartTube actions directly in a personal-only
horizontal utilities row above the timeline. The first enabled action is the original
Background playback/PiP action. The row includes speed, repeat, queue, full HQ settings,
statistics, screen dimming, AFR, chat/comments, search, share, seek interval, zoom, rotate,
flip and sound. Each original icon has its original readable action name below it. The
row obeys the existing player-button preferences; Advanced remains a fallback for hidden
buttons and explicit Options commands.

The 68dp row uses fixed 96dp cells and horizontal scrolling, so enabling all 16 actions
does not meet the two core bars' 12-control limit. Its absolute dock leaves the existing
timeline, transport, left/right actions and suggestion-row coordinates unchanged. It is
hidden during compact seek/storyboard presentation and restored afterward. Up from the
timeline's existing top-edge route enters the utilities; Down returns to the timeline;
Up from utilities retains the original close-overlay behavior. Initial controls focus
still selects Play/Pause, while a utility already focused on reappearance keeps focus.

The row binds the same original action objects with the existing button presenter and
original click, long-click and selection listeners. A third adapter receives the same
state/icon invalidations as the core adapters. Per-action updates rebind the existing
button and label rather than rebuilding the row, retaining focus and scroll. Unbind and
adapter replacement unregister the old observer and clear the original button callbacks
and pending tooltips. No playback controller or decoder is added.

The following gaps were corrected by this audit and subsequent device validation:

- Previous, play/pause, and next lived outside the map. Hiding their bar buttons left no
  on-screen fallback. Advanced now includes those three original transport objects. The
  play/pause object is still allocated and owned by the superclass, which updates its state.
- Advanced used ordinary button models. Nested Back, bound like/dislike updates, and
  asynchronous controller callbacks could leave its cached labels, icons, or state stale.
  Its action and explicit Options models now use the existing dynamic description contract.
  State, metadata, and playback callbacks notify them; preference refresh reads the current
  original action label and icon as well as the description. Reopening a menu reuses the same
  models and action objects.
- Subsequent physical-TV validation found native Shorts feed OK only emitted presenter
  click notifications, whose base handlers are no-ops. Feed OK now calls the existing
  engine's `setPlayWhenReady` exactly once before the original notification. It does
  nothing before engine initialization. Regular controls retain the original transport.

The 10 explicit Options commands match every nonempty `onButtonLongClicked` implementation
in the upstream controller set. They call the original listener with the current original
action index and remain usable when physical OK-long-press is disabled. They do not pretend
that an unsupported long action exists for the other actions.

## Action matrix

Paths below start at the regular player. In native Shorts, More opens the same regular
controls; Settings → Advanced therefore reaches this same catalog and the same callbacks.
“Options” means the explicit **Options: …** command in Advanced or the original physical
long press where a visible original button exists.

| Base feature | Personal route | Original implementation / state owner |
| --- | --- | --- |
| Previous | Center transport; Advanced | Original `SkipPreviousAction` → glue `previous()` → presenter `onPreviousClicked`; source restart threshold retained |
| Play / pause | Center transport; Advanced; native Shorts feed OK | Original superclass `PlayPauseAction` / `PlayerAdapter`; Shorts changes the same engine's `setPlayWhenReady` before preserving presenter click notifications |
| Next | Center transport; Advanced; native Shorts Down | Original `SkipNextAction` → glue `next()` → presenter `onNextClicked` |
| Full playback / quality settings | Utilities; Advanced → Playback settings | Original `HighQualityAction` → `HQDialogController.onHighQualityClicked` |
| Video quality | Settings → Quality; full HQ above | Real `FormatItem` video list / `HQDialogController.selectFormatOption`; full HQ retains codec-specific variants and presets |
| Audio track / language | Settings → Audio; full HQ above | Real audio `FormatItem` list, original selection/persistence; full HQ also retains preferred audio language |
| Captions quick toggle | Visible CC; Advanced | Original `ClosedCaptioningAction` → `PlayerUIController.onSubtitleClicked` |
| Caption tracks / translation / advanced settings | Settings → Captions; Options: captions | Basic panel uses actual tracks and source-language metadata; original `onSubtitleLongClicked` still exposes all original subtitle settings |
| Speed toggle / speed choices | Utilities; Settings → Speed; Advanced; Options: speed | `VideoStateController.onSpeedClicked/onSpeedLongClicked`; original per-video/channel state |
| Repeat / playlist / shuffle / reverse / end mode | Utilities; Settings → Repeat; Advanced; Options: playback mode | Original `PlaybackModeAction`; `PlayerUIController.applyRepeatMode/showPlaybackModeDialog` |
| Statistics | Utilities; Settings → Stats for nerds; Advanced | Original `VideoStatsAction` → `onDebugInfoClicked` / original debug view |
| SponsorBlock skip / toggle | Visible SponsorBlock; Advanced | Original `ContentBlockAction` → `SponsorBlockController`; current matched segment is skipped by the original short callback |
| SponsorBlock categories / behavior | Options: SponsorBlock; global settings | Original `SponsorBlockSettingsPresenter`, original reloading callback |
| Playback queue | Utilities; Advanced | Original `PlaybackQueueAction` → `AppDialogUtil.showPlaybackQueueDialog`; selection calls presenter `onNewVideo` |
| Add to playlist | Visible button; Advanced | Original `PlaylistAddAction` / `PlayerUIController.onPlaylistAddClicked` |
| AFR toggle / configuration | Utilities; Advanced; Options: AFR | Original `AFRAction`, `applyAfr`, `AutoFrameRateSettingsPresenter`, and `AutoFrameRateController` |
| Picture in picture / background mode | First utility; Advanced; original global player settings | Original `PipAction` → `onPipClicked`; original engine-block/finish path and Android PiP handling |
| Comments / live chat | Utilities; Advanced; native Shorts comments | Original `ChatAction` broadcasts to `ChatController` and `CommentsController`; their real metadata selects comments or live chat |
| Chat layout / enable settings | Options: chat | Original `ChatController.onButtonLongClicked`; original left/right/disabled callbacks |
| Screen dimming / screen off | Utilities; Advanced; Options: screen dimming | Original `ScreenDimmingAction`, `prepareScreenOff/applyScreenOff/applyScreenOffTimeout/showScreenOffDialog` |
| Seek interval | Utilities; Advanced | Original `SeekIntervalAction` → `PlayerUIController.onSeekInterval` |
| Zoom / aspect ratio / rotation settings | Utilities; Advanced → Video zoom; full HQ | Original `VideoZoomAction` → original resize/zoom/aspect/rotation option categories and callbacks |
| Rotate / flip | Utilities; Advanced | Original `RotateAction` / `FlipAction` → original `onRotate/onFlip`, persisted through `PlayerData` |
| Mute / sound settings | Utilities; Advanced; Options: sound | Original `SoundOffAction`, `applySoundOff/showSoundOffDialog` |
| Share | Utilities; Advanced | Original `ShareAction` → `onShareLink` |
| Search | Utilities; Advanced | Original `SearchAction` → `onSearchClicked` |
| Channel | Visible left action; Advanced; native Shorts author | Original `ChannelAction` / `openChannel` |
| Description / timestamps | Visible description; Advanced; native Shorts title | Original `VideoInfoAction` → `onVideoInfoClicked`; original timestamp callbacks |
| Subscribe / notifications | Visible left action; Advanced; native Shorts subscribe; Options: channel or subscribe | Original `SubscribeAction`, `onSubscribe`, `showNotificationsDialog`; account writes require deliberate user selection |
| Like / dislike | Visible right actions; Advanced; native Shorts like | Original mutually bound `ThumbsUpAction` / `ThumbsDownAction`, original callbacks and metadata updates |

## Full HQ menu is retained

The simple Quality and Audio panels are additional entry points. Advanced's original HQ
action still invokes `HQDialogController.onHighQualityClicked`, which retains all of:

- Full video and audio format lists, preferred audio language, and video presets.
- Video zoom / aspect ratio / rotation categories.
- Network engine and video buffer.
- Audio delay and pitch effect.
- Sleep timer.
- Internally contributed categories and their original hide listeners.

The original `mCategories`, `mCategoriesInt`, `mHideListeners`, and `onDialogHide` flow are
unchanged. Basic track dialogs do not consume those full-menu categories or hide listeners.
Background playback was already commented out in this HQ menu upstream; its supported
configuration remains in the original global player settings and PiP action.

## Shared non-button behavior

The following source paths were compared directly with the upstream reference. Where
stated unchanged, `git diff a945135 -- <paths>` produced no changes at audit time:

| Behavior | Evidence / scope |
| --- | --- |
| Controller registration and event order | `common/.../presenters/PlaybackPresenter.java` is unchanged; registers VideoState, Suggestions, VideoLoader, ErrorFixer, PlayerUI, Remote, SponsorBlock, AFR, HQ, Chat, Comments in their original order |
| Resume position, speed persistence, playback end modes | Original `VideoStateController`; speed state/callbacks unchanged |
| Suggestions, queue loading, previous/next, autoplay | Original `SuggestionsController` and `VideoLoaderController`; loader changes before this audit only add guarded optional caption-language metadata |
| SponsorBlock fetching, categories, timeline, skip watcher | Original `SponsorBlockController` unchanged |
| Frame-rate application and display-mode restore | Original `AutoFrameRateController` unchanged |
| Remote/casting/control events and playback recovery | Original `RemoteController` and `ErrorFixerController` unchanged |
| Live chat and comments/replies | Original `ChatController` and `CommentsController` unchanged |
| Decoder / codec / buffer / formats / audio / subtitle engine | Entire `common/.../exoplayer` tree unchanged |
| Surface / texture engine host and Android PiP | Entire `smarttubetv/.../playback/mod/surface` tree unchanged |
| Global player options and persistence | `PlayerSettingsPresenter`, `PlayerData`, `PlayerTweaksData`, `AutoFrameRateSettingsPresenter`, `SponsorBlockSettingsPresenter` unchanged |
| MediaSession and queue navigator | `PlaybackFragment.createMediaSession` keeps the original connector, control dispatcher, metadata provider, and `BackboneQueueNavigator`; this audit changes none of them |
| Media keys, gamepad and seek | Original `PlaybackActivity` handlers retained. `VideoPlayerGlue.findAction` searches hidden mapped actions in personal mode. The inherited seek UI, confirmation/cancellation, thumbnails, chapters and configured intervals remain in place |
| Original rewind / fast-forward actions | Both objects still exist and original direct seek methods remain. Upstream had their bar insertion commented out; they were not a removed visible upstream button |

The personal flavor deliberately fixes some cosmetic choices (bar placement, font and
theme) for its requested appearance. Existing cosmetic settings may not change those
specific surfaces. This does not establish that every upstream cosmetic preference has
visual parity; the functionality requirement is checked against the underlying player
operations and real option callbacks above.

## Native Shorts

`src/stpersonal/.../shorts/PersonalPlaybackFragment` subclasses the full `PlaybackFragment`
inside the same `PlaybackActivity`. It does not create another decoder, presenter,
controller set, queue, MediaSession or account state. Its side controls call the original
presenter with the current original button state. More (or Menu while the feed has focus)
opens the regular controls, including the entire catalog and explicit options.

The feed has intentional navigation changes: Down selects source Next, Up directly calls
the source loader's previous queue item instead of the regular player's restart threshold,
Left opens the rail, and OK toggles the same player. Repeated Up/Down presses are suppressed
to avoid unintended queue skipping. FF/RW opens regular controls; inherited seeks and media
dispatch continue afterward. When regular controls, a dialog, or PiP is active, feed key
interception yields to the original path. Source key activity/sleep timers are still notified.

## Regression and device validation

`PersonalPlayerActionsTest` (stpersonal unit-test source set) exercises actual glue/action
objects with a recording `PlayerAdapter` and listener:

1. Hides every bar button and verifies all 24 mapped actions plus three original transport
   objects still exist; dispatches previous/play/pause/next through those models.
2. Verifies a menu command is the same action instance used by the visible bar, retains
   its current controller state, and is reused on subsequent catalog creation.
3. Disables both physical long-press preferences, verifies physical Chat long press is
   suppressed, then verifies all 10 explicit options invoke their original callbacks.
4. Verifies bound like/dislike state and asynchronous next-title updates refresh the menu
   models without replacing their identities.

Run with the existing Java 11 test runtime:

```text
:smarttubetv:testStpersonalDebugUnitTest --tests com.liskovsoft.smartyoutubetv2.tv.ui.playback.other.PersonalPlayerActionsTest
```

`PersonalShortsPlaybackTest` adds two actual feed-view D-pad OK regressions with a recording
engine subclass: playing → paused → playing performs one engine write before each notification,
and OK before engine readiness performs neither operation. Its selector is
`com.liskovsoft.smartyoutubetv2.tv.ui.personal.shorts.PersonalShortsPlaybackTest`.

The direct-row extension adds two action-model regressions to `PersonalPlayerActionsTest`
and four real-presenter regressions in `PersonalPlayerUtilitiesPresenterTest`: all 16
named cells, horizontal scroll and first/last boundaries; click/held-OK object forwarding
without an extra short click; state/label refresh retaining focus and scroll, observer
replacement/unbind cleanup; and the actual row's top-edge/utility/timeline focus route,
reappearance focus, compact/empty visibility and unchanged core coordinates. The new
selector is `com.liskovsoft.smartyoutubetv2.tv.ui.mod.leanback.playerglue.tweaks.PersonalPlayerUtilitiesPresenterTest`.

At source handoff these tests are written but delegated to the build owner; this audit did
not run Gradle, sign an APK, modify an account, or control the TV.

Remaining final-APK checks, on regular playback and Shorts unless hardware/content-specific:

- Confirm the named utilities row is visible without opening Settings, starts with Background
  playback, fits the default choices, and scrolls through all 16 enabled choices. Verify the
  actual background/PiP/resume effect and each original short/long action deliberately.
- Verify Play → Up → timeline → Up → utilities; utility Down → timeline; utility Up closes
  the overlay. Open a utility dialog and return; preserve utility focus, state and scroll.
  Start/confirm/cancel seeking and verify storyboard clearance and dock restoration; disable
  all utility button preferences and confirm the core/timeline coordinates stay unchanged.

- Open More → Settings → Advanced; scroll through all actions and Options without truncation.
  Hide transport bar buttons, confirm Advanced transport works, then restore the preferences.
- Change repeat/speed/captions in a child menu and press Back; labels, state, icon and focus
  must reflect the current original action. Verify next-title changes during queue navigation.
- Verify all full HQ categories, codec/audio selection, advanced subtitles, per-channel/video
  speed, sleep timer, audio delay and pitch effect actually apply and persist as upstream.
- Verify seek confirm/cancel, interval, storyboard/chapter display, media/gamepad keys,
  queue selection, end/repeat/shuffle/reverse modes and resume position.
- Use content with known SponsorBlock segments to verify actual skipping; verify live chat,
  comments/replies, audio/subtitles, zoom/rotate/flip, statistics and screen/sound actions.
- Verify AFR on the real display, PiP/background/resume/Home, dialog nested Back and activity
  recreation restore video/subtitles correctly. These depend on device capability.
- Inspect account-mutating commands and callbacks without selecting them during automation;
  likes, subscription changes and playlist writes need a deliberate user test if required.

See [personal-player.md](personal-player.md) for the earlier measured layout and device
results; those earlier results must not be described as validation of this revision.
