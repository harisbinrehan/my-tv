# My Tv

An Android remote control for one device: the PTCL Shoq TV box (ZTE B866V2F) on this home network.
It talks to the box over Wi-Fi with Google's Android TV remote protocol, the same one the Google TV
phone app uses, so the box needs no developer mode, no ADB and no root.

## Only this box

The box's remote service presents a TLS certificate. Its SHA-256 is pinned in
`atvremote/src/main/kotlin/com/shoqremote/atv/ShoqBox.kt`, and the app refuses any device whose
certificate differs, on both the pairing port (6467) and the remote port (6466). If the router gives
the box a new address, the app finds it again over Bonjour and checks the same pin.

A factory reset of the box gives it a new certificate. Read the new one and rebuild:

```bash
cli/build/install/cli/bin/cli pin 192.168.1.5
```

## Build

```bash
./build.sh
```

It runs the protocol tests and files the APK as `../Apps/MyTv-v<versionCode>.apk`, next to this source
folder. Bump `versionCode` in `app/build.gradle.kts` first; an existing version is never overwritten. It needs
JDK 17, the Android SDK (platform 35, build-tools 35) at the path in `local.properties`, and Gradle 8.11.1.

## Install on the phone

Copy the newest APK from `Apps/` to the phone (or serve the folder on the home Wi-Fi and open it in the phone's
browser), allow the browser or file manager to install apps when Android asks, and install it.

## Pair

Open the app, switch the TV to the Shoq box, tap **Start pairing** and type the 6-character code the
TV shows. The app keeps its key in app-private storage, out of backups. To unpair, clear the app's
storage, or clear the box's "Android TV Remote Service" data to forget every paired remote.

## What it does

Four sections: Remote, Apps, TV (live channels) and Now (the app on screen). One connection to the box is
shared by the screens and a permanent notification with live controls (menu, Notification controls).

- **TV**: dial any channel number, channel up and down, and your own channel list with categories, search,
  and pinned channels in your own order. Live TV is ZTE's player, reached with Home then OK; the app types the
  channel number there.
- **Now**: the app on the TV, a touch pad (swipe to move, tap for OK), live typing that mirrors each letter
  into the TV's text box, speech-to-text, a Search YouTube shortcut when YouTube is on screen, voice search
  through the TV's Google Assistant, and volume with a clear Mute/Unmute.

Earlier notes, still true:

- **Remote**: D-pad and OK, Back, Home, Menu, power, volume and mute, channel up and down, play or
  pause, rewind, fast forward, search, a number pad, typing into the TV's search boxes, and TV
  Settings. Holding a key acts like holding it on the real remote; the phone's volume buttons drive
  the box while the app is open.
- **Apps**: a tile for each app the box has shown on screen, with its official name and icon read
  from the app's Google Play page. Hold a tile to add it to favourites or remove it.
- **Notification panel**: a My Tv quick settings tile that opens the remote (menu, Add to
  notification panel, on Android 13+; older phones add it by editing the panel).

## How it opens things on this box

The box's remote service opens a link only when an installed app claims it. It ignores `market://`
and `intent:` links, and a link no app claims (a guessed custom scheme) makes it drop the connection,
so the app never guesses.

- Apps with a known web address (YouTube) open from that address.
- Every other app opens through its Play Store page: the app waits for the page, presses OK on its
  Open button, and checks that the app came up (about 3 seconds).
- TV Settings has no address, so the app presses Home, Up and OK, the path to the gear on the PTCL
  home screen, and checks that Settings opened.

## Mac test tool

`cli` exercises the same protocol code from this Mac:

```bash
cli/build/install/cli/bin/cli pin [host]
cli/build/install/cli/bin/cli pair <host> <stateDir> <codeFile>
cli/build/install/cli/bin/cli status <host> <stateDir>
cli/build/install/cli/bin/cli key <host> <stateDir> UP DOWN LEFT RIGHT OK BACK HOME
cli/build/install/cli/bin/cli run <host> <stateDir> key:HOME wait:1500 app:<package> link:<uri> long:OK
```

`run` prints the app the box reports on screen (`APP ...`) as each step lands.

## Layout

| Path | What |
|---|---|
| `atvremote/` | The protocol in plain Kotlin: framing, messages, client certificate, pinned TLS, pairing, remote |
| `app/` | The Android app (Jetpack Compose) |
| `cli/` | The Mac test tool |
