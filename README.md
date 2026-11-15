# Ana Ekran

Next 4K Google TV Stick için kendi ana ekranım: internet izni yok, dış kütüphane yok.
Tasarım: `docs/superpowers/specs/2026-11-15-tv-launcher-design.md`

```bash
export ANDROID_SERIAL=<NEXT_STICK_IP>:5555
./gradlew :app:assembleRelease                                      # derle
adb install -r app/build/outputs/apk/release/app-release.apk        # kur
adb shell cmd package set-home-activity dev.anaekran/.HomeActivity  # ana ekran yap
```

Geri dönüş:
- Projectivy kuruluysa: `adb shell cmd package set-home-activity com.spocky.projengmenu/.ui.home.MainActivity`
- Değilse: `adb shell pm enable com.google.android.apps.tv.launcherx && adb shell pm enable com.google.android.tungsten.setupwraith`
