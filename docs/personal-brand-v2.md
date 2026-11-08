# SmartTubeY identity artwork, version 2

The personal flavor uses original artwork generated with the built-in imagegen
tool for this project. The red S/play icon is inspired by the requested TV video
app identity. The artwork does not come from the reference APK. Native header
labels continue to read `SmartTubeY` using Android text.

| Source filename | Android resource | Original dimensions | SHA-256 |
| --- | --- | --- | --- |
| `smarttubey-logo-v2.png` | `mipmap/app_icon_v2`, with `app_logo_v2` as an alias | 1536 × 1024 | `a979427c6f1416f30c35a97ec5c77a0134f64462a82e3f2b448abcc6a0f721df` |
| `smarttubey-tv-banner-v2.png` | `mipmap/app_banner_v2` | 1672 × 941 | `4f7e97ae89dd9798c6f8e379b75b731bbc81fa9cbfd735f32dc826efe4a9f6eb` |

Both PNGs are copied byte for byte into `src/stpersonal/res/mipmap-nodpi`.
Their pixels, alpha, dimensions and compression are unchanged. Android performs
normal drawable sizing. Header ImageViews use `FIT_CENTER`, preserve the icon's
aspect ratio and add native title text. The banner contains the generated
SmartTubeY lockup and is used only for the TV launcher banner.

The personal manifest points the main and alternative launcher icons, application
logo and TV banner at the v2 resources. The original six personal PNGs remain
unchanged as fallback assets: `app_icon`, `app_icon_alt`, `app_logo`,
`app_logo_semi_red`, `app_logo_semi_grey` and `app_banner`. The base SmartTube
flavors and their manifests/resources retain their existing identity.

The personal SplashActivity inherits the original Browse theme. Android 12 and
later use its native starting-window icon API with the new icon and dark
background; older Android versions retain the inherited startup behavior with
the new application icon. There is no custom launcher Drawable implementation,
new startup delay or replacement splash activity.

Browse, Search and the account chooser share an icon plus native text header.
The new helper owns no focus or navigation behavior. Build, merged-manifest and
TV checks must confirm the launcher/banner, startup and each header after the
branding commit is integrated. These checks are separate from the player and
settings functionality regressions.
