# Personal player presentation

The `stpersonal` resource overlay follows the captured YouTube 7.28.301 player on the target 1920×1080 / 320 dpi TV. It retains SmartTube's playback adapter, controller registration order, action objects, track callbacks, seek UI, MediaSession, subtitle/chat callbacks and activity lifecycle.

The presentation is selected by `personal_player_presentation`; its default is false. Existing upstream flavors use their original layouts and action adapters. The personal flavor uses a centered previous/play/next group, edge action groups, a thin timeline with separate current/total labels, top metadata, and a right settings panel. The settings panel contains a route to every original action and explicit options commands for long-press features.

## Original action placement

Visible controls continue to honor the existing player-button preference. A hidden visible control remains reachable in Settings → Advanced. `ControlBarPresenter.MAX_CONTROLS` remains 12: the personal primary row has at most 3 actions, secondary row at most 9. Advanced is a scrollable preference list, not a truncated control row.

| Original ID | Class | Personal placement | Stateful/long-press behavior |
| --- | --- | --- | --- |
| `lb_control_play_pause` | `PlaybackControlsRow.PlayPauseAction` | Center transport | Original play/pause object and media-key dispatch |
| `lb_control_skip_previous` | `PlaybackControlsRow.SkipPreviousAction` | Center transport, left | Original previous callback |
| `lb_control_skip_next` | `PlaybackControlsRow.SkipNextAction` | Center transport, right | Original next callback and dynamic next-title label |
| `lb_control_high_quality` | `HighQualityAction` | Settings → Quality; Settings → Audio; Advanced | Quality and Audio open separate real track lists through the existing format-selection callback; Advanced opens the original combined HQ controller |
| `action_repeat` | `PlaybackModeAction` | Settings → Repeat; Advanced | Settings opens the original mode picker; Advanced retains the original cycling action and options command |
| `action_video_speed` | `VideoSpeedAction` | Settings → Speed; Advanced | Settings opens the original long-action speed chooser; Advanced retains the original short toggle and options command |
| `action_pip` | `PipAction` | Advanced | Original PiP callback |
| `action_screen_dimming` | `ScreenDimmingAction` | Advanced | Original toggle; explicit dimming options calls the original long callback |
| `action_chat` | `ChatAction` | Advanced | Original chat toggle and explicit placement/options command |
| `action_search` | `SearchAction` | Advanced | Original in-player search callback |
| `action_share` | `ShareAction` | Advanced | Original sharing callback |
| `action_seek_interval` | `SeekIntervalAction` | Advanced | Original seek-interval dialog |
| `action_video_zoom` | `VideoZoomAction` | Advanced | Original video zoom callback |
| `action_rotate` | `RotateAction` | Advanced | Original rotation callback |
| `action_flip` | `FlipAction` | Advanced | Original flip toggle |
| `action_sound_off` | `SoundOffAction` | Advanced | Original mute toggle and explicit sound options command |
| `action_afr` | `AFRAction` | Advanced | Original AFR toggle and explicit AFR options command |
| `action_channel` | `ChannelAction` | Left edge, first; Advanced | Original channel navigation, dynamic avatar and notification options |
| `action_info` | `VideoInfoAction` | Left edge, Description; Advanced | Original info/description callback; comment behavior remains controller-owned |
| `action_subscribe` | `SubscribeAction` | Left edge, Subscribe; Advanced | Existing state index/label and notification options |
| `action_thumbs_up` | `ThumbsUpAction` | Right edge; Advanced | Original state and mutually exclusive bound dislike action |
| `action_thumbs_down` | `ThumbsDownAction` | Right edge; Advanced | Original state and mutually exclusive bound like action |
| `lb_control_closed_captioning` | `ClosedCaptioningAction` | Right edge; Settings → Captions; Advanced | Visible button retains the original short toggle; Settings opens real Off/language choices; Advanced options and long press retain the full subtitle settings |
| `action_playlist_add` | `PlaylistAddAction` | Right edge; Advanced | Original add-to-playlist dialog |
| `action_playback_queue` | `PlaybackQueueAction` | Advanced | Original queue dialog |
| `action_content_block` | `ContentBlockAction` | Right edge; Advanced | Original SponsorBlock skip/toggle and explicit SponsorBlock settings command |
| `action_video_stats` | `VideoStatsAction` | Settings → Stats for nerds; Advanced | Original statistics toggle |

The original glue also allocates rewind and fast-forward actions but does not add them to its adapters. Their existing seek/key paths are retained. Hidden mapped actions remain discoverable by their original key codes through the personal `findAction` fallback. Physical long-press controls keep upstream gesture preferences. Explicit named options in Advanced call the same long-action controller callbacks without requiring a physical long press.

Advanced wraps the original Action objects rather than reconstructing their state. Its short-action entries read current labels and two-state indices when opened/selected. Controller state changes still invalidate visible action adapters. The tail fallback includes newly added upstream mapped actions so future features do not silently disappear.

Quality and Audio use `HQDialogController.showFormatDialog` to display only the requested
real video/audio radio category. The shared selection callback retains format persistence,
preset warnings, media reload and overlay behavior. These panels leave the full HQ category
maps and hide listeners intact; only the original combined HQ dialog consumes their cleanup.
Track labels and disabled entries retain their actual engine meanings. No disabled renderer
is relabeled as Auto, and unavailable official audio processing features are not invented.
The personal control bar also bypasses the legacy wrap-content center-margin measurement,
so its edge groups anchor to the actual dock width.

Basic personal Quality now groups supplied video tracks by the existing engine display
resolution and rounded actual frame rate. The current selected format wins its group; all
other groups retain the first candidate from TrackSelectorManager's codec/bitrate preference
order. Only the source-provided list is considered: its support filtering and the user's
explicit all-formats override remain authoritative. No additional codec support is inferred.
Known high frame rates appear in labels such as 1080p60; simultaneous 24/30 variants are
distinguished, while unknown frame rate is not fabricated as 30. Default/Disabled and
unknown-resolution tracks remain in the original full HQ dialog under Advanced. The grouped
display option forwards selection to its original FormatItem option, preserving persistence,
presets, warnings and reload behavior. Basic Audio and the two-argument showFormatDialog API
keep their original labels and choices. Other flavors and the full HQ category builder are unchanged.

`VideoQualityOptionsTest` covers duplicate codec grouping, current-track priority, distinct
frame rates, source-list preservation, excluded basic-only entries, and original callback
identity. Its four JUnit tests also run standalone against the existing compiled contracts;
no Android runtime or Gradle build is needed for those grouping cases.

Personal Settings → Captions opens a primary radio list of Off and real source caption
tracks, including genuine ASR tracks with their actual labels. Generated translations are
reachable through Auto-translate, whose description names the active translated track.
The service uses the same `*` marker for ASR and translation, so the marker alone cannot
identify translations. The loader retains only source display-language names derived from
actual MediaSubtitle URLs without a `tlang` query; no URL/token is retained. Missing or
incomplete metadata falls back to the complete real-track picker. Same-language entries
using the source URL count as source-equivalent. Metadata is cleared on new video, engine
release, finish and view destruction, and guarded by request generation/current video ID.
Only the engine-supplied default track is labeled Off. Lists are copied before the existing
preferred-language reorder; no missing tracks are fabricated. Selection reuses the same
callback as the full chooser, preserving channel enablement and PlayerData persistence,
and its close callback refreshes the CC button. The visible CC button retains its short
toggle; long press and Advanced → Closed captions options reach the original settings for
manual/autogenerated sublists, per-channel enablement, style, size and position. Other
flavors keep their existing routes. An unavailable engine list falls back to that chooser.
The submenu is a navigation row, never a checked track; Back restores its focus and rebuilds
the primary labels/selection from current engine state. Full Advanced subtitle settings
and the original two-argument basic picker retain their existing complete track lists.

The six personal Settings summaries read current player/action state instead of capturing
the text when the parent menu opens. Returning from a child panel refreshes only explicitly
dynamic rows. Existing quality-info and action-state callbacks also notify those rows when
the engine completes a change after Back. Each attached dynamic preference owns one listener
and unregisters it on detach; the player glue retains only its six reusable option models.
Static preferences and other flavors retain their existing binding. Quality text comes
from the engine's track formatter, so a requested/checked radio row is not presented as
confirmation that the actual stream has already switched.

## Measured overlay refinements

The personal title uses 21 sp medium Android sans-serif and its description dock moves up
12.5 dp. Compact overlay metadata uses the real author, provider view count, and browse item's
production/relative date; full metadata supplies a date only when the compact source is absent.
Rich description, ratings and subscriber data remain on `Video` and in the original Description
route. No publication age or popularity value is fabricated. Before metadata arrives, unavailable
compact fields are omitted.

The timeline moves down 2 dp and the control group another 1.5 dp. Primary controls receive 8 dp gaps,
which place their intended centers at 860/960/1060 px on the reference 1920 px canvas. The channel
image is 28 dp inside its 36 dp control. Unsubscribed presentation reads Subscribe while the original
Action index/callback stays intact. Original vector drawings supply outlined thumbs/bookmark;
active original thumb icons and playlist highlight color retain their state indication.

Personal row entry/reappearance targets the existing play/pause control. Hidden-overlay horizontal
seek keys and dedicated seek keys explicitly target the existing timeline. Active seek mode and
its original confirm/cancel callbacks are preserved; pending default focus is discarded when an
explicit seek begins. Up can still move from transport controls to the timeline for ordinary seeking.

The live panel lease also resizes AndroidX's fixed 360 dp inner preference container to match its
612 px host, then restores its original parameters on release. The previous 720 px inner width had
extended 108 px beyond the host's left edge. Preference rows use a 48 dp minimum with font padding
removed: ordinary title/summary rows fit the measured 96 px target while longer real settings can
grow rather than losing their explanatory text. The live preview bounds remain unchanged.

## Real video preview during settings

`AppDialogActivity` requests a preview lease only for a normal player-origin dialog. It checks the top application view, a live PlaybackFragment/activity, the user's don't-resize preference, and PiP. Browse-origin dialogs, background playback, notifications/transparent dialogs, comments and opaque dialog activities keep their existing presentation path.

`PersonalPlayerDialogPresentation` resizes the existing `surface_root` and subtitle host. At 1920×1080 its requested video frame is x=54 px, y≈218 px, width=1146 px and height≈645 px, matching the measured reference. The right panel is 612 px wide with a 54 px end margin. The surface is retained; no screenshot or second player substitutes for live playback.

The optional common `DialogPreviewHost` interface prevents upstream `fitVideoIntoDialog` from also applying its zoom/gravity transform to these personal normal dialogs. Controller registration and callbacks remain in their original order. Comments/overlay/transparent dialog paths remain upstream-owned. Video size/aspect callbacks continue to run on the original host.

Leases are owner-scoped and idempotent. Nested activities cannot restore fullscreen while another lease remains. Closing, stopping or destroying the dialog releases its lease. Destroying the player view clears all leases. Original host parameters, backgrounds, clipping, subtitle padding and affected visibility are restored. Later player/chat visibility updates take precedence over a stale captured visibility.

The visible preference child supplies the personal presentation type. Its comments class and
saved per-child overlay/transparent flags survive nested Back navigation without relying on
the presenter's latest category backup. A child-backstack observer and post-commit refresh
update the lease; the observer is removed on destruction or manager replacement. Paused
activities cannot reacquire through a queued refresh. Returning from comments to a normal
personal panel restores configured zoom/center gravity before resizing, avoiding a second
application of the legacy comments transform.

## Validation and remaining gaps

Integrated debug validation on 2026-11-08 at `f8a15d7` passed assemble (2m57s),
lint (0 errors, 305 retained warnings, no new issues) and four quality-options
tests through Gradle. The debug APK upgraded the target TV while retaining its
signed-in account. These results do not constitute final signed-release QA.

On that APK, real NASA playback loaded with the correct title at x96 using 42px
text. The focused center transport occupied x912–1008, centered at x960; labels
were legible. The right panel occupied x1254–1866, with a live SurfaceView preview
at x54–1200/y217–862. Basic Quality displayed one choice each for
2160/1440/1080/720/480/360/240/144, and selecting 720 changed the stream.
Earlier debug validation also observed fullscreen restoration after a codec
change. The separate 31-assertion navigation smoke passed, but does not exercise
player actions or track changes.

The interim signed `c5434f7` release also passed assemble (4m37s), release lint
(0 errors, 305 retained warnings, no new issues), signature/alignment checks and
an in-place TV update retaining the account. On a regular captioned video,
comments opened, replies opened, and Back returned through comments to Advanced
with the normal preview restored, then to fullscreen. The real seek storyboard,
chapter label and green SponsorBlock timeline segment rendered. Back dismissed
the seek UI; position cancellation and actual segment skipping were not proven.
On `f8a15d7`, reopening Settings confirmed the selected stream as 720/30/VP9;
the stale parent summary exposed by child Back is addressed by the subsequent
dynamic-description change and still requires final-APK validation.

Remaining on-TV checks, including the final signed release:

- Remaining transport/media-key states, controlled seek confirmation/cancel, and actual SponsorBlock skipping.
- Every row in the placement matrix, with state changes, bound like/dislike state, original long presses and explicit Advanced options.
- Settings → Quality/Audio/Captions/Speed/Repeat → Back → Settings → Back; focus returns to the invoking control.
- Other comments/reply contexts, live chat and Shorts comments; the regular-video nested Back path passed on the interim release.
- Close via Back, Home, background/resume, timeout and activity recreation; verify fullscreen/subtitle restoration, no stale clipping and no changed engine state.
- Browse settings while a background/PiP player exists; notification/comment/opaque/don't-resize paths must not acquire a personal preview lease.
- Live video, chat, subtitles, vertical/cinema video, zoom/rotation and texture/surface modes; measure actual SurfaceView clipping on the TV.

The visible SponsorBlock button is an intentional additional SmartTube control. Quality and Audio use separate track panels, while codec-specific labels and the available audio choices follow SmartTube's engine capabilities. The personal menu preserves SmartTube's actual capabilities; it does not add inactive Report/Feedback controls. Android system sans-serif is used, with no copied proprietary JavaScript or font assets. Remaining glyph/state coverage, animation timings, seek-preview placement and unmeasured row coordinates require device comparison. Preference resource overlays apply consistently to the personal flavor's settings surfaces.
