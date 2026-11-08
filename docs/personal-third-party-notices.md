# Tracked license and notice inventory

This inventory records checked-in license and notice files in SmartTubeY and
its pinned submodules. Preserve the existing text and copyright headers with
each component. External Gradle artifact metadata belongs to the resolved
dependency inventory; component-specific license texts remain authoritative.

The inventory was made at application source `8fae9f7`, with
`MediaServiceCore` at `db543d4d5d639cbf3084e3870b0dac6e4e2524a1` and both
`SharedModules` checkouts at `5b6b5d1d3ba670e3f89edef1d1394a7562251f2c`.

| Component | License text checked into the component | Tracked files |
| --- | --- | --- |
| SmartTube application | MIT | [LICENSE](../LICENSE), [.reuse/dep5](../.reuse/dep5) copyright metadata |
| ChatKit | Apache License 2.0 | [chatkit/LICENSE](../chatkit/LICENSE) |
| DoubleTapPlayerView | MIT, copyright Viktor Krez | [doubletapplayerview/LICENSE](../doubletapplayerview/LICENSE) |
| Bundled Amazon ExoPlayer tree | Apache License 2.0 | [exoplayer-amzn-2.10.6/LICENSE](../exoplayer-amzn-2.10.6/LICENSE) |
| File picker | Apache License 2.0 | [filepicker-lib/LICENSE](../filepicker-lib/LICENSE) |
| Bundled Fragment library | Apache License 2.0 | [fragment-1.1.0/LICENSE](../fragment-1.1.0/LICENSE) |
| Bundled Leanback library | Apache License 2.0 | [leanback-1.0.0/LICENSE](../leanback-1.0.0/LICENSE) |
| SlidableActivity | Apache License 2.0 | [slidableactivity/LICENSE.md](../slidableactivity/LICENSE.md) |
| MediaServiceCore | MIT | [MediaServiceCore/LICENSE](https://github.com/yuliskov/MediaServiceCore/blob/db543d4d5d639cbf3084e3870b0dac6e4e2524a1/LICENSE) |
| SharedModules | MIT | [SharedModules/LICENSE](https://github.com/yuliskov/SharedModules/blob/5b6b5d1d3ba670e3f89edef1d1394a7562251f2c/LICENSE) |
| AppUpdateChecker2 | MIT | [SharedModules/appupdatechecker2/LICENSE](https://github.com/yuliskov/SharedModules/blob/5b6b5d1d3ba670e3f89edef1d1394a7562251f2c/appupdatechecker2/LICENSE) |
| Apache Commons IO 2.8.0 | Apache License 2.0 and Apache Software Foundation notice | [LICENSE.txt](https://github.com/yuliskov/SharedModules/blob/5b6b5d1d3ba670e3f89edef1d1394a7562251f2c/commons-io-2.8.0/LICENSE.txt), [NOTICE.txt](https://github.com/yuliskov/SharedModules/blob/5b6b5d1d3ba670e3f89edef1d1394a7562251f2c/commons-io-2.8.0/NOTICE.txt) |
| J2V8 wrapper | Eclipse Public License 1.0 | [SharedModules/j2v8/LICENSE.txt](https://github.com/yuliskov/SharedModules/blob/5b6b5d1d3ba670e3f89edef1d1394a7562251f2c/j2v8/LICENSE.txt) |

`MediaServiceCore/SharedModules` is pinned to the same SharedModules commit and
contains a second tracked copy of its root license, AppUpdateChecker2 license,
Commons IO license and notice, and J2V8 license. The complete file index is:

```text
.reuse/dep5
LICENSE
chatkit/LICENSE
doubletapplayerview/LICENSE
exoplayer-amzn-2.10.6/LICENSE
filepicker-lib/LICENSE
fragment-1.1.0/LICENSE
leanback-1.0.0/LICENSE
slidableactivity/LICENSE.md
MediaServiceCore/LICENSE
MediaServiceCore/SharedModules/LICENSE
MediaServiceCore/SharedModules/appupdatechecker2/LICENSE
MediaServiceCore/SharedModules/commons-io-2.8.0/LICENSE.txt
MediaServiceCore/SharedModules/commons-io-2.8.0/NOTICE.txt
MediaServiceCore/SharedModules/j2v8/LICENSE.txt
SharedModules/LICENSE
SharedModules/appupdatechecker2/LICENSE
SharedModules/commons-io-2.8.0/LICENSE.txt
SharedModules/commons-io-2.8.0/NOTICE.txt
SharedModules/j2v8/LICENSE.txt
```

Recheck this list when changing submodule pins, native components, or external
dependencies. Include the corresponding license/notice material in the release
source/archive alongside the [build record](personal-build.md).
