<h1 align="center">Jellyfin for Android TV</h1>
<h3 align="center">Part of the <a href="https://jellyfin.org">Jellyfin Project</a></h3>

---

<p align="center">
<img alt="Logo banner" src="https://raw.githubusercontent.com/jellyfin/jellyfin-ux/master/branding/SVG/banner-logo-solid.svg?sanitize=true"/>
<br/><br/>
<a href="https://github.com/jellyfin/jellyfin-androidtv">
<img alt="GPL 2.0 License" src="https://img.shields.io/github/license/jellyfin/jellyfin-androidtv.svg"/>
</a>
<a href="https://github.com/jellyfin/jellyfin-androidtv/releases">
<img alt="Current Release" src="https://img.shields.io/github/release/jellyfin/jellyfin-androidtv.svg"/>
</a>
<a href="https://translate.jellyfin.org/projects/jellyfin-android/jellyfin-androidtv/">
<img alt="Translation Status" src="https://translate.jellyfin.org/widgets/jellyfin-android/-/jellyfin-androidtv/svg-badge.svg"/>
</a>
<br/>
<a href="https://opencollective.com/jellyfin">
<img alt="Donate" src="https://img.shields.io/opencollective/all/jellyfin.svg?label=backers"/>
</a>
<a href="https://features.jellyfin.org">
<img alt="Feature Requests" src="https://img.shields.io/badge/fider-vote%20on%20features-success.svg"/>
</a>
<a href="https://matrix.to/#/+jellyfin:matrix.org">
<img alt="Chat on Matrix" src="https://img.shields.io/matrix/jellyfin:matrix.org.svg?logo=matrix"/>
</a>
<br/>
<a href="https://play.google.com/store/apps/details?id=org.jellyfin.androidtv">
<img width="153" alt="Jellyfin on Google Play" src="https://jellyfin.org/images/store-icons/google-play.png"/>
</a>
<a href="https://www.amazon.com/gp/aw/d/B07TX7Z725">
<img width="153" alt="Jellyfin on Amazon Appstore" src="https://jellyfin.org/images/store-icons/amazon.png"/>
</a>
<a href="https://f-droid.org/en/packages/org.jellyfin.androidtv/">
<img width="153" alt="Jellyfin on F-Droid" src="https://jellyfin.org/images/store-icons/fdroid.png"/>
</a>
<br/>
<a href="https://repo.jellyfin.org/releases/client/androidtv/">Download archive</a>
</p>

Jellyfin for Android TV is a Jellyfin client for Android TV, Nvidia Shield, and Amazon Fire TV devices. We welcome all contributions and pull
requests! If you have a larger feature in mind please open an issue so we can discuss the implementation before you start. 

## Building

The app uses Gradle and requires the Android SDK. We recommend using Android Studio, which includes all required dependencies, for
development and building. For manual building without Android Studio make sure a compatible JDK and Android SDK are installed and in your
PATH, then use the Gradle wrapper (`./gradlew`) to build the project with the `assembleDebug` Gradle task to generate an apk file:

```shell
./gradlew assembleDebug
```

The task will create an APK file in the `/app/build/outputs/apk/debug` directory. This APK file uses a different app-id from our stable
builds and can be manually installed to your device.

## Branching

The `master` branch is the primary development branch and the target for all pull requests. It is **unstable** and may contain breaking
changes or unresolved bugs. For production deployments and forks, always use the latest `release-x.y.z` branch. Do not base production work
or long-lived forks on `master`.

Release branches are created at the start of a beta cycle and are kept up to date with each published release. Maintainers will cherry-pick
selected changes into release branches as needed for backports. These branches are reused for subsequent patch releases.

## Translating

Translations can be improved very easily from our [Weblate](https://translate.jellyfin.org/projects/jellyfin-android/jellyfin-androidtv)
instance. Look through the following graphic to see if your native language could use some work! We cannot accept changes to translation
files via pull requests.

<p align="center">
<a href="https://translate.jellyfin.org/engage/jellyfin-android/">
<img alt="Detailed Translation Status" src="https://translate.jellyfin.org/widgets/jellyfin-android/-/jellyfin-androidtv/multi-auto.svg"/>
</a>
</p>

---

## 🎬 Custom Themes (v1.1 fork by bzl1982)

This fork adds two custom themes inspired by KODI's Titan Bingie Mod (NETFLIX SINK) and Arctic Fuse 2 (北极引线) design languages, for an authentic Netflix / Apple TV Infuse launcher feel on Android TV.

### Available themes

| Theme ID | Color palette | Inspiration |
|---|---|---|
| `Theme.Jellyfin.Netflix` | Black `#141414` + Red `#E50914` + Match Green `#46D369` | KODI Titan Bingie Mod "NETFLIX SINK" |
| `Theme.Jellyfin.Infuse` | Dark `#1A1A1E` + Orange `#FF6B1A` + Amber `#FFB74D` | KODI Arctic Fuse 2 + Apple TV Infuse |

### v1.1 design tokens (added 2026-08-04)

Each theme now exposes four custom tokens via `?attr/` for future Compose layouts:

| Token | Netflix | Infuse | Purpose |
|---|---|---|---|
| `matchGreen` | `#46D369` | `#FFB74D` | "70% match" badge / focus highlight |
| `heroOverlay` | `#99000000` | `#99000000` | Hero region left text background (60% black) |
| `hdrLabelBg` | `#33FFFFFF` | `#33FFFFFF` | 4K / HDR label background (20% white) |
| `brandAnchor` | `#E50914` | `#FF6B1A` | Bottom-right brand logo color |

### Roadmap (v1.2)

- **30/70 Hero layout** — 30% left info region (poster title + match score + meta) + 70% right backdrop image
- **Three-piece focus state** — 2px primary stroke + elevation shadow + scale(1.04) micro-zoom
- **Per-context focus** — Card focus (color stroke) vs side menu (color icon + bold text) vs settings (white frame)
- **Compose Hero composables** consuming the new theme tokens

### Install

Settings → Display → Theme → select `Netflix (Black & Red)` or `Infuse (Orange)`.

### Credits

- KODI skin authors: jurialmunkey (Arctic Fuse), marcelveldez (Titan Bingie Mod)
- Jellyfin Android TV maintainers for the upstream Compose base
- Inspired by Netflix launcher UI and Apple TV Infuse

---

