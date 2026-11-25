# Netflix Remote

Netflix Remote is a small Android companion app that turns one Android phone into a remote control for Netflix playing on another Android device connected to a TV. Install the same APK on both devices, then choose **Tablet** (the receiver) or **Phone** (the remote) on first launch.

```
[Phone: remote] -- local Wi-Fi / TCP --> [Tablet: receiver] -- HDMI --> [TV]
```

It is an independent, unofficial project and is not affiliated with, endorsed by, or associated with Netflix.

## Why this app exists

Netflix on regular Android tablets does not provide a general-purpose remote-control API comparable to the one available on Android TV or Fire TV. This app was created for the practical setup of an Android tablet connected to a television over HDMI, where controlling playback from the sofa with a phone is more convenient than reaching for the tablet.

No screen or video is mirrored or transmitted. The phone sends only small text commands on the local network.

## How it works

The tablet runs a foreground service that advertises itself on the local Wi-Fi network (mDNS) and listens for authenticated commands. The phone can discover it automatically or connect with its IP address and a six-digit PIN.

For playback control, the tablet uses the best available method:

1. **Netflix media session** — when Netflix exposes an Android media session, the app uses native play, pause, and seek commands. This is the most accurate option and requires notification access on the tablet.
2. **Accessibility controls** — otherwise, the app finds Netflix player controls by their accessible labels and activates them. If a control cannot be found, it falls back to gestures, including centre taps for play/pause and side double-taps for seeking.

The default **Automatic** mode tries the media session first, then accessibility. You can force either mode from the tablet’s Advanced section.

## Compatibility

- Android **8.0 (API 26)** or newer on both devices.
- Two Android phones/tablets connected to the same local Wi-Fi network. Router client isolation must be disabled.
- Netflix installed on the receiver device. The app targets the standard Android Netflix package (`com.netflix.mediaclient`).
- An HDMI-capable Android tablet or device plus an HDCP-compatible cable, adapter, and TV/display if you want to use an external screen.
- The receiver needs the Android **Accessibility** service enabled. Notification access is strongly recommended for accurate native playback control.

The app has been tested with Xiaomi Pad 7 (HyperOS / Android 16) as the HDMI receiver and a Nothing A059 (Android 16) as the remote. Netflix’s interface and media-session behaviour can vary by device, Android version, app version, language, and account, so gesture fallbacks may need adjustment on other devices.

## Install and use

The prebuilt debug APK is available as [`NetflixRemote.apk`](NetflixRemote.apk).

### Receiver tablet

1. Install and open the app, then choose **Tablet**.
2. Enable **Accessibility** for Netflix Remote. This is required for gesture-based playback and navigation.
3. Enable **Notification access** for Netflix Remote. This enables media-session control when Netflix makes it available.
4. Tap **Start service** and note the six-digit PIN shown on screen.

### Remote phone

1. Install and open the app, then choose **Phone**.
2. Tap **Search** to discover the tablet, or enter its IP address manually.
3. Enter the PIN displayed on the tablet and tap **Connect**.

The phone remembers the last address and reconnects automatically. The receiver service starts again after a device reboot or an app update unless it was explicitly stopped.

## Available controls

| Control | Preferred method | Fallback |
| --- | --- | --- |
| Play / pause | Netflix media session | Player control or centre-screen taps |
| Seek ±10 s / ±30 s | Native seek | Netflix seek control or side double-taps |
| Skip intro / next episode | — | Accessible player button, then configurable screen position |
| Volume / mute | Android audio manager | Same |
| Back | Android accessibility global action | Same |
| Open Netflix | Launches the Netflix app | Same |

## Troubleshooting

- **The phone cannot find or connect to the tablet:** confirm that the receiver service is running, both devices are on the same Wi-Fi network, and the router does not isolate clients. Use the tablet’s displayed IP address for manual connection if necessary.
- **“Skip intro” misses:** in the Tablet app, open **Advanced** and adjust the fallback position as a percentage of the screen. The app first tries the real accessible button.
- **Accessibility stops working:** Android vendors can disable accessibility services or stop background work. Re-enable the service and, where available, exempt Netflix Remote from battery optimisation and enable auto-start.
- **Video is black on the TV:** Netflix requires an HDCP-compatible HDMI chain. Check the cable, adapter, and display; this is independent of the remote-control connection.

## Build from source

Requirements: Android Studio with an Android SDK for API 35, and JDK 17 or later.

```bash
./gradlew assembleDebug
```

The generated APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

`local.properties` is deliberately untracked and contains no project-specific paths. Let Android Studio create it, or configure `ANDROID_HOME` / `ANDROID_SDK_ROOT` for your local SDK.

## Security and privacy

The receiver listens only on the local network and accepts commands only after the correct six-digit PIN is supplied. Keep the PIN private on shared Wi-Fi networks. The app does not relay video, create a cloud account, or require an internet service of its own; Netflix itself naturally requires whatever network access it normally uses.
