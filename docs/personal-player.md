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
| `lb_control_closed_captioning` | `ClosedCaptioningAction` | Right edge; Settings → Captions; Advanced | Visible button retains the original short toggle; Settings opens the original long-action caption chooser |
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

Static XML parsing and whitespace checks are performed before handoff. Full personal-variant compilation and on-TV checks depend on the integrated baseline build; this branch has not run Gradle or controlled the TV.

Required on-TV checks:

- Play/pause/previous/next, media keys, seek confirmation/cancel, storyboard thumbnails and SponsorBlock timeline segments.
- Every row in the placement matrix, with state changes, bound like/dislike state, original long presses and explicit Advanced options.
- Settings → Quality/Audio/Captions/Speed/Repeat → Back → Settings → Back; focus returns to the invoking control.
- Settings → Advanced → Chat/comments → replies → Back → comments → Back → Advanced; verify the live preview returns at its normal size and fullscreen restores after closing.
- Close via Back, Home, background/resume, timeout and activity recreation; verify fullscreen/subtitle restoration, no stale clipping and no changed engine state.
- Browse settings while a background/PiP player exists; notification/comment/opaque/don't-resize paths must not acquire a personal preview lease.
- Live video, chat, subtitles, vertical/cinema video, zoom/rotation and texture/surface modes; measure actual SurfaceView clipping on the TV.

The visible SponsorBlock button is an intentional additional SmartTube control. Quality and Audio use separate track panels, while codec-specific labels and the available audio choices follow SmartTube's engine capabilities. The personal menu preserves SmartTube's actual capabilities; it does not add inactive Report/Feedback controls. Android system sans-serif is used, with no copied proprietary JavaScript or font assets. Exact glyphs, category icons/checkmarks, all animation timings, seek-preview placement and final row coordinates require device comparison. Preference resource overlays apply consistently to the personal flavor's settings surfaces.
