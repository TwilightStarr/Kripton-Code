# Masaüstü modu: sekme gruplama, animasyonlar ve görev çubuğu iyileştirmeleri
- **Mevcut model korundu:** masaüstü zaten sekme tabanlıydı (`DesktopWindows.kt` = saf sekme mantığı; yüzen pencereler `v2` ile sekmeye göçmüştü), bu yüzden ayrı bir `WindowMode.Tab` eklenmedi. Üstüne gruplama + hareket eklendi.
- **Animasyonlar** (`DesktopMotion.kt` → `TabLayer`): uygulama **açılışı** alt çubuktaki simgesinden merkeze doğru büyür (köşeler yuvarlaktan düze açılır); **sekme geçişi** yatay kayma yerine hafif zoom + solma; **kapanış** aşağı doğru küçülerek kaybolur. Kapanan AI sekmesinin web oturumu animasyon bitince bırakılır (sayfa kapanırken boşalmaz). Açılışta geri yüklenen sekmeler animasyon oynamaz.
- **Hareket belirteçleri** (`Motion.kt`): `Motion.SHORT/MEDIUM/LONG` süreleri + `Emphasized*` eğrileri; `MotionScale`/`LocalMotionScale` hepsini tek yerden ölçekler (`MotionMath.kt` saf hesap, `MotionTest`). Projede Material3 `MotionScheme` yoktu; o yaylanma tabanlıdır ve süre ölçeklenemez, bu yüzden süre + eğri belirteçleri kullanıldı.
- **Ayarlar > Masaüstü modu > Animasyonlar:** "Pencere ve sekme animasyonları" anahtarı, **"Geçiş hızı"** kaydırıcısı (0,5×–2×) ve **"Sistem animasyon ölçeğini izle"** (`Settings.Global.ANIMATOR_DURATION_SCALE`, canlı izlenir; 0 = animasyon yok). Genel "Animasyonlar" anahtarı ve sistemin "animasyonları kaldır"ı yine geçerli: kapalıyken hiçbir şey hareket etmez. Veri: `DesktopPrefs.windowAnimations / motionSpeed / followSystemAnimatorScale`.
- **Sekme gruplama:** birden çok penceresi olabilen uygulamalar (şimdilik **Dosyalar**; her pencerenin kendi klasörü var) çubukta TEK öğe altında toplanır; köşede sayı rozeti. Görünen grupta yeniden dokunmak sıradaki pencereye geçer. AI sekmeleri (hesap başına tek web oturumu) ve Kripton Code (tek uygulama yığını) bilerek tek pencerede kaldı. Kayıt biçimi (`v2`) değişmedi; eski kayıtlar aynen okunur.
- **Görev çubuğu:** çalışan uygulama göstergesi yumuşakça uzar/kısalır; öğeler çubuğa girerken/çıkarken/yer değiştirirken kayar. **Uzun basış ya da sağ tık menüsü:** pencere **önizleme kartları** (renkli şerit + simge + ad, kartta ✕, dokununca o pencereye geç), yenile (AI), **yeni pencere** (Dosyalar), sabitle/kaldır, sola/sağa taşı, kapat / **tümünü kapat** (grupta). Masaüstünü göster düğmesinin uzun basış / sağ tık menüsünde **tüm sekmeleri kapat**.
- **Kartlar canlı küçük görüntü değildir:** WebView/PDF içeriğini küçük resme çizmek pahalı ve kararsız olduğundan kart ad + simge gösterir.
- Testler: `DesktopTabsTest` (gruplama, yeni pencere, döngü, tümünü kapat, kayıt) ve `MotionTest` (süre ölçekleme). Yeni dizeler `strings_desktop.xml` (tr/en).

# Masaüstü modu: Linux tarzı başlatıcı (launcher) + sekmeler
- **Yeni düzen:** yüzen/sürüklenen pencereler kaldırıldı. Uygulamalar artık **sekme gibi** açılır: aynı anda tek sekme tüm alanı kaplar, diğerleri arkada açık kalır (AI oturumları, sohbetler, dosya gezintisi kaybolmaz). Sekme yokken boş masaüstü görünür (büyük saat, tarih, sabit uygulamaların kısayolları).
- **Alt çubuk (dock):** solda **Uygulamalar** düğmesi; yanında sabitlenmiş uygulamalar ve açık sekmeler; sağda masaüstünü göster, tam ekran, saat. Açık uygulama sekme gibi görünür (simge + ad, altında açık işareti); görünen sekmede ✕ ile kapanır. **Uzun basış menüsü:** yenile (AI), çubuğa sabitle / kaldır, sola / sağa taşı, kapat.
- **Uygulama başlatıcı:** arama kutulu ızgara; bölümler: Kripton (Kripton Code, Dosyalar), Yapay zekâlar, Cihazdaki uygulamalar (gerçek simgeleriyle). Dokununca sekme olarak açılır; kutucuktaki **raptiye** (ya da uzun basış) uygulamayı alt çubuğa ekler/çıkarır. Altta: tam ekran, ana ekran (launcher) yap, masaüstü modundan çık. Cihaz uygulamaları her açılışta yenilenir (yeni kurulanlar görünür).
- **Cihazdaki Android uygulamaları:** Android bir uygulamayı başka uygulamanın içine gömmeye izin vermez; bu yüzden tam ekran açılır ve çubukta açık sekme olarak izlenir. Çubuktan dokununca yeniden öne gelir; Ana Sayfa tuşu masaüstüne döndürür. Bu sekmeler kaydedilmez (uygulama artık çalışmıyor olabilir).
- **Yan yana (bölünmüş ekran) uygulama kaldırıldı:** `FLAG_ACTIVITY_LAUNCH_ADJACENT` yolu, seçici ve ilgili metinler silindi; yerine yukarıdaki sekme düzeni geldi.
- **Dosyalar sekmesi:** dosyayı basılı tutup **alt çubuktaki açık AI sekmesine** sürükle (hedef vurgulanır) ya da satır menüsünden gönder. Diğer uygulamadan sürükle-bırak, görünen AI sekmesine hâlâ bırakılabilir.
- **Kayıt:** sekmeler, görünen sekme ve sabit uygulamalar kaydedilir (`v2` biçimi). Eski yüzen pencere kaydı (`v1`) sekmelere çevrilerek okunur. Silinen AI hesaplarının sekmeleri ve sabitleri kaldırılır. İlk açılışta Kripton Code açık, çubukta Kripton Code + Dosyalar sabittir.
- **Altyapı:** sekme/sabitleme kuralları (`ui/desktop/DesktopWindows.kt`) saf mantıktır, birim testlidir (`DesktopTabsTest`). Yeni dosyalar: `DesktopLauncher.kt` (başlatıcı + boş masaüstü), `DesktopAppIcons.kt`, `DesktopDrop.kt`; çubuk `DesktopTaskbar.kt`'de.
- Kaldırılanlar: `DesktopWindowFrame.kt`, pencere taşı/boyutlandır/yasla/döşe/diz kodu ve ilgili metinler. Manifest: yalnız `<queries>` açıklaması güncellendi.

# Tema: gri-beyaz (gümüş) tonlar + AMOLED saf siyah
- Zümrüt yeşil vurgu kaldırıldı; tüm vurgu, durum ("tamamlandı", "canlı", "önerilen") ve rozet renkleri logo gibi gri/beyaz tonlara çevrildi. Yalnız hata rengi kırmızı kalır.
- Koyu temanın zemini saf siyah (#000000): AMOLED ekranda pikseller kapanır. Kartlar/yüzeyler siyahtan birkaç kademe açık gri; Material "tint" kapatıldı, menü ve iletişim kutuları griye boyanmaz.
- Açılış ekranı, pencere zemini ve widget siyah/gümüşe uyarlandı; AI sağlayıcı simgeleri (avatar, odaklı pencere çerçevesi) renkli kalır.
- Açık tema korundu (beyaz/grafit).

# Çalışma alanı: düzen kaydetme, hazır düzenler, sabitleme, yer değiştirme
- **Düzen kaydetme:** açık AI'lar, pencere boyutları, sabit pencere, hazır düzen ve "Odağı büyüt" seçimi saklanır; çalışma alanı açılınca aynı düzen gelir. Silinmiş hesapların pencereleri geri gelmez; bozuk kayıt çökmeye değil boş düzene yol açar.
- **Hazır düzenler** (üstte "Düzenler"): "Eşit böl" ve "Biri büyük, diğerleri yan şeritte" (sayfadaki ilk pencere büyük, kalanlar yan şeritte alt alta; boyut tutamacı bu düzende de çalışır).
- **Sabitleme (pin):** pencere başlığındaki iğne ile bir AI her sayfada görünür; diğerleri kalan yerlerde sayfalanır.
- **Yer değiştirme:** başlığı sürükleyip başka pencerenin üstüne bırakınca iki pencere yer değiştirir (boyutlar yuvada kalır). Sabit pencere sürüklenmez.
- **Çift dokunuşla tam ekran:** başlığa çift dokunmak büyütür / geri alır.
- **Bildirim rozeti:** başka sayfadaki bir AI yanıtını bitirince (ya da sorunla bitince) o sayfanın noktasında küçük bir işaret çıkar; sayfaya geçince kaybolur. Durum bilgisi WebMonitor'dan gelir.

# Web arayüzünde dosya yükleme
- WebView'a dosya seçici (`onShowFileChooser`) bağlandı: sayfadaki "dosya ekle / yükle" düğmesi artık sistemin dosya seçicisini açar, seçilen dosyalar sayfaya verilir (AI sohbetleri ve tam ekran Canlı web'de, dokunma açıkken).
- Seçici yalnız ekranda görünen sayfada açılır; otomasyon (zip ekleme) etkilenmez. Seçici iptal edilirse ya da ekrandan çıkılırsa bekleyen istek kapatılır, düğme yeniden çalışır.

# LeakCanary bildirimleri yerine çökme günlüğü (yalnız debug)
- LeakCanary'nin kendi bildirimleri ve toast'ları kapatıldı.
- Uygulama sızıntısı ya da analiz hatası bulununca ayrıntı **Ayarlar > Çökme günlüğü**ne yazılır ve tek bir kısa bildirim çıkar: "Hata tespit edildi — Ayarlar > Çökme günlüğü bölümüne bakarak hatayı görebilirsiniz."
- Aynı sızıntı bir daha bildirilmez/kaydedilmez (kalıcı imza listesi); bildirim sabit kimlikli olduğu için yığılmaz. Sistem/kütüphane sızıntıları sayılmaz.
- Release derlemesinde etkisi yok (`src/debug` ve `src/release` içindeki `DebugTools`).

# Çökme düzeltmesi (PatternSyntaxException)
- Android'in ICU düzenli ifade motoru `}}` gibi kaçışsız kapanış süslü parantezini reddediyor (`PromptTemplate` başlatılırken çökme). Şablon deseni tamamen kaçışlı hâle getirildi: `\{\{([^\{\}\n]+?)\}\}`. Kodda başka kaçışsız süslü parantezli desen kalmadı (tüm Regex'ler tarandı).

# AI sohbetleri: tablet düzeni
- Tablette (≥600dp) AI sohbetleri iki bölmeli: solda liste, sağda seçilen AI'ın web arayüzü. Başka AI seçilince önceki sayfa kapanır (çerezler kaydedilir), yenisi açılır.
- Yan çubuğa (ray ve geniş çekmece) **AI sohbetleri** sekmesi eklendi; sohbet açıkken de yan çubuk görünür. Telefon düzeni değişmedi.

# AI sohbetleri (WhatsApp benzeri)
- Ana ekrandaki **AI sohbetleri** düğmesi: web hesaplarının (Claude, ChatGPT, Kimi) listesi; avatar, ad, durum, açık olana yeşil nokta.
- Satıra dokununca o AI'ın web arayüzü ekranı dolduracak boyutta (donanım katmanlı, akıcı kaydırma, klavyeyle yeniden boyutlanır) açılır.
- Oturum düşmüşse giriş sayfası açılır; burada giriş yapıp çıkınca oturum kaydedilir ve hesap "hazır" olur.
- Ekrandan çıkınca oturum kapanır (çalışmalar o hesabı bekleyip kullanabilsin). Hesap bir çalışmada kullanılıyorsa sayfa yeniden açılmaz/boyutlanmaz (iş bozulmasın); Canlı web'e yönlendirilir.

# Bu sürümdeki değişiklikler (Canlı web)

## Canlı web: tam ekran
- "Sayfa" sekmesine **⤢ Tam ekran** düğmesi eklendi: AI'ın web sayfası tüm ekranı kaplar, sistem çubukları gizlenir.
- Üstte tek bir küçük **✕** düğmesi (yanında Sığdır / 0,5× / 1× / Dokunma) kalır; geri tuşu da tam ekrandan çıkarır.
- Tam ekrana geçerken WebView yeniden kurulmaz (oturum/yazılan prompt bozulmaz).

## Web oturumu düşünce
- Kayıtlı oturum kabul edilmeyip sayfa giriş ekranına (ör. claude.ai/login) düşerse artık 45 sn beklenmez: hemen "oturum düşmüş" bildirilir, hesap yeniden giriş ister ve Canlı web'de uyarı görünür.
- Oturum kapanırken GİRİŞSİZ (anonim) çerezler, çalışan eski oturumun üzerine yazılmaz.

# Bu sürümdeki değişiklikler

## 1. Web arayüzü: "This browser is out of date"
- `WebUserAgent` artık sabit Chrome/130 yerine cihazdaki GERÇEK WebView sürümünü okur (okunamazsa 142). Sec-CH-UA bilgisi de aynı sürümle verilir.

## 2. AI çıktısı: önce sakla → işe yarayanı al → gerisini sil (kod ASLA silinmez)
- Yeni `RawReplyLog`: her AI yanıtı ve AI'ın verdiği özgün dosyalar (zip dahil) işlenmeden ÖNCE diske yazılır.
- Yeni `OutputTriage`: yanıttan yalnız dosya blokları alınır; sohbet yazısı/gürültü çalışma metnine girmez.
- Alışveriş bitince ham yanıtlar yalnız kod bloklarına indirilir (birebir). Kod sayısı doğrulanmadan ham dosya silinmez.
- Elenen eski/yinelenen sürümler de arşivde kalır (`_kripton/raw/replies-code.md`, seri modda zip içinde).
- Seri mod günlüğü gerçek rakamı yazar: "ham N karakter → kullanışlı M karakter".
- Canlı sayaç düğme/simge/ekran-okuyucu metnini saymaz.
- Seri mod ekranına "AI çıktılarını zip olarak kaydet" düğmesi eklendi (özgün zip'ler + ayıklanan kod).

## 3. GitHub / Actions
- Repo zip'i uzun süreli istemciyle iner (2 dk sınırı kalktı); boş/bozuk zip kabul edilmez, yeniden denenir.
- Yeni sohbet açılırken dalın ucundaki TAMAMLANMIŞ Actions sonucu okunur; hata varsa tüm hata ve uyarılar AI'a eksiksiz verilir.
- Hata/uyarı sayısı sınırı (120/80), 24.000 karakter kesmesi, "ilk 30 uyarı", "son 40 satır" sınırları kaldırıldı.
- Gradle `* What went wrong:` bölümü satır satır tam alınır.
