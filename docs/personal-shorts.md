Personal Shorts presentation
============================

The stpersonal fragment_playback overlay instantiates PersonalPlaybackFragment,
a subclass of the existing PlaybackFragment hosted by the same PlaybackActivity.
PlaybackPresenter, controller registration, ExoPlayer, queue, format selection,
SponsorBlock, state/history and MediaSession remain owned by the original player.
Regular videos delegate to the superclass and use its normal controls. Switching
out of Shorts restores the surface/subtitle layout parameters and padding.

The browse factory adds a default createShortsFragment hook. Other flavors still
create ShortsGridFragment. The personal entry consumes real BrowsePresenter feed
updates and starts the first/restored playable short once. Returning from the
player leaves a Play Shorts action; it cannot reopen automatically in a Back loop.
PersonalShortsSession retains the current source group because Video weakly owns
its group, and remembers the current short for explicit re-entry.

Shorts has three focus areas: rail, portrait video and metadata/actions. Right
from the rail focuses the video and hides the rail. Right from video focuses Like.
Back first reveals the rail while keeping the current video. A second Back exits
through the existing player/origin lifecycle. Held Back is consumed as one gesture.
Home, Subscriptions, Library and Settings route to the existing browse view;
Advanced explicitly opens the full existing disclosure, including pinned sections.

Down calls PlaybackPresenter.onNextClicked. Up calls the original
VideoLoaderController.loadPrevious: the generic Previous UI otherwise restarts
the current video after a threshold. loadPrevious still chooses through the source
SuggestionsController/Playlist and dispatches onNewVideo, saving prior state and
using the full controller/engine path. Source queue priority and section-playlist
preferences continue to apply. A queued regular video restores regular presentation.

Title, author, avatar, subscription/like state and available like count come from
the existing video/controller callbacks. Actions use the original controller IDs.
More exposes the existing controls, menus and quality options. Hardware/gamepad
seeking exposes and delegates to those same controls. Account selection reloads
the same video through the source controller path to refresh account-specific state.
Related-video and Remix controls are omitted because the current model supplies no
supported matching route. No reference media or fixture content is included.

The background is a darkened, low-resolution image of the current runtime video.
The portrait surface uses measured 304x531dp bounds at (296,4.5), with the focus
outline around it. Native metadata occupies the right side. Dialog preview owner
leases defer portrait relayout and hide native chrome until the last lease releases.
Existing controls, active dialogs and PIP keep their original input paths.

Integrated debug assemble and lint passed at `f8a15d7` on 2026-11-08 (0 lint
errors, 305 retained warnings, no new issues). Earlier debug-lineage TV captures
verified decoded portrait playback, Down to the next real video, Up to the
previous video, Back revealing the rail while retaining playback, and a second
Back returning to the stopped entry without an automatic reopen. The separate
31-assertion navigation smoke passed; it does not exercise Shorts playback.

Final signed-release QA remains pending. Remaining manual checks include
SurfaceView rounded clipping, feed continuation beyond loaded items, account
changes, queue-to-regular transition, PIP, background/resume and More/active
seek/dialog/comments focus and restoration. No additional player or media decoder
is instantiated. Basic next/previous observations do not establish continuation
or every controller path.
