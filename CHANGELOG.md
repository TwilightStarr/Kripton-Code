# Değişiklik Günlüğü

Biçim [Keep a Changelog](https://keepachangelog.com/tr/1.1.0/) yaklaşımını izler; sürümleme [SemVer](https://semver.org/lang/tr/) iledir.
Her sürümün otomatik notları ayrıca GitHub Releases sayfasında yayınlanır.

## [Yayınlanmamış]

### Değişti (tema)
- Zümrüt yeşil vurgu kaldırıldı; tüm vurgu, durum ("tamamlandı", "canlı", "önerilen") ve rozet renkleri logo gibi gri/beyaz tonlara çevrildi. Yalnız hata rengi kırmızı kalır.
- Koyu temanın zemini saf siyah (#000000): AMOLED ekranda pikseller kapanır. Kartlar/yüzeyler siyahtan birkaç kademe açık gri; Material "tint" kapatıldı, menü ve iletişim kutuları griye boyanmaz.
- Açılış ekranı, pencere zemini ve widget siyah/gümüşe uyarlandı; AI sağlayıcı avatarları gri-beyaz farklı tonlarla ayrışır.
- Açık tema korundu (beyaz/grafit).

### Eklendi (çalışma alanı)
- **Düzen kaydetme:** açık AI'lar, pencere boyutları, sabit pencere, hazır düzen ve "Odağı büyüt" seçimi saklanır; çalışma alanı açılınca aynı düzen gelir. Silinmiş hesapların pencereleri geri gelmez; bozuk kayıt çökmeye değil boş düzene yol açar.
- **Hazır düzenler** (üstte "Düzenler"): "Eşit böl" ve "Biri büyük, diğerleri yan şeritte" (sayfadaki ilk pencere büyük, kalanlar yan şeritte alt alta; boyut tutamacı bu düzende de çalışır).
- **Sabitleme (pin):** pencere başlığındaki iğne ile bir AI her sayfada görünür; diğerleri kalan yerlerde sayfalanır.
- **Yer değiştirme:** başlığı sürükleyip başka pencerenin üstüne bırakınca iki pencere yer değiştirir (boyutlar yuvada kalır). Sabit pencere sürüklenmez.
- **Çift dokunuşla tam ekran:** başlığa çift dokunmak büyütür / geri alır.
- **Bildirim rozeti:** başka sayfadaki bir AI yanıtını bitirince (ya da sorunla bitince) o sayfanın noktasında küçük bir işaret çıkar; sayfaya geçince kaybolur. Durum bilgisi WebMonitor'dan gelir.

### Eklendi (Canlı web)
- **Canlı web ekranı:** akış ve seri modda açık olan web sohbetlerinin (Claude, ChatGPT, Kimi) sayfası istenen an izlenebilir. Oturum başına durum (sayfa açılıyor / prompt gönderiliyor / AI çıktı veriyor / yanıt alındı / sorun), son promptun uzunluğu ve ne kadar önce gönderildiği, AI çıktısının uzunluğu ve takılma uyarısı (60 sn yeni çıktı yok), her turun prompt ve yanıt özeti gösterilir. Sayfa sabit 1280x2000 düzeninde ölçeklenerek gösterilir (otomasyon bozulmaz); dokunma varsayılan kapalıdır. Kapanan oturumların kaydı (son 8) kalır; hiçbir şey diske yazılmaz.
- Yanıt beklenirken sayfadan yalnız okuyan hafif bir yoklama (`WebLiveProbe`) ~3 sn'de bir çalışır.
- Run, seri mod ve ana ekrana **Canlı web** düğmesi eklendi.

### Eklendi (hesapların aynı anda çalışması)
- Bir run artık hazır düğümleri **aynı anda** yürütür: açık ve hatasız hesap sayısı kadar (en çok `PromptDispatcher.MAX_PARALLEL` = 6) düğüm paralel çalışır; bir hesapta aynı anda tek düğüm vardır. Tüm hesaplar meşgulse düğüm hataya/beklemeye düşmez, hesap boşalınca başlar. Bağımlılıklar (`[[girdi:]]`, birleştirmeler) aynen korunur.
- **Web oturumları paralel**: WebView sürümü çoklu profili destekliyorsa her hesap kendi profilinde (ayrı çerez/depo) açılır; desteklenmiyorsa eski sıralı davranış sürer.

### Düzeltildi (işler hiç başlamıyordu)
- **Uygulama içi yürütücü** (`InAppDispatcher`): run artık yalnız WorkManager işçisine bağlı değil; uygulama açıkken kendi süreci içinde de yürütülür. İşçi hiç başlamasa ya da sessizce ölse bile düğümler ilerler. Açılışta ve bekçi taramasında açık run'lar otomatik sürdürülür. Aynı run'ı iki yürütücü birlikte işlemez (`PromptDispatcher.process` koruması).
- `Error` türü hatalar (NoClassDefFoundError vb.) artık yakalanır: düğüm sebebiyle birlikte "Hata" olur, işçi sessizce ölmez.
- Web düğümlerinde düğüm ayrıntısında "hangi hesapta ne bekleniyor" notu görünür.

### Eklendi (gelişmiş prompt motoru)
- **Değişkenler**: `[[değişken: ad = değer]]` + `{{ad}}` / `{{ad|varsayılan}}`; yerleşikler (`{{başlık}}`, `{{sıra}}`, `{{tarih}}`, `{{deneme}}`…). Yalnız değişken tanımlayan prompt AI çağırmaz. Tanınmayan `{{...}}` kod şablonları bozulmaz.
- **Çıktı bağlama**: `{{çıktı:Başlık}}`, `{{özet:}}`, `{{dosya:Başlık#yol}}`, `{{yollar:}}`, `{{blok:}}`; kaynak bitene kadar beklenir, yeniden denemede bağlı düğümler sıfırlanır (`RetryPlanner`). `[[kaynak: zorunlu]]` ile eksik kaynakta düğüm AI çağrılmadan düşer.
- **Doğrulama + yeniden deneme kuralları**: `[[doğrula: ...]]` (min/max/içerir/içermez/regex/dosya/yer-tutucu-yok/kesilmemiş), `[[tekrar]]`, `[[tekrar-bekle]]`, `[[tekrar-hesap]]`, `[[tekrar-ipucu]]`, `[[hatada]]`, `[[hata-tekrar]]`.
- Prompt düzenleyicide yönerge/kaynak uyarıları. Ayrıntı: `docs/PROMPT-ENGINE.md`. Veritabanı şeması değişmedi.
### Eklendi (kullanım ve maliyet panosu)
- **Token takibi:** Her istek artık girdi/çıktı tokenını kaydeder. Sağlayıcı `usage` bildirirse gerçek değer (akışta `stream_options.include_usage`, bunu destekleyen OpenAI, OpenRouter, Groq, Mistral, DeepSeek'e gönderilir); bildirmezse başarılı istekte karakterden **tahmin** (≈4 karakter = 1 token) ve "tahmini" diye işaretlenir. Başarısız istekte bilgi yoksa bilinmiyor (null) kalır, uydurulmaz. Reddedilen/boş yanıtta sağlayıcının faturaladığı token da kaydedilir.
- **Kullanım ve maliyet ekranı** (Ana sayfa > Kullanım kartı): 24 saat / 7 gün / 30 gün; toplam token, girdi/çıktı dağılımı, saatlik ya da günlük token grafiği, hesap başına istek, token, günlük kota doluluğu, dinlenme durumu ve tahmini maliyet.
- **İsteğe bağlı fiyat:** Hesap düzenleyicide 1M token başına girdi/çıktı fiyatı (USD). Fiyat girilmeyen hesapta maliyet gösterilmez; toplam eksikse uyarılır.
- Altyapı: `TokenMeter` (coroutine bağlamıyla taşınır; `AiGenerator` imzası ve sahteler değişmedi), `UsageReport` (saf hesap), veritabanı **v7** (`usage_events` token alanları, `ai_accounts` fiyat alanları; 6→7 migrasyonu mevcut verileri korur, eski kayıtlar "bilinmiyor" olur).

### Eklendi (havuz görünürlüğü)
- **AI'lar ekranında havuz durumu**: her hesap kartı artık havuzdaki sırasını (yedekler zincirin sonunda), neden dinlendiğini (sağlayıcı sınırı / günlük kota / devre kesici) ve **ne zaman açılacağını**, son 24 saatteki istek sayısını (`37 / 100`) ve "yakın zamanda çok hata verdi" uyarısını gösterir. Üstte tek satırlık özet: aktif · yedek · dinleniyor · hatalı. Durum 30 saniyede bir tazelenir.
- Mantık `PoolStatus` içinde, `AccountSelector` ile aynı kurallar ve aynı sıralamayla çalışır (ekranda görünen sıra ile işlerin gerçekte seçildiği sıra ayrışamaz). Veritabanı şeması değişmedi.

### Değişti (debug)
- LeakCanary'nin ana ekrana eklediği ayrı **"Leaks" simgesi** kaldırıldı (`leak_canary_add_launcher_icon=false`, yalnız debug). Sızıntı bildirimleri çalışmaya devam eder.

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
