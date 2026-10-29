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
