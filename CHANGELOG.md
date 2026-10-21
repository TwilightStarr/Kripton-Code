# Değişiklik Günlüğü

Biçim [Keep a Changelog](https://keepachangelog.com/tr/1.1.0/) yaklaşımını izler; sürümleme [SemVer](https://semver.org/lang/tr/) iledir.
Her sürümün otomatik notları ayrıca GitHub Releases sayfasında yayınlanır.

## [Yayınlanmamış]

### Değişti (dayanıklılık)
- **CI düzeltmesi artık kör değil**: istemde hatanın geçtiği dosyaların güncel içeriği ve projenin arayüz özeti var. Hesaplar güce göre sırayla denenir (hata/boş/kesik cevapta sıradaki hesaba geçilir) ve her CI turunda başlangıç hesabı kaydırılır. AI'ın büyük bir dosyayı yarıdan fazla kısaltması reddedilir; düzeltme zip'e de işlenir.
- **Sabır**: sunucu/ağ hatasında düğüm artık 60 denemeye kadar (bekleme 15 sn'den 15 dk'ya artar, toplam ~7-8 saat) yeniden denenir; önce havuzdaki yedek hesap denenir. Bağlam aşımı, kesik çıktı, geçersiz istek gibi kalıcı hatalarda da sıradaki hesaba/modele geçilir; hepsi başarısızsa düğüm düşer.
- **Android sınırları**: 15 dakikada bir çalışan **bekçi** (`RunWatchdogWorker`) zinciri kopmuş yarım run'ları (dağıtım ve CI izleme) yeniden kurar; kurtarma artık yalnız uygulama açılışına bağlı değil. Ön plan servisi (dataSync, Android 15 günlük ~6 saat kota) başlatılamazsa hata yutulmaz: kullanıcı bilgilendirilir ve iş 8 dakikalık dilimlerle sürer. WorkManager 2.10.0'a yükseltildi (Android 15 dataSync zaman aşımı yönetimi). Not: kullanıcı uygulamayı "Zorla durdur" ederse Android tüm zamanlanmış işleri siler; uygulama elle açılana dek hiçbir şey çalışamaz.
- **Ara kayıt**: her katalog birleştirmesi bitince katalogun dosyaları ve ham düğüm çıktıları GitHub'da `kriptoncod/wip-<runId>` dalına commit edilir (`[skip ci]`). Telefon kaybolsa bile iş depoda durur. Repo seçili ve token tanımlıysa çalışır; hata run'ı etkilemez.

### Değişti (güvenilir sonuç)
- **Kısmi sonuç (`PARTIAL`)**: nihai birleştirme bitse bile bir düğüm başarısız olduysa run artık yeşil "Tamamlandı" değil **Kısmi — onay bekliyor** olur. GitHub'a otomatik gönderilmez; Run ekranındaki "Eksik haliyle GitHub'a gönder" düğmesiyle onaylanır. Başarısız düğümler yeniden denenebilir.
- **CI doğrulaması**: projede `.kriptoncod/verify.sh` yoksa türe göre (Node, Python, Gradle/Android, Maven, Go, Rust) derleme + test betiği eklenir. Test yoksa betik "TEST YOK" ile bilerek başarısız olur. Tür tanınmazsa CI zayıf denetimi açık uyarıyla basar.
- **Test yazdırma**: push'tan önce projede test yoksa en güçlü hesaptan başlayarak AI'a yalnızca test dosyaları yazdırılır (kaynak kod, workflow ve verify.sh'a yazılamaz); sonuç `_kripton/tests.md` içinde raporlanır.
- **Birleştirme kodla**: katalog/nihai birleştirme dosya yoluna göre kodla yapılır; çakışmasız dosyalar AI'a gitmez ve düşemez. AI yalnız aynı yolda farklı içerik varsa, yalnız o dosyalarla çağrılır; boş/kısaltılmış cevapta en uzun sürüm korunur.
- `[[girdi: ...]]` artık önceki çıktının tamamı yerine **arayüz özeti** (yollar + bildirim satırları) verir.

### Değişti (tema)
- Tema seçenekleri yalnızca **Sistem / Koyu / Açık**. Eski kayıtlar (siyah, grafit, adaçayı) Koyu'ya, bilinmeyen değer Sistem'e düşer. Tema değişimi renk geçişiyle animasyonludur. Açılış ekranı koyu kalır (`values-night` yalnızca pencere zemini için, `values-night-v31` açılış temasını koyu tutar).

### Eklendi (pil kısıtlaması)
- **Ayarlar → Pil kısıtlaması**: durum gösterilir; Android'in "kısıtlama yok" izni (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) istenebilir; Xiaomi, Huawei, Oppo, Vivo, Samsung, OnePlus ve Asus cihazlarda üreticinin otomatik başlatma ekranı açılır.

### Eklendi (animasyonlar)
- Logo artık durumu anlatır: boşta, çalışıyor (altıgen üzerinde dolanan nokta), bekliyor, başarı, hata ve açılış. Çalıştırma başlığındaki logo run durumunu izler.
- Ekran geçişleri (kayma + solma), ana ekranda kademeli belirme, düğme/kart basma efekti, çalışan düğümde nabız halkası, açılış animasyonu. Hepsi **Ayarlar → Animasyonlar**'a bağlıdır.
- Hızlı e-posta ekleme: AI'lar ekranındaki "Tek dokunuşla ekle" düğmeleri sırayla belirir; hesap kaydedilince giriş ekranında logo başarı hareketi yapar ve ardından kapanır.

### Düzeltildi (açılış ekranı)
- Uygulama açılırken görünen koyu mavi ekran kaldırıldı: `values-night` kaynakları (`#0A1226`), Android 12+ için tanımlı siyah açılış temasını eziyordu. Gece moduna özel kaynaklar silindi; açılış artık her zaman düz siyah zemin + beyaz logodur.

### Eklendi (logo animasyonu ve animasyon ayarı)
- Çalışma sürerken logodaki düğüm noktası altıgenin çevresinde yumuşakça dolanır: geçtiği çizgiyi siler, çizgi noktanın arkasından geri gelir. Yan menü logosu, ana ekran logosu ve yükleme göstergeleri (dönen çemberlerin yerine) bunu kullanır; iş bitince nokta turunu tamamlayıp yerine oturur.
- **Ayarlar → Animasyonlar** anahtarı: logo animasyonu, yükleme göstergeleri ve ekran geçişlerinin hepsini kapatır. Android'in "animasyonları kaldır" ayarı da dikkate alınır.

### Değişti (ana ekran)
- Ana ekran yeniden tasarlandı: özet sayaçlar (AI hesabı / prompt seti / çalıştırma), sıradaki adımı gösteren hızlı başlangıç, sürmekte olan çalışma bandı, son çalıştırmalar ve "Nasıl çalışır?" kartı. Tablette iki sütun, telefonda tek sütun.

### Eklendi (e-posta ile web oturumu)
- Yeni hesap türü `WEB_SESSION`: **AI'lar → + E-posta ile**. Claude, ChatGPT, Kimi ve Gemini için sağlayıcının kendi giriş sayfası WebView'da açılır; oturum çerezleri KeyVault'ta (hesap başına) saklanır. Şifre saklanmaz.
- Claude ve Gemini için zorunlu "hesabın kapatılabilir" uyarısı (onay kutusu olmadan giriş açılmaz).
- Sağlayıcıya göre çıktı: Claude/ChatGPT zip indirme + açma (kod bloğuna düşme), Kimi uzun kod bloğu + otomatik "devam et" ve birleştirme, Gemini API anahtarıyla.
- Kota/limit iletisi tanınır (EN/TR/ZH), bekleme süresi çözülür, hesap "Bekliyor" olur ve iş başka hesaba kayar; hesap başına gönderim aralığı; oturum düşerse hesap hataya alınır ve yeniden giriş istenir.
- WorkManager ön plan servisi (dataSync) ve bildirim; seçiciler `web-selectors.json` ile ezilebilir.
- Not: web arayüzü seçicileri canlı siteye karşı doğrulanmadı; saf mantık birim testleriyle ve sahte sohbet sayfasıyla sınandı.

### Değişti (düğüm grafiği kullanılabilirliği)
- Grafik artık ekranın kalan tüm alanını kullanır; ayrıntı paneli grafiğin üzerine alttan (yarım yükseklikte) açılır, grafiği küçültmez. Kapatma düğmesi ve geri tuşu paneli kapatır; aynı düğüme tekrar dokunmak da kapatır.
- Sürükleyerek kaydırma ve iki parmakla yakınlaştırma/uzaklaştırma; sağ üstte **+ / − / Sığdır** düğmeleri. Seçilen düğüm panelin altında kalıyorsa görünür alana kaydırılır.
- Satır başına düğüm sayısı ekran genişliğine göre seçilir (telefonda ilk açılışta yatay kaydırma gerekmez); düğümler en az 48dp (dokunma hedefi), harfler kalın ve büyük; kısa bir açıklama (K/sayı/B/N) eklendi. Başlık bölümü sıkıştırıldı.

### Eklendi (Proxy hesap türü)
- Yeni hesap türü **Proxy (CLIProxyAPI uyumlu)**: taban adres + anahtar (anahtar KeyVault'ta şifreli); aynı sağlayıcı için birden çok hesap; hesap başına sağlayıcı/model seçimi (veritabanı şeması 4, `ai_accounts.baseUrl`, MIGRATION_3_4).
- Sağlayıcıya özel çıktı normalizer'ları (Claude, Gemini, OpenAI, DeepSeek, Kimi, genel); model kimliğine göre seçilir.
- Kod bloğu ayrıştırıcı genişletildi: dil+yol biçimleri, yorum/önceki satır etiketi, uzun çitler, iç içe markdown, kapanmamış blok tespiti. Yalnız dil etiketli bloklar artık dosya sayılmaz.
- Eksik/bozuk çıktıda otomatik yeniden deneme (`RetryingAiGenerator`, en çok 3 deneme).
- Düz http yalnız yerel/özel ağ adreslerine izinli (`network_security_config.xml` genel izin verir, sınır `ProxyProvider`'dadır).
- Not: ilk derlemede üretilen `app/schemas/.../4.json` dosyası commit edilmelidir.

### Eklendi (düğüm grafiği)
- Run artık düğüm grafiğiyle sunulur: katalog çıkarma → paralel çalışan yapay zekalar → katalog birleştirme → nihai birleştirme. Güçlü hesapların düğümü daha büyük çizilir; işler hesap gücüne göre dağıtılır.
- Yüzlerce prompt belirli bir formatta (`## KATALOG:` / `### PROMPT:` başlıkları ya da klasör başına bir katalog olan zip) kataloglara ayrılır; zip doğrudan **Zip'ten çalıştır** ile yüklenebilir.
- Kota/token bitince hata verilmez: ilgili iş bekler (Retry-After), başka hesaba geçilir, kota gelince otomatik sürer.
- Run istenildiği an **Duraklat / Devam et** ile yönetilir (veritabanı şeması 3, `Job` türü/katalog/başlık/gövde sütunları, MIGRATION_2_3).
- Her düğümün çıktısı iş biter bitmez grafikte görülür, tek tek kaydedilebilir; tüm çıktılar zip içinde (`_kripton/nodes/`) saklanır.
- Not: ilk derlemede üretilen `app/schemas/.../3.json` dosyası commit edilmelidir.

### Kaldırıldı
- Var olmayan sınıflara dayanan, hiçbir yerden açılmayan "Dağıtım" ekranı ve ViewModel'i.
- README dosyalarındaki "önerilen model" ve "öneriler" ifadeleri kaldırıldı.

### Düzeltildi
- Derlemeyi bozan hatalar: KDoc içinde `/**` içeren yorumlar (kapanmayan yorum), `\s`/`\x00` gibi geçersiz Kotlin kaçışları (`CodeBlockParser`, `PathSafety`, `CiLogParser`), `CiLoopWorker` söz dizimi, `FinalAiAssembler` içinde suspend olmayan yerden suspend çağrı, `RunFinalizer` tip uyumsuzluğu, `RunScreen` iptal düğmesinin suspend fonksiyonu doğrudan çağırması.
- `strings.xml` ile `strings_providers.xml` arasındaki yinelenen kaynaklar (derleme hatası) ve kaçışsız kesme işareti.
- `play-services-auth` sürümü geçerli bir sürüme (21.2.0) çekildi.
- Run durumu: CI başlatıldıktan sonra durum yanlışlıkla `DONE` ile eziliyordu; artık `CI_WAITING` kalır. CI döngüsü doğru dal adını (`kriptoncod/run-<id>`) kullanır ve sonsuza dek yeniden denemez.
- Google OAuth istemci kimliği Ayarlar'da değişince artık uygulamayı yeniden başlatmak gerekmez.
- Gradle wrapper (`gradle-wrapper.jar`, resmi `gradlew`/`gradlew.bat`) repoya eklendi.

### Eklendi
- Çıktıyı GitHub'a gönderme adımı (`RunPublisher`): yeni dal + tek commit + doğrulama workflow'u, ardından CI izleme.
- Set ekranında **Çalıştır** düğmesi ve hedef (yalnızca zip / GitHub reposu) seçimi.
- Geçmişten run ayrıntısına gitme; run ekranında **Zip'i kaydet** ve CI/push aşamalarında iptal.
- Ayarlar'dan GitHub ve İzinler ekranlarına bağlantı; GitHub ayarlarında CI düzeltme denemesi sayısı.
- Zip artık AI'ın ```yol/dosya bloklarını gerçek dosya yollarıyla içerir (blok yoksa ham çıktı `output_<id>.txt`).
- `CodeBlockParser`, `PathSafety`, `CiLogParser` ve `GitHubService.defaultBranch` için testler.
- Türkçe ve İngilizce README.
- Etiketle tetiklenen otomatik sürüm yayını (`.github/workflows/release.yml`), SHA-256 dosyası ve sürüm metni şablonu.
- İsteğe bağlı release imzalama (ortam değişkenleri/secret'lar) ve keystore üretim betiği (`scripts/create-keystore.sh`).
- Sürüm yayınlama rehberi (`docs/RELEASING.md`).
- NVIDIA NIM (ücretsiz geliştirici katmanı) sağlayıcısı eklendi; `docs/FREE-PROVIDERS.md` ücretsiz sağlayıcı ve yasal notlarla eklendi.

### Değişti
- Claude, OpenAI ve Kimi artık yalnızca API anahtarıyla bağlanır; çalışmayan e-posta/şifre ve Kimi oturum girişi katalogdan kaldırıldı. Kimi anahtar sayfası eklendi.
- `ProviderCatalogTest` güncel sağlayıcı listesine (11) ve Gemini'nin Google OAuth'una göre düzeltildi.
- `versionName` ve `versionCode` artık derleme sırasında `-PversionName` / `-PversionCode` ile verilebilir.

## [0.1.0]

### Eklendi
- İlk sürüm: çoklu AI hesabı, prompt setleri, arka plan üretimi, geçmiş, GitHub + CI döngüsü, tema ayarı.
