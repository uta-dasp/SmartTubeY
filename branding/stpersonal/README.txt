SmartTubeY personal branding

These original red/white TV/play shapes identify the personal fork separately from
the official YouTube application and upstream SmartTube. No upstream or official
application image is used. The SmartTubeY wordmark is rendered as outlined text;
no font file is redistributed.

Generate-Branding.ps1 creates both the editable SVG path sources in this directory
and PNG overlays in smarttubetv/src/stpersonal/res/mipmap-nodpi. Run it using Windows
PowerShell, which supplies System.Drawing and the installed Arial Bold typeface.
No additional packages are needed. PNGs are committed, so normal Android builds
do not run this generator or depend on Windows fonts.

The manifest's app_banner is exactly 320x180 pixels. app_icon and app_icon_alt are
320x320. app_logo and both theme logo variants are transparent 320x180 images,
matching the inherited resource dimensions and replacing visible legacy branding.
The intentionally blank app_logo_none option is preserved. The existing About
presenters use the already-overridden app_name string and contain no separate logo.
Only stpersonal resolves these images; no upstream flavor resources are modified.
