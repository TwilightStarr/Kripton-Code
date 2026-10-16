# Değişiklik Günlüğü

Biçim [Keep a Changelog](https://keepachangelog.com/tr/1.1.0/) yaklaşımını izler; sürümleme [SemVer](https://semver.org/lang/tr/) iledir.
Her sürümün otomatik notları ayrıca GitHub Releases sayfasında yayınlanır.

## [Yayınlanmamış]

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
