# AudioScribe companion (Android)

Listen to and read your AudioScribe library on your phone. Your place, bookmarks and highlights sync
with the PC; books you download play with no connection.

## Quick start (no install)
1. On the PC: AudioScribe → **Phone** → **Start sharing**.
2. On the phone (same Wi-Fi), open the address shown in Chrome and type the 6-digit code.
3. Optional: Chrome menu → **Add to Home screen**.

In the browser, downloaded books play while the page stays open. For full offline use (opening the
app with the PC switched off), install the Android app below.

## The Android app
`android/` is a small Android Studio project that bundles this same page (`index.html`) into an app.
- **Without Android Studio:** push this folder to GitHub and run the *Android companion* workflow
  (`.github/workflows/android.yml`). Download the APK from the run and open it on the phone (allow
  installing from your browser/files app when asked).
- **With Android Studio:** open `companion/android`, then Build → Build APK(s).

The first time, type the PC address (e.g. `192.168.1.20:8765`) and the code.

## Notes
- Phone and PC talk over your home network only (plain HTTP, pairing code + token). Don't forward the
  port to the internet.
- Books without audio can be read aloud with the phone's own voice (▶).
- Long-press a sentence to bookmark, highlight or add a note. Tap a sentence to listen from there.

## Lock screen, notification and Android Auto (Android app)
The app shows what's playing on the lock screen and in a notification (previous chapter / play / next
chapter), keeps playing when you switch apps, and works with headphone buttons. In Android Auto it lists
the chapters of the book you're on and has sleep-timer buttons. Android Auto only shows apps installed
from the Play Store unless you enable **Unknown sources**: open Android Auto's settings, tap the version
number ten times to unlock developer settings, then turn on *Unknown sources*.
