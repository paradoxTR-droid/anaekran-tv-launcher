# Kendi TV ana ekranı (launcher) — tasarım

Tarih: 2026-11-15

## Amaç ve neden

Next 4K Google TV Stick için kullanıcının kendi ana ekran uygulaması. Bugün
Projectivy Launcher kullanılıyor; reklamsız ve hızlı ama kullanıcı güvenlik
konusunda ona güvenmiyor (loglarda Firebase'e oturum verisi gönderdiği
görüldü). Hedef: kaynak kodu bu klasörde duran, kullanıcının kendi derlediği,
dışarıya hiçbir veri gönderemeyen, hafif bir ana ekran.

## Cihaz

- Model `4K_Google_TV_Stick` (Skyworth / Amlogic), Android 14 (API 34), Google TV.
- 2 GB RAM, boşta ~600 MB. Ekran 1920x1080, density 320 (xhdpi; 960x540 dp).
- ADB: `<NEXT_STICK_IP>:5555` (ağ üzerinden, yetkili).
- Mevcut durum: `com.google.android.apps.tv.launcherx` ve
  `com.google.android.tungsten.setupwraith` devre dışı (`pm disable-user`).
  Varsayılan ana ekran `com.spocky.projengmenu/.ui.home.MainActivity`.
- Kumanda: Bluetooth "RemoteG10" (D-pad, OK, Geri, Home, ses). Ses tuşları
  şu an CEC'e yönlendirilmiş; bu proje ses tuşlarına dokunmaz.

## Hedefler

1. **Giriş animasyonu (TV açıldığında, ~2 sn):** "TV açıldığında" = (a) cihaz
   açılışından sonraki ilk gösterim ve (b) bekleme modundan her uyanıştan
   sonraki ilk gösterim (kullanıcı kararı: her açılışta oynasın). Süreç
   ölümüne bağlı değildir: RAM baskısıyla öldürülüp Home ile yeniden başlayan
   süreç animasyon oynatmaz.
   - Açılış tespiti: `System.currentTimeMillis() -
     SystemClock.elapsedRealtime()` ile hesaplanan açılış zamanı
     `SharedPreferences`'ta saklanan değerle karşılaştırılır
     (`RECEIVE_BOOT_COMPLETED` yok).
   - Uyanış tespiti: activity yaşarken dinamik kayıtlı receiver
     `ACTION_SCREEN_OFF` gelince `SharedPreferences`'a "intro bekliyor"
     bayrağı yazar. `onResume`'da bayrak varsa animasyon oynar ve bayrak
     silinir. Bayrak kalıcı olduğu için süreç bekleme sırasında öldürülse de
     uyanışta animasyon oynar. Uyanışta ana ekran öne gelmezse (başka bir
     uygulama açık kaldıysa) animasyon bir sonraki ana ekran gösteriminde
     oynar. Animasyon sırasında herhangi bir tuş
   animasyonu atlar ve ızgarayı hemen kullanılabilir yapar (builder kararı).
   Ekranın ortasında
   saate göre seçilmiş bir cümle büyükten normale belirir, sonra küçülerek
   sol üste (başlık yerine) kayar; ardından uygulama kartları alttan sırayla
   (her biri ~80 ms gecikmeli) uçarak ızgaradaki yerine oturur. Animasyon
   bitince ekran doğrudan ana ekrandır — ayrı bir geçiş yok.
2. **Saate göre değişen cümle:** Her soğuk açılışta saat dilimine göre bir
   cümle, o dilimin listesinden rastgele seçilir. Animasyondan sonra başlık
   olarak sol üstte kalır. Launcher günlerce açık kalabildiği için saat
   dilimi değişince başlık da animasyonsuz yeni dilimin cümlesine geçer
   (dakikalık saat güncellemesiyle birlikte kontrol edilir; builder kararı). Dilimler ve örnek cümleler (son listeyi builder
   genişletebilir, her dilimde 3–5 cümle):
   - 05:00–11:00 sabah: "Günaydın, kahve hazır mı?"
   - 11:00–17:00 gündüz: "Öğleden sonra molası"
   - 17:00–22:00 akşam: "Akşam keyfi"
   - 22:00–05:00 gece: "Gece sineması başlasın"
3. **Tek ızgara:** Cihazda kurulu tüm TV uygulamaları (`CATEGORY_LEANBACK_LAUNCHER`
   activity'si olanlar), kendisi hariç, 5 sütunlu ızgarada; her kart
   uygulamanın banner'ı (16:9). Banner yoksa ikon + uygulama adı içeren
   düz renk kart. Çok uygulama varsa ızgara dikey kayar.
4. **Odak:** Kumandayla üzerine gelinen kart ~%8 büyür, beyaz çerçeve ve
   gölge alır (150 ms animasyon). OK uygulamayı açar.
5. **Üst bar (sağ üst):** saat (HH:mm, dakikada bir güncellenir), ardından
   üç odaklanabilir simge: Wi-Fi (`Settings.ACTION_WIFI_SETTINGS`), Ses
   (`Settings.ACTION_SOUND_SETTINGS`), Ayarlar (`Settings.ACTION_SETTINGS`).
   Izgaranın ilk satırından yukarı basınca bu simgelere çıkılır.
6. **Sıralama ve gizleme:** Varsayılan sıra alfabetik (Türkçe collator). OK'e
   uzun basınca küçük menü: "Başa taşı", "Gizle". Başa taşınanlar en üstte,
   taşınma sırasına göre. Gizlenenleri geri getirmek için Ayarlar simgesine
   uzun basınca "Gizli uygulamalar" listesi açılır; oradan tekrar gösterilir.
   Kayıt: `SharedPreferences` (sabitlenenler sıralı liste, gizlenenler küme;
   anahtar paket adı).
7. **Güncel liste:** Uygulama kurulunca/kaldırılınca ızgara kendiliğinden
   yenilenir (activity açıkken dinamik kayıtlı `PACKAGE_ADDED/REMOVED/CHANGED`
   receiver'ı; ayrıca `onResume`'da yeniden yükleme).
8. **Ana ekran davranışı:** Home tuşu ızgarayı (animasyonsuz) gösterir ve
   odağı ilk karta alır. Geri tuşu ana ekranda hiçbir şey yapmaz.

## Hedef olmayanlar

- Öneri/kanal satırları, "izlemeye devam et", arama, sesli asistan,
  bildirimler, hava durumu, duvar kağıdı seçimi, tema seçimi, çoklu profil.
- Ses tuşu, CEC veya kumanda davranışı değişikliği.
- Telefon/tablet desteği (yalnızca bu TV, yatay 1080p).

## Anti-hedefler (çalışsa bile başarısızlık sayılır)

- Manifest'te `INTERNET` veya herhangi bir ağ izni olması.
- Analitik, çökme raporu, reklam veya herhangi bir 3. taraf çalışma zamanı
  kütüphanesi. İzinli tek çalışma zamanı bağımlılığı: `kotlin-stdlib`.
- Erişilebilirlik servisi, arka plan servisi, `QUERY_ALL_PACKAGES`,
  `RECEIVE_BOOT_COMPLETED` gibi gereksiz izinler.
- Projectivy'den yavaş hissettirmesi veya daha fazla RAM kullanması
  (Projectivy ~90 MB PSS ölçüldü; hedef bunun altı). Hız ölçütü: animasyon
  hariç, Home'dan kullanılabilir ızgaraya süre (`am start -W`)
  Projectivy'ninkinden kısa (builder kararı; animasyon atlanabilir olduğu
  için ölçüme dahil değil).

## Kısıtlar

- Yalnızca Android framework View'ları ve animatörleri (`ValueAnimator`,
  `ViewPropertyAnimator`); AndroidX yok (RecyclerView dahil). Izgara
  framework `GridView` ya da `ScrollView` içinde elle dizilmiş kartlar.
- Kotlin, `minSdk 34`, `compileSdk`/`targetSdk 36` (yüklü: platforms 34–37,
  build-tools 36.0.0). JDK 17, sistem Gradle 9.x mevcut.
- Derleme araçları (Android Gradle Plugin, Kotlin Gradle plugin) ilk derlemede
  Google Maven / Maven Central'dan indirilir; bunlar APK'ya girmez.
- Package visibility: manifest'te `<queries>` içinde `LEANBACK_LAUNCHER`
  intent'i; `QUERY_ALL_PACKAGES` kullanılmaz.
- Banner'lar arka plan thread'inde yüklenir, bellek içi cache'lenir; ana
  thread'de PackageManager çağrısı yok.
- Uygulama adı (sistemde görünen): "Ana Ekran". Paket adı builder'a kalmış.

## Yaklaşımlar

- **A · Sade Android View'ları + Kotlin (seçilen).** Dış kütüphane yok, APK
  birkaç yüz KB, düşük RAM. Kumanda odağı elle ayarlanır. Kullanıcı bunu
  seçti: güvenlik (incelenecek kod en az) ve 2 GB cihazda hız.
- **B · Jetpack Compose for TV.** Odak ve animasyon yazması kolay ama onlarca
  AndroidX kütüphanesi, birkaç MB APK, daha ağır açılış. Güvenlik/hız
  gerekçesine ters.
- Elenenler: web/Flutter (ağır, gerekçeye ters); saf Java (Kotlin stdlib'den
  kurtulur ama kazanç küçük, kod uzar).

## Kurulum ve geçiş

1. `./gradlew assembleDebug` (imzalama için debug anahtarı yeterli; kişisel cihaz).
2. `adb install -r <apk>`; `aapt dump permissions <apk>` ile izin listesinin
   boş (ya da yalnızca zararsız) olduğu doğrulanır.
3. `adb shell cmd package set-home-activity <paket>/.MainActivity`;
   Home tuşuyla açıldığı doğrulanır.
4. Kullanıcı onaylarsa Projectivy kaldırılır (`pm uninstall com.spocky.projengmenu`).
5. Geri dönüş: Projectivy kaldırılmadıysa `set-home-activity
   com.spocky.projengmenu/.ui.home.MainActivity`; kaldırıldıysa
   `pm enable com.google.android.apps.tv.launcherx` ve
   `pm enable com.google.android.tungsten.setupwraith`.

## Doğrulama

- Saat→dilim→cümle seçimi için küçük bir JVM birim testi (dilim sınırları:
  04:59, 05:00, 10:59, 11:00, 21:59, 22:00, 00:00).
- Cihazda: yeniden başlatmadan sonra ve bekleme modundan uyanınca
  (`input keyevent KEYCODE_SLEEP` / `KEYCODE_WAKEUP`) animasyon oynar;
  Home'da ve süreç öldürülüp (`am kill`) Home'a basılınca oynamaz;
  animasyon sırasında tuşa basınca atlanır; tüm Leanback
  uygulamaları görünür; OK açar; uzun basma menüsü sabitler/gizler ve
  yeniden başlatmadan sonra korunur; üst bar simgeleri doğru ayar ekranını
  açar; `dumpsys meminfo <paket>` PSS < 90 MB.

## Builder'a kalanlar

- Paket adı, dosya/sınıf düzeni, renk ve yazı tipi ayrıntıları (koyu tema,
  taslaktaki koyu lacivert radyal arka plan esas alınır).
- Dilim başına cümlelerin tam listesi (yukarıdaki tonda, Türkçe).
- Izgara için `GridView` mi elle dizilim mi.
- Animasyon eğrileri ve tam süreler (toplam ~2 sn içinde kalmak şartıyla).

## Değişiklik (2026-11-15): Düzen B · Satırlar

Kullanıcı uygulama sırasında düzeni A'dan (tek ızgara) B'ye (satırlar) çevirdi.
Bu bölüm Hedef 3, 5 ve 6'nın yerine geçer; diğer hedefler aynen geçerli.

- **Üst:** solda saate göre değişen cümle, sağda saat. Simge yok.
- **Favoriler satırı:** büyük 16:9 kartlar (ekranda 4 tane görünür, fazlası
  yana kayar). İlk açılışta hazır: Netflix (`com.netflix.ninja`), YouTube
  (`com.google.android.youtube.tv`), Disney+ (`com.disney.disneyplus`), Exxen
  (`com.exxen.android`) — yalnızca kurulu olanlar (kullanıcı kararı). Bir kez
  yazılır; sonra kullanıcının seçimi esastır.
- **Tüm uygulamalar satırı:** küçük 16:9 kartlar, yana kayar, alfabetik
  (Türkçe), favoriler ve gizlenenler hariç.
- **Ayarlar satırı:** Wi-Fi, Ses, Ekran, Bluetooth, Tüm ayarlar kutucukları
  (simge + yazı). "Tüm ayarlar"a uzun basınca "Gizli uygulamalar" açılır.
- **Uzun basma menüsü:** favori kartında "Favorilerden çıkar", "Gizle";
  diğer kartlarda "Favorilere ekle" (sona eklenir), "Gizle".
- **Giriş animasyonu:** cümle ortadan başlığa kayar, ardından görünen kartlar
  ve kutucuklar satır satır alttan gelir (toplam ~2 sn, tuşla atlanır).
- **Odak:** yön tuşlarıyla satırlar arası geçiş; açılışta ilk favori kartı.
