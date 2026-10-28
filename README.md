[![CircleCI](https://circleci.com/gh/midori-browser/core.svg?style=svg)](https://circleci.com/gh/midori-browser/core)
[![Telegram](https://img.shields.io/badge/Telegram-Chat-gray.svg?style=flat&logo=telegram&colorA=5583a4&logoColor=fff)](https://t.me/midoriweb)
[![Twitter](https://img.shields.io/twitter/follow/midoriweb.svg?style=social&label=Follow)](https://twitter.com/grupoastian)
[![Donate](https://img.shields.io/badge/Stripe-Donate-gray.svg?style=flat&logo=stripe&colorA=0071bb&logoColor=fff)](https://donate.stripe.com/00g6s675Xawl6ZO9AH)
[![Patreon](https://img.shields.io/badge/PATREON-Pledge-red.svg)](https://www.patreon.com/midori_browser)
[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/T5V5269OIA)

<p align="center">
    <b>Midori</b>
    a lightweight, fast and free web browser for Android
</p>

![Midori Screenshot](https://astian.org/wp-content/uploads/2023/09/Midori-Claro-ImagoTipo.webp)

Midori is a lightweight yet powerful web browser which runs just as well on little embedded computers named for delicious pastries as it does on beefy machines with a core temperature exceeding that of planet earth. And it looks good doing that, too. Oh, and of course it's free software. And for Android

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
alt="Get it on F-Droid"
height="80">](https://f-droid.org/packages/org.midorinext.android/)
[<img src="https://play.google.com/intl/en_us/badges/images/generic/en-play-badge.png"
alt="Get it on Google Play"
height="80">](https://play.google.com/store/apps/details?id=org.midorinext.android)



[Midori Android](https://ads.fund/token/0xadff14b59f07c283470dbd50695b0df84e30471a) project is supported by ADS.FUND

**Privacy out of the box**

* Tabs Browser.
* Private browsing.
* Support extensions.

**Productivity features**

* Opens many Tabs.
* History Manage
* Download Manage.
* Bookmark.
* Dark Theme.
* Share Content.
* and much more.

## Sponsors
<div align="left">
  <a href="https://www.herodevs.com/">
    <img src="https://cdn.prod.website-files.com/62865614b39c464b76d339aa/668c0ec8bc50d24d58a40475_Logo%20Gradient%20Darkmode.svg" alt="HeroDevs Logo" width="400" height="200">
  </a>
</div>

![Midori Screenshot](https://astian.org/wp-content/uploads/2023/09/midori-devices.webp)

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=goastian/midori-android&type=Date)](https://www.star-history.com/#goastian/midori-android&Date)

## Midori Sync

Settings → Midori Sync connects this browser to the same account and encrypted
collections used by Midori Desktop. The Android client supports bookmarks,
history, passwords, payment cards, addresses and saved form values. Existing
local data is reconciled with incoming data; concurrent changes remain available
for review. Tabs and browser settings are outside the Android scope of this change.

Sign in through the Sync website with PKCE, or enter a Desktop pairing code, then
unlock the data with the account's MS2 recovery code. New accounts can create
client-held encryption after confirming that the recovery code was saved.
Disconnecting retains local browsing data.

The native cryptographic and merge code is copied from the Desktop SDK with a
hash manifest. In the combined development checkout, verify the copy with:

```sh
python3 scripts/vendor-sync-core.py --check
```

Build the isolated device-test application without replacing a regular Midori
installation:

```sh
./gradlew :app:assembleOriginalPlaystoreSyncTest \
  :app:assembleOriginalPlaystoreSyncTestAndroidTest \
  -PsyncIntegration=true -Pplaystore_version_code=2026000200
```

Its application ID is `org.midorinext.android.synctest`. Physical interoperability
tests require the Desktop checkout, a local Sync server on port 8000, and the
Desktop `scripts/test-sync-android-live.py` runner. That runner resets only this
isolated test application and uses a synthetic account.
