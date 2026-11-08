# Build and package SmartTubeY on Windows

SmartTubeY's `stpersonal` flavor is an independently installable Android TV app
with application ID `org.smarttubey.personal`. Its YouTube TV presentation is
under active development and visual/interaction parity remains in progress.
The upstream `stbeta`, `ststable`, and `stfdroid` flavors remain in the project.
The personal UI continues to use SmartTube's service and playback modules.

The initial target is an Android 12, ARMv7 TV with a 1920 x 1080 Android UI
coordinate space. A 4K panel/video stream and the UI's render resolution are
separate measurements. Build the ARMv7 APK for that device.

## Record the source and toolchain

Clone with submodules, then record the exact revisions used for each artifact:

```powershell
git clone --recurse-submodules --branch smarttubey/personal-ui https://github.com/uta-dasp/SmartTubeY.git
Set-Location SmartTubeY
git rev-parse HEAD
git status --short
git submodule status --recursive
```

Keep the recorded submodule pins. The initial inspected service revisions are:

| Submodule | Commit |
| --- | --- |
| `MediaServiceCore` | `db543d4d5d639cbf3084e3870b0dac6e4e2524a1` |
| `SharedModules` | `5b6b5d1d3ba670e3f89edef1d1394a7562251f2c` |
| `MediaServiceCore/SharedModules` | `5b6b5d1d3ba670e3f89edef1d1394a7562251f2c` |

`settings.gradle` prefers sibling `../SharedModules` or `../MediaServiceCore`
directories when present. Use an isolated checkout without those sibling
overrides to build the recorded submodules.

| Component | Verified configuration |
| --- | --- |
| Application build JDK | Temurin 17.0.21+9, full JDK |
| Gradle | 7.5 binary distribution |
| Android Gradle Plugin | 7.4.2 |
| Kotlin plugin | 1.8.10 |
| Android SDK platform | API 34, standard revision 3 |
| Android Build Tools | 30.0.3 |
| Android Command-line Tools | 11.0 |
| Platform Tools used for deployment | 37.0.1 |
| Pinned Robolectric test runtime | Java 11, tested with Temurin 11.0.32.1+1 |
| Declared NDK | 21.0.6113669, optional for stripping the existing native libraries |

The current upstream CI uses JDK 17; the upstream README also contains an older
JDK recommendation. The application builds documented here passed with JDK 17.
The pinned Robolectric 4.6.1 instrumenter rejects Java 17 platform classes, so
the focused unit test uses a separate Java 11 test JVM.

Download tools from their official distributors and verify published hashes.
The Gradle 7.5 binary ZIP SHA-256 used here is
`cb87f222c5585bd46838ad4db78463a5c5f3d336e5e2b98dc7c0c586527351c2`.
Tool paths below are examples for an adjacent, private `toolchain` directory;
existing installations can supply the same pinned components.

## Use process-local paths

Run from the repository root and substitute your existing tool locations:

```powershell
$repo = (Get-Location).Path
$toolRoot = [IO.Path]::GetFullPath((Join-Path $repo '../toolchain'))
$env:JAVA_HOME = Join-Path $toolRoot 'jdk-17'
$env:ANDROID_HOME = Join-Path $toolRoot 'android-sdk'
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:ANDROID_USER_HOME = Join-Path $toolRoot 'android-user-home'
$env:GRADLE_USER_HOME = Join-Path $toolRoot 'gradle-user-home'
$gradle = Join-Path $toolRoot 'gradle-7.5/bin/gradle.bat'
$buildTools = Join-Path $env:ANDROID_HOME 'build-tools/30.0.3'
& $gradle --version
```

Keep `ANDROID_USER_HOME` stable: its existing `debug.keystore` supplies the
certificate used by the installed development builds. A fresh user directory
can cause Android tooling to create a different development key.

The checked-in Windows `gradlew.bat` includes upstream cleanup of the checkout's
`.gradle` directory. Calling the verified Gradle 7.5 distribution directly keeps
that incremental project state between iterations. Both routes use Gradle 7.5.

## Build and check the personal variant

Quote the dotted Gradle property so PowerShell passes it as one argument:

```powershell
& $gradle '--no-daemon' '-Pandroid.builder.sdkDownload=false' `
    ':smarttubetv:assembleStpersonalDebug' '--console' 'plain'
& $gradle '--no-daemon' '-Pandroid.builder.sdkDownload=false' `
    ':smarttubetv:lintStpersonalDebug' '--console' 'plain'
```

Check `$LASTEXITCODE` after each command. SDK acquisition is disabled in these
commands to keep package installation explicit. Without the declared NDK,
Gradle successfully packages the already supplied native libraries and reports
that their debug symbols were not stripped.

The ARMv7 output is:

```text
smarttubetv/build/outputs/apk/stpersonal/debug/SmartTubeY_personal_32.72_armeabi-v7a.apk
```

The build also emits other configured ABI variants. Use the ABI-specific APK
matching the device; the initial target supports `armeabi-v7a`.

The known standalone-Rows regression has two focused Robolectric tests:

```powershell
$env:SMARTTUBEY_TEST_JAVA_HOME = Join-Path $toolRoot 'jdk-11'
& $gradle '--no-daemon' '-Pandroid.builder.sdkDownload=false' `
    '-I' './tools/java11-unit-tests.init.gradle' `
    ':smarttubetv:testStpersonalDebugUnitTest' `
    '--tests' 'androidx.leanback.app.StandaloneRowsFragmentTest' '--console' 'plain'
```

The init script selects Java 11 for the test process while Gradle and app
compilation stay on JDK 17. Its runtime Maven cache uses the adjacent
`toolchain/maven-local` directory. The test checks attaching and selecting rows
without a Browse host. Device checks still cover focus, navigation, playback,
settings, accounts, network recovery, and the captured visual states.

## Preserve signing identity for updates

The existing installed personal app must keep the same application ID and
signer certificate across debug and release updates. Retain the current private
keystore and record the certificate SHA-256 digest from the already verified
personal APK:

```powershell
$currentApk = 'PATH_TO_VERIFIED_PERSONAL_APK'
& (Join-Path $buildTools 'apksigner.bat') verify --verbose --print-certs $currentApk
```

Arrange a protected copy of the existing keystore outside the source checkout
before configuring a release. The key-migration/backup step should preserve its
certificate. Keep keystores, passwords, account data, extracted reference APKs,
and decompiled reference output outside Git.

The existing build reads root `keystore.properties` when present. It then uses
that signing configuration for both release and debug variants. The ignored
file has these property names, with existing private values supplied locally:

```properties
storeFile=C:/private/SmartTubeY/existing-personal.keystore
storePassword=EXISTING_STORE_PASSWORD
keyAlias=EXISTING_KEY_ALIAS
keyPassword=EXISTING_KEY_PASSWORD
```

Use forward slashes in the Windows properties path. This document supplies no
passwords or new-key generation step. Without that file the current release
configuration has no signing key, so its release output needs signing before
installation. The protected-key migration and final release upgrade test remain
separate from the development APK checks.

## Produce and verify the final package

After visual and functional QA, clean the application module to remove its
incremental ZIP slack. This preserves completed library builds and cached
dependencies. Preserve the known-good rollback APK and its metadata first.

The first release compile on the 8 GB Windows host exhausted system resources
with the upstream 3 GB heap and default parallelism. The following profile
successfully completed the signed release build. Keep release lint in a separate
invocation so its analysis starts in a fresh Gradle process:

```powershell
$releaseMemoryArgs = @(
    '--max-workers=1',
    '-Dorg.gradle.jvmargs=-Xmx1536m -Dfile.encoding=UTF-8',
    '-Pkotlin.compiler.execution.strategy=in-process'
)
& $gradle @releaseMemoryArgs '--no-daemon' '-Pandroid.builder.sdkDownload=false' `
    ':smarttubetv:clean' ':smarttubetv:assembleStpersonalRelease' '--console' 'plain'
& $gradle @releaseMemoryArgs '--no-daemon' '-Pandroid.builder.sdkDownload=false' `
    ':smarttubetv:lintStpersonalRelease' '--console' 'plain'
```

If Windows reports insufficient system resources, let that build finish and
wait for the host to recover. Retry the assemble task with this profile without
another clean; preserve the successfully completed library work and dependency
caches. These options apply only to the current command and do not change the
upstream Gradle properties or global Java settings.

Record the Git and submodule revisions, tool versions, build commands, APK
SHA-256, and signer-certificate digest. Verify the finished ARMv7 APK:

```powershell
$releaseApk = 'PATH_TO_FINAL_SIGNED_ARMV7_APK'
& (Join-Path $buildTools 'zipalign.exe') '-c' '-v' '4' $releaseApk
& (Join-Path $buildTools 'apksigner.bat') verify --verbose --print-certs $releaseApk
& (Join-Path $buildTools 'aapt.exe') dump badging $releaseApk
Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256
```

Confirm application ID `org.smarttubey.personal`, the intended version, ARMv7
native code, the retained certificate, and release debuggability settings.
The personal `update_urls` array is empty; APK updates are manually sideloaded.
Use an in-place update and exercise it on the TV with settings preserved:

```powershell
$adb = 'PATH_TO_ADB_EXE'
$tvSerial = 'YOUR_TV_SERIAL'
& $adb '-s' $tvSerial install '-r' $releaseApk
```

Keep the previous working personal APK for rollback. Official YouTube and the
upstream SmartTube package identities remain distinct.

These instructions reproduce the source/toolchain configuration. Current
`BuildConfig.TIMESTAMP` uses `System.currentTimeMillis()`, and incremental APK
packaging can retain unused ZIP space, so development APK hashes vary. Claims of
byte-for-byte reproducibility require a fixed build timestamp, controlled
dependency artifacts and ZIP inputs, and comparison of two independent clean
builds. This has not been established by the development build checks.

Preserve the [tracked third-party notices](personal-third-party-notices.md)
with the corresponding source components.
