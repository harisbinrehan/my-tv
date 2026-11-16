# My Tv: full project handover

You are picking up an Android app called **My Tv**: a phone remote for one PTCL Shoq TV box. The person
giving you this owns the box and will use the app. Read this whole file before changing anything. It holds
everything learned while building the app, including behaviour of the box that took many live tests to find.

How to use this file: open the source folder in Claude Code (this file is the folder's `CLAUDE.md`, which
Claude Code reads automatically), or paste it into a chat together with the source archive and the APK.

## 1. What the app is

- An Android app that controls the PTCL Shoq TV box over home Wi-Fi with Google's **Android TV Remote
  protocol v2**, the protocol the Google TV phone app uses. No ADB, no developer mode, no root on the box.
- It talks to **one box only**: the box's TLS certificate is pinned, and any other device is refused.
- Package `com.shoqremote.app`, app name **My Tv**, current version **5.0 (versionCode 5)**.
- Stack: Kotlin, Jetpack Compose (Material 3), minSdk 26, targetSdk 35, no third-party libraries beyond
  AndroidX and kotlinx-coroutines. The protocol is implemented by hand in this repo.

## 2. What the owner asked for (product direction)

The goal, in the owner's words: when an app is open on the TV, control it entirely from the phone and never
pick up the physical remote. Typing must work properly; it was the main complaint.

Requests made over the build, all implemented in version 3 unless noted:

1. Connect only to this Shoq box; run on the phone.
2. Name "My Tv"; a better icon; a quick settings tile in the notification panel.
3. A permanent notification while the TV is on, with the app on the TV and live controls.
4. Four sections: **Remote** (fixed, no scrolling), **Apps**, **TV** (live channels), **Now** (the running app).
5. Apps as tiles with official icons; favourites at the top of the Apps section (not on Remote).
6. Working TV Settings and Search buttons; Mute that reads Unmute when muted.
7. Speak instead of typing; voice search.
8. TV section: list all channels by category (Sports, News and so on), search them, pin and arrange them.
   Partly done: the list starts empty because no channel list could be obtained (see section 9).

## 3. Current state (version 16: version 5, plus section 9 item 6, the activity log in section 8, and in-app updates in section 7)

### Version 16 (2026-11-16)

- Home screen widgets (`Widgets.kt`, layouts `widget_*.xml`): TV power (1x1), Now playing (4x1: app, Mute,
  Play/Pause, Power), Remote (3x4: Back, arrows, OK, Home, volume, Mute, Play/Pause, Power). Taps go to
  `TvLinkService` as foreground-service intents; a tap before the connection is ready waits up to 5 s. The service
  refreshes the widgets when what they show changes. With notification controls off, a widget tap starts the
  service and it stops after a quiet minute. Menu: "Add a widget" (pins through Android's own dialog).
  Tested on the emulator against the box: Mute from the widget muted and unmuted the TV, also after the app's
  process was killed (Android restarted the service). A force-stopped app's widgets are disabled by Android until
  the app is opened again.
- Rename on app tiles; the no-Play-page hint (see section 9, item 7).

### Version 15 (owner's requests, 2026-11-16)

- Remote: CH up/down removed; the sound row is Mute plus a wide VOL. Rows are laid out with explicit gaps and the
  arrows take only the height left (v14 under-counted one gap and squeezed Back/Voice/Home onto the media bar on
  the owner's S23 Ultra). Checked at 1080x2316, 450 dpi, font scale 1.15.
- Opening an app (Apps) or a channel (TV) while connected switches to the remote.
- Typing panel: no Paste and no recent-search chips; recent searches are no longer stored, and the saved list is
  cleared on first start (`Storage.upgradeFromBefore15`).
- Notification player card: no app artwork. Apps: Netflix and Play Store added (once, to existing lists), Settings
  tile removed (TV settings is in the menu). Play Store opens via https://play.google.com/store/apps.
- NetMirror is installed on the box but is not on the Play Store, so the only launch route the box allows (the Play
  Store page) can't open it. Not added.

### Version 14 (owner's layout requests, 2026-11-16)

- Bottom bar: Apps, Remote, TV (the remote is a normal tab again). Type is a floating button (bottom right) on
  every page, hidden while the typing panel is open; the remote page leaves its slot free beside the media bar.
- Remote page, top to bottom: a "Now on TV" card (app icon, name, volume bar) when the arrows still get 250 dp;
  Mute with wide VOL and CH; the arrows and OK; Back, Voice, Home; Rewind / Play-pause / Forward as one bar.
- Mute icons show the state everywhere (crossed speaker while muted), notification included; labels say the action.

### Versions 12 and 13 (owner's layout requests, 2026-11-16)

- The remote button is a floating button (bottom right, above the bar) on Apps and TV only; the header has power
  and the menu. Lists keep 96 dp at the bottom so it never covers a row.
- The remote page is laid out for one thumb: controls anchored to the bottom (Voice, Mute, Rewind, Forward; wide
  VOL and CH pills; arrows and OK; Back, Play/Pause, Home lowest). The Menu (≡) key was removed at the owner's
  request; TV settings moved to the ⋮ menu (shown while connected). Type lives only in the bottom bar.
- Updates show as a slim strip (Update, ×), which opens a sheet with the notes and Later or Update now; while
  updating the strip shows progress. Cancelling Android's prompt brings the strip back.
- The TV notification's player card has Power (Turn TV off, via `link.power()`) where Back was; with the TV off
  it shows Turn on as before.

### Version 11 (owner's layout requests, 2026-11-16)

- Navigation: the remote is the home page, opened from a button left of the power button in the header; the
  bottom bar is Apps, Type, TV; Back returns to the remote. The Now page and the big Turn TV on bar are gone.
- TV: channel logos (`ChannelLogos.kt` + `assets/channel_logos.json`, built by `tools/channel_logos.py` from the
  public iptv-org database: 180 of the 199 published Shoq channel names, Pakistani channels preferred), star
  favourites on top, search by name, and a typed number offers "Watch channel N".
- Still owed: the box's own channel numbers and installed apps. PTCL publishes no numbered line-up and the box
  reports neither. Developer mode on the box is locked by PTCL (tapping Build shows a QR code holding the box
  MAC and an RSA-encrypted challenge for PTCL/ZTE technicians); never try to get around it. Plan: the owner props
  the phone's camera at the TV with wireless debugging on; the Mac steps through channels (CLI) and reads each
  channel banner from `adb exec-out screencap`, then seeds the list. Photos are the fallback.

### Version 5 (typing first, premium dark design), tested 2026-11-15
- **Typing panel** (`ui/TypePanel.kt`), one tap from anywhere: the Type tab in the middle of the bottom bar, the
  Type button on Remote, or the type bar on Now. It opens with the phone keyboard up and shows which app it types
  into. Paste (inserts the clipboard at the cursor, line breaks become spaces), voice typing (phone speech-to-text),
  hold-to-repeat Backspace (a lone Backspace on an empty field still sends DEL once, never repeated), Clear (only
  what the app typed), Search (YouTube: results link; otherwise Enter), recent searches as chips filtered by what's
  typed. Anything that looks private (`@`, `://`, `www.`, letters+digits without spaces, over 60 chars) is never kept.
- The status line is real feedback: "On your TV" only when `TvLink.onTv` (what the completed sends put in the TV
  box) equals the field; "Sending..." otherwise; "Not connected" with Search disabled when offline.
- Typing engine: `typeLive` stores the desired text and each queued sync brings the TV to the latest value, so a
  burst of keystrokes can't reorder or double. Enter/Search/forget reset inside the send queue (no race with queued
  keystrokes). The mirror survives reconnects (typing offline is caught up on reconnect, nothing typed twice) and is
  reset when the TV switches app. Backspaces count code points (one per emoji). The field keeps the keyboard's
  composing region, so autocorrect works and keyboards don't repeat letters.
- If the box ever sends `remote_ime_show_request` (22), the panel opens itself. This box hasn't been seen to send it.
- Remote: arrows and volume repeat every 90 ms while held (after 380 ms); other keys keep long-press semantics.
  Haptic tick on each repeat. Keys have hairline borders and a springy press.
- Design: neutral charcoal palette (`Palette` in `ui/Theme.kt`), one soft indigo accent used for typing and
  selection, neutral OK with a thin indigo ring, Type tab drawn with the same geometry as a selected tab.
- Power on is POWER when the box reports standby (it ignores WAKEUP, tested), WAKEUP only when the state is unknown.

Verified on the real box from an emulator running the app (paired with the Mac test identity): connect, status,
typing with symbols ("On your TV" confirmed), hold-to-delete, Search YouTube, recents, turn off then Turn TV on
(standby to on in about a second), opening YouTube from Apps, hold VOL+/VOL- repeat (6 to 10 and back), Wi-Fi drop
and automatic reconnect with typing caught up in about 6 s. Key round trip about 110 ms. Not seen by anyone at the
TV: the letters themselves on screen (the box never echoes text boxes), and whether the TV set follows the box's
power over HDMI-CEC.


### Version 4 (this owner's box, permanent connection, power)
- Superseded detail: v4 tried WAKEUP first; the box ignores it, so v5 sends POWER when the box reports standby.
- Re-locked to this owner's box (section 4). Version 3 was pinned to the box it was built with and showed
  "Can't find the Shoq box" here, because every certificate mismatch was reported as "not found". A mismatch
  now says "A different Shoq box answered (certificate xxxx...)".
- The lock also accepts a certificate whose CN is `atvremote/<BOX_ID>`, so the app keeps connecting if the box's
  remote service makes itself a new certificate (this box's certificate was dated the day the app arrived).
- Power is one smart button (top bar, green to turn on, red to turn off). Turning on: if the box is
  unreachable, send Wake-on-LAN to both MACs and reconnect at once (wait up to 25 s); then KEYCODE_WAKEUP (224),
  which cannot turn a running box off; KEYCODE_POWER only as a fallback when the box itself reported standby.
  It turns off (KEYCODE_POWER) only when the box reports it is on.
- While the TV is off or unreachable, a big **Turn TV on** bar replaces Settings, Search and Mute on Remote, and
  the notification shows Turn on (also when unreachable, not only in standby).
- Permanent connection: reconnect waits are cut short when Wi-Fi returns, the app opens or Turn on is pressed;
  retries pause while the phone is off Wi-Fi (rechecked every 60 s); the connection is dropped at once when Wi-Fi
  goes; retries keep the OFFLINE status so the notification doesn't flicker. On first connection the app asks
  Android once to exempt it from battery optimisation (Samsung otherwise sleeps it); menu: Always stay connected.
- Not verified on the box yet: WAKEUP and Wake-on-LAN waking this box from standby, and whether the TV set follows
  over HDMI-CEC. Test with the CLI (`key:WAKEUP`, `key:SLEEP`) with the owner watching.

### Earlier (version 3)

### Remote (fixed layout, sized to the screen)
- Settings (opens the box's Settings), Search (voice search through the TV's Google Assistant),
  Mute or Unmute (follows the box's mute state).
- D-pad with OK, Back, Home, Menu, volume and channel rockers, play or pause, rewind, fast forward.
- Holding a key sends a long press (START_LONG then END_LONG on release), like the physical remote.
- The phone's own volume buttons drive the box while the app is open. Power is in the top bar.

### Apps
- Favourites row at the top (hold an app, Add to favourites), then a grid of tiles with each app's official
  Play Store name and icon. Settings has a built-in gear tile.
- The box cannot list its installed apps (section 9), so an app joins the grid the first time the box reports
  it on screen. With notification controls on, that includes apps opened with the physical remote.

### TV
- Go to channel: type a number and Go. CH up and down buttons.
- User-maintained channel list (number, name, category from `CHANNEL_CATEGORIES`), search by name or number,
  category chips, pinned channels on top with up and down arrows for order, tap to watch, hold to edit.

### Now
- The app on the TV with its icon, plus Back, Home, Play or pause, and Menu (CH up and down on live TV).
- Touch pad: swipe to move (one arrow press per 44 dp of travel, dominant axis), tap for OK.
- Live typing card: each change in the phone's text box is mirrored into the TV's focused text box.
  The mic in the box is phone speech-to-text. Enter submits. When YouTube is on screen the card shows
  **Search YouTube**, which opens YouTube's results directly.
- Ask Google on the TV (voice search), and a volume card with level and Mute or Unmute.

### Notification and other pieces
- `TvLinkService`: a `connectedDevice` foreground service with a MediaSession, so Android 13+ draws a player
  card. It shows the app on the TV (name and official icon) with rewind, play or pause, fast forward,
  mute or unmute and back; a power button when the TV is in standby. On by default; toggle in the top bar
  menu (Notification controls). Restarts after phone reboot (`BootReceiver`).
- Quick settings tile (`RemoteTileService`); on Android 13+ the menu item asks Android to add it.
- First run: Start pairing, the TV shows a 6-character code, type it. The key stays in app-private storage,
  excluded from backups.

### Verified on the real box (owner watching the TV)
Pairing, status, keys, Settings path, opening apps (YouTube by link, ARY Plus by Play Store page),
voice search ("Open YouTube" opened YouTube), typing (insert and Backspace in YouTube's search box),
YouTube search link, live TV channel switching (channels 5 and 12).

### Not verified yet
The version 3 screens on a phone (no emulator was available), the notification and player card, voice search
from the phone's microphone (the format was proven with audio streamed from a Mac), the boot receiver,
and the quick settings tile on the owner's Android version.

## 4. The box

| Item | Value |
|---|---|
| Device | PTCL Shoq TV box, ZTE B866V2F, Android TV 12 |
| Remote service | `com.google.android.tv.remote.service` 7.00.999235495, supported features mask 639 |
| Address | 192.168.1.5 on the owner's LAN (DHCP). The app finds it again over mDNS `_androidtvremote2._tcp` |
| TLS certificate SHA-256 | `e0b20037ad14ad6cd02cd70fae8111e89abc15e2802b874e94a9e50ca73ab202` (same on 6466 and 6467; read 2026-11-15) |
| Certificate subject | `CN=atvremote/54:AE:BC:9A:69:82`, dnQualifier `PTCLBox_B866V2F/PTCLBox_B866V2F/B866V2F`; `ShoqBox.BOX_ID` is that MAC |
| MACs | `54:AE:BC:9A:69:81` (what the router sees at 192.168.1.5), `54:AE:BC:9A:69:82` (in the certificate); both get Wake-on-LAN |
| Earlier box | Version 3 was pinned to `44f895316f0b145606ce3d02367575f6746f5c10960ca1c8f5c1f26aa561d865`, a different box |
| Open ports | 6466 remote, 6467 pairing, 8008/8009/8443 Google Cast, 8547 ZTE TR-069 (ISP management, digest auth, not ours), 9000 unknown raw TCP, 38149, 45049 |

Packages seen on screen:

| Package | What it is |
|---|---|
| `com.launcher.ptcl` | PTCL home screen. Its first screen is the live TV preview |
| `com.zte.CTBrowser` | ZTE live TV player (full screen), and also the box's web browser |
| `com.android.tv.settings` | Settings |
| `com.android.vending` | Play Store |
| `com.google.android.katniss` | Google Assistant |
| `com.release.aryzaptv` | ARY Plus |
| `com.google.android.youtube.tv` | YouTube |

## 5. How control works on this box (all verified)

Protocol basics (`atvremote` module): pairing on 6467 and control on 6466, both TLS with a self-signed client
certificate, each protobuf message prefixed by a varint length. Pairing sends PairingRequest, Options,
Configuration (hex encoding, 6 symbols, input role), then the secret: SHA-256 over client modulus, client
exponent, server modulus, server exponent (big-endian, no sign byte) and the bytes of code characters 3 to 6.
The first byte of that hash must equal code characters 1 to 2, so a typo is caught before sending. Field
numbers follow `remotemessage.proto` and `polo.proto` from tronikos/androidtvremote2. The box pings about every
5 seconds; the client treats 16 seconds of silence as a dead connection.

| Need | What works on this box |
|---|---|
| Which app is on screen | Box sends `remote_ime_key_inject` (field 20), `app_info` (1), `app_package` (12) on every app switch. Nothing else about text boxes |
| Open YouTube | App link `https://www.youtube.com` |
| Open any Play Store app | Link `https://play.google.com/store/apps/details?id=<package>`, wait for `com.android.vending` (about 1.3 s), wait 1.5 s, press OK on the focused Open button; the app appears within about 0.5 s. Only used for apps the box has shown running |
| What does not open apps | `market://launch?id=` and `intent:` links are ignored; `&launch=true` only shows the page. An https link no app claims opens the ZTE browser |
| Never do | Send a guessed custom-scheme link (`aryzap://` was tested): the box drops the remote connection |
| TV Settings | KEYCODE_SETTINGS (176) is ignored. Use Home, wait 1.5 s, Up, OK; confirm `com.android.tv.settings` |
| Live TV and channels | KEYCODE_TV (170) and digits on the PTCL home do nothing. Use Home, wait 1.5 s, OK (opens `com.zte.CTBrowser`), wait about 1.2 s, then digit keys (KEYCODE_0 to 9 are 7 to 16) about 250 ms apart. No OK after the digits |
| Voice search | Include VOICE (8) in the features reply. Press KEYCODE_SEARCH; the box sends `remote_voice_begin` (field 30) with a session id within 2 s; echo it back; stream `remote_voice_payload` (31: session, samples) of **8 kHz, 16-bit, mono, little-endian PCM** in real time, about 16 KiB per piece (pieces under 8 KiB are zero-padded), then `remote_voice_end` (32). 16 kHz audio is not understood. The Assistant can act before the stream ends |
| Typing | The box never reports text boxes, even with one focused. `remote_ime_batch_edit` (21) with ime and field counters 0, `edit_info.insert = 1` and `value = text` **inserts** the text at the cursor (verified in YouTube). KEYCODE_DEL (67) deletes one character. Letter keycodes do not type. On an empty YouTube search box, DEL acts as Back and leaves search, so only ever delete characters the app typed (`TextMirror`) |
| YouTube search | `https://www.youtube.com/results?search_query=<url-encoded text>` shows results directly |
| Official app names and icons | The app's Play Store page: `og:title` minus " - Apps on Google Play" and minus " for Android TV"; `og:image` with everything after `=` replaced by `=s256` |
| Volume | Box reports level, max (15) and muted. KEYCODE_VOLUME_MUTE (164) toggles |
| Turn on without pairing | **Not possible on this box** (all tested 2026-11-15/16 from a standby the owner chose; the box stayed off each time). Cast on 8009 (TLS, any certificate, no pairing; `CastWake.kt`, `cli castwake`): `GET_STATUS` gives `isStandBy` and `isActiveInput`, and LAUNCH from standby is accepted (`USER_ALLOWED`), but the Default Media Receiver (`CC1AD845`, even held 10 s) and YouTube (`233637DE`) both run hidden while the box stays asleep. It only woke the box twice from a standby a few seconds old. A pairing request in standby is accepted but doesn't wake it. Wake-on-LAN does nothing. ADB (5555) is closed; 9000 answers TCP but not HTTP; DIAL (8008) has no YouTube. Only a paired phone's POWER (or the box remote) wakes it |

## 6. Code map

```
atvremote/   protocol in plain Kotlin (JVM), shared by the app and the Mac tool
  Proto.kt          protobuf writer and reader, varint framing
  Messages.kt       pairing and remote message builders, feature flags
  ClientIdentity.kt RSA key and self-signed X.509 certificate (hand-written DER)
  Tls.kt            openTls, PinnedTrustManager (the one-box lock), WrongDeviceException
  Pairing.kt        PairingSession, pairingSecret
  RemoteClient.kt   connection, reader thread, configure and ping replies, keys, links, text, voice
  TextMirror.kt     live typing: Backspace back to where texts differ, then insert the rest
  ShoqBox.kt        pinned certificate, default host, ports, mDNS type; Keys; Direction
  test/ProtocolTest.kt  12 tests, including a local TLS server for the pin and client certificate
cli/          Mac test tool (pin, pair, status, key, run, watch, voice)
app/          the Android app
  MyTvApp.kt        Application that holds the single TvLink
  TvLink.kt         the shared connection, UiState, and every command (apps, Settings, channels, voice, typing, pairing)
  TvLinkService.kt  permanent notification and media session
  BootReceiver.kt, RemoteTileService.kt, MainActivity.kt
  RemoteViewModel.kt  thin facade the screens call
  Storage.kt        prefs and key files: pairing, host, apps, favourites, channels, Play Store labels and icons
  AppCatalog.kt     Play Store name and icon fetch and cache; fallback labels
  BoxFinder.kt      mDNS discovery when the box's address changes
  ui/RemoteScreen.kt  scaffold, top bar and menu, Remote section, pairing
  ui/AppsScreen.kt, ui/TvScreen.kt, ui/NowScreen.kt, ui/KeyButton.kt, ui/Theme.kt
```

## 7. Build, install and signing

- Needs JDK 17 (`JAVA_HOME`), the Android SDK with platform `android-35` and build-tools `35.0.0`, and a
  `local.properties` file with `sdk.dir=<path to the SDK>` (not included; it is machine-specific).
- Gradle 8.11.1 through the included wrapper. AGP 8.7.3, Kotlin 2.1.0, Compose BOM 2024.12.01.
- Build: `./gradlew :atvremote:test :app:assembleRelease`; the APK is
  `app/build/outputs/apk/release/app-release.apk`. `./build.sh` runs the same and files the APK as `../Apps/MyTv-v<versionCode>.apk` (the `Apps` folder next to
  this source folder; every version the owner installs is kept there). It refuses to overwrite an existing version.
- Release builds are shrunk with R8 and **signed with the building machine's debug keystore**. An APK built on
  another computer cannot install over the shared APK: uninstall the old app first (pairing is then lost, pair
  again) or sign with one keystore from now on.
- Bump `versionCode` (and `versionName`) in `app/build.gradle.kts` for every APK the owner installs.
- Version 4 onwards is signed with this owner's Mac debug keystore (`~/.android/debug.keystore`). Version 3 was
  signed elsewhere, so it must be uninstalled once before version 4 installs. Keep that keystore to update in place.
- Install by sideloading; Android may warn about an unknown developer.

### In-app updates and the public repo (version 9 and later)

- The source is public at github.com/harisbinrehan/my-tv (made public 2026-11-16 at the owner's request, after
  an audit: no keys, tokens or passwords; commits use the GitHub no-reply address, not the owner's email).
- `Updater.kt` reads `releases/latest/download/update.json` (versionCode, versionName, url, sha256, notes) from
  `RELEASES_REPO`, on every return to the app at most every 15 minutes, and from the menu's Check for updates.
  Update downloads the APK over HTTPS, checks the SHA-256, and commits it through `PackageInstaller`; Android
  shows its confirmation (`UpdateReceiver`), and the first time asks to allow installing from My Tv.
  `BootReceiver` also handles `MY_PACKAGE_REPLACED` to bring the notification back after an update.
- Publish with `./release.sh "notes"` after bumping the version. Never publish an APK signed with any other key.
- After publishing, GitHub's `releases/latest` can keep pointing at the previous release for a few minutes on some
  of its servers (seen 2026-11-16: the emulator got v9 for about 5 minutes while the Mac already got v10). That is
  not an app bug; the in-app update to v10 then worked end to end on the emulator (permission page, download,
  SHA-256 check, Android's confirmation, "My Tv updated to 10.0").
- **Security rule: the signing keystore (`~/.android/debug.keystore` on the owner's Mac) is what stops anyone
  else's APK from replacing the app.** Never commit it, upload it or copy it into the repo. Also never commit
  `state/` (CLI pairing key), `local.properties`, `~/.android/adbkey` (wireless debugging key) or APKs.
  `.gitignore` covers these. Before pushing, grep for keys and personal details as on 2026-11-16.

## 8. Testing against the real box from a computer

The emulator can drive the real box too (it reaches the LAN through the Mac): install a debug build, then copy a
paired identity (`client.key`, `client.crt` from a CLI state dir) into the app's `files/` with `adb shell run-as`,
and write `shared_prefs/shoq-remote.xml` with `paired=true`. Then `adb shell input` taps and types, and
`uiautomator dump` reads the app's status. Keep identities out of the project folder.

You cannot see the TV. Tell the owner before any step that changes what the TV shows, ask what appeared, and
put the TV back where it was afterwards.

```bash
./gradlew :cli:installDist
cli/build/install/cli/bin/cli pin 192.168.1.5                     # read the box certificate, compare with the pin
cli/build/install/cli/bin/cli pair 192.168.1.5 state/test code.txt # pair; write the TV's code into code.txt
cli/build/install/cli/bin/cli status 192.168.1.5 state/test        # device, power, volume, app on screen
cli/build/install/cli/bin/cli run 192.168.1.5 state/test key:HOME wait:1500 key:OK wait:2000 key:8 key:9
cli/build/install/cli/bin/cli watch 192.168.1.5 state/test wait:20000   # print every message from the box
cli/build/install/cli/bin/cli voice 192.168.1.5 state/test clip.pcm 8000 # stream raw 8 kHz 16-bit mono PCM
```

`run` and `watch` steps: `key:NAME` or `key:<code>`, `long:NAME`, `app:<package>`, `link:<uri>`,
`text:<insert>`, `live:<full text>` (TextMirror), `clear:` (delete only what was typed), `wait:<ms>`.
Key names: HOME BACK UP DOWN LEFT RIGHT OK VOLUME_UP VOLUME_DOWN MUTE POWER MENU PLAY_PAUSE.
`APP <package>` lines show what the box reports on screen. `state/` holds the tool's private key: keep it out
of git and out of anything shared. A new computer means a new key and a new pairing.

### Reading the app's activity log (version 8 and later)

Everything the app does is logged under the tag `MyTv` (`Trace.kt`): start-up, who holds the connection,
each connect attempt and why it ended (with the exception), state changes (link, power, app on screen,
volume, pairing step, busy target), keys sent or dropped, apps and channels opened, pairing steps, messages
shown to the user, and Wi-Fi up/down. Typed text appears only as a character count; pairing codes never.
On the owner's phone, connect over wireless debugging (Developer options > Wireless debugging > Pair device
with pairing code; `adb pair <ip>:<pair-port> <code>`, then `adb connect <ip>:<port>`), then:

```bash
adb logcat -v time -s MyTv
```

## 9. Open items

1. **Channel list.** PTCL publishes no numbered line-up (searched; the old Smart TV site no longer resolves) and
   the box does not expose one. The owner can photograph the TV's channel list; turn that into channels
   (number, name, category) and seed them, for example a one-time import into `Storage.channels`.
2. **Phone verification of version 3**: Now section typing and touch pad, notification card on the owner's
   Android version, voice from the phone microphone, quick settings tile, boot restart.
3. **Apps grid** fills only as apps appear on screen; the box has no app list API. A full list would need ADB
   (developer options on the box), which was deliberately not used.
4. **Battery**: the notification keeps one TLS connection open; reconnect backoff tops out at 30 s.
5. The Settings and live TV paths depend on the PTCL home layout (Home lands on the live TV preview, the
   Settings gear is one step up). A firmware update that changes the layout would break them.

6. **Versions 6 and 7.** v6 put a Cast-based Turn on TV on the pairing screen; it failed on the owner's phone and
   in every retest from a real standby (see the table in section 5), so v7 removed it and the pairing screen now
   says the box only lets a paired phone switch it on. `CastWake` stays in `atvremote` for the CLI only. Pairing
   survives updates; only uninstalling or clearing the app's data loses it.

7. **NetMirror.** Not on the Play Store. The owner's voice ("Open NetMirror" via the mic) opens it. Generated speech
   was tested 2026-11-16 and is unreliable: ending the voice stream right after the words makes the box cancel;
   digital-zero silence keeps the Assistant waiting (faint noise fixes that), but "Open NetMirror" in a synthetic
   voice opened Netflix once and only showed results the next time. `android-app://<package>` links are ignored.
   While NetMirror was on screen the box reported `com.netflix.ninja`, so NetMirror may be installed under Netflix's
   package; to confirm, ask what the Netflix tile opens. Apps Google Play has no page for (HTTP 404,
   `Storage.noPlayPage`) now say "Tap the mic and say 'Open <name>'"; tiles can be renamed.
   The Mac test identity lives in `~/Library/Application Support/MyTvCli/state` (re-paired 2026-11-16 21:3x).
   **The box forgot that pairing when it restarted at 18:28**; check whether phones lose theirs too.

## 10. If this is a different Shoq box

Also set `ShoqBox.BOX_ID` and `MAC_ADDRESSES` from the `cli pin` SUBJECT line and the router's ARP table.

The app is locked to the box above. For another unit: run `cli pin <ip>`, put the printed SHA-256 into
`ShoqBox.CERT_SHA256` and the address into `ShoqBox.DEFAULT_HOST`, rebuild, and pair the phone again.
A factory reset of the box also changes its certificate.

## 11. Rules for working on this project

- Never send guessed app links to the box; only https links or links proven to work.
- Delete only what the app typed; never send extra Backspace.
- Press OK on a Play Store page only for an app the box has shown running (installed).
- Test on the real box for anything involving the box, and report what was and was not verified.
- Keep comments short (one or two lines, explaining why). No secrets in commits: `state/`, `local.properties`
  and keystores stay out.
