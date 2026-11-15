# Ana Ekran

Next 4K Google TV Stick (Android 14) ve Xiaomi Mi TV Stick (Android 9) için kendi ana ekranım: internet izni yok, dış kütüphane yok.

![Ana ekran](docs/img/anaekran.png)
Tasarım: `docs/superpowers/specs/2026-11-15-tv-launcher-design.md`

```bash
export ANDROID_SERIAL=<NEXT_STICK_IP>:5555
./gradlew :app:assembleRelease                                      # derle
adb install -r app/build/outputs/apk/release/app-release.apk        # kur
adb shell cmd package set-home-activity dev.anaekran/.HomeActivity  # ana ekran yap
```

Geri dönüş:
- Projectivy kuruluysa: `adb shell cmd package set-home-activity com.spocky.projengmenu/.ui.home.MainActivity`
- Next (Google TV): `adb shell pm enable com.google.android.apps.tv.launcherx && adb shell pm enable com.google.android.tungsten.setupwraith`
- Xiaomi (Android 9, <XIAOMI_STICK_IP>): `adb shell pm enable com.google.android.tvlauncher`

Android 9'da `set-home-activity` tek başına yetmiyor; orijinal ana ekran (`com.google.android.tvlauncher`) `pm disable-user --user 0` ile kapatıldı.
