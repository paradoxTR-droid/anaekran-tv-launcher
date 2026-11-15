# Ana Ekran

Next 4K Google TV Stick (Android 14) ve Xiaomi Mi TV Stick (Android 9) için kendi ana ekranım: internet izni yok, dış kütüphane yok.

![Ana ekran](docs/img/anaekran.png)

Tasarım: `docs/superpowers/specs/2026-11-15-tv-launcher-design.md`

## Kurulum

### Gerekenler

- Android 9 (API 28) veya üstü bir Android TV / Google TV cihazı
- Bilgisayarda JDK 17, Android SDK (`local.properties` içinde `sdk.dir=...`) ve `adb`
- TV ile bilgisayar aynı ağda

### 1. TV'de ağ üzerinden hata ayıklamayı aç

1. Ayarlar > Sistem > Hakkında > **Android TV OS derlemesi**'ne 7 kez bas. Geliştirici seçenekleri açılır.
2. Ayarlar > Sistem > Geliştirici seçenekleri > **USB hata ayıklama**'yı aç (Android 11+ cihazlarda **Kablosuz hata ayıklama** da olabilir).
3. TV'nin IP adresini not et: Ayarlar > Ağ ve İnternet > bağlı ağ.

### 2. Bağlan

```bash
adb connect <TV_IP>:5555
export ANDROID_SERIAL=<TV_IP>:5555
```

TV'de çıkan "USB hata ayıklamaya izin verilsin mi?" penceresinde **İzin ver**'i seç.

### 3. Derle ve kur

```bash
./gradlew :app:assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

### 4. Ana ekran yap

```bash
adb shell cmd package set-home-activity dev.anaekran/.HomeActivity
```

Bazı cihazlarda orijinal ana ekran yine öne çıkar. O zaman orijinal ana ekranı kapat:

- Google TV (Next, Android 14):
  ```bash
  adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
  adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
  ```
- Android TV (Xiaomi, Android 9):
  ```bash
  adb shell pm disable-user --user 0 com.google.android.tvlauncher
  ```

HOME tuşuna bas; yeni ana ekran açılır.

### 5. İşin bitince

Ağ üzerinden hata ayıklama açık kalırsa ağdaki başka biri cihaza bağlanmayı deneyebilir. Kurulumdan sonra Geliştirici seçenekleri'nden **USB hata ayıklama**'yı kapat.

## Kullanım

- **OK**: uygulamayı aç
- **OK'a uzun bas**: menü (favorilere ekle/çıkar, sırala, gizle, uygulama bilgisi, kaldır)
- **Tüm ayarlar'a uzun bas**: gizli uygulamalar
- Uygulama kaldırma her zaman sistemin onay penceresinden geçer.

## Geri dönüş

Orijinal ana ekranı geri aç, sonra bu uygulamayı kaldır:

- Google TV (Next):
  ```bash
  adb shell pm enable com.google.android.apps.tv.launcherx
  adb shell pm enable com.google.android.tungsten.setupwraith
  ```
- Android TV (Xiaomi):
  ```bash
  adb shell pm enable com.google.android.tvlauncher
  ```

```bash
adb uninstall dev.anaekran
```

## Testler

```bash
./gradlew :app:testDebugUnitTest
```

## Lisans

[MIT](LICENSE)
