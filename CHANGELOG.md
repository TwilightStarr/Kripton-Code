# Değişiklik Günlüğü

Biçim [Keep a Changelog](https://keepachangelog.com/tr/1.1.0/) yaklaşımını izler; sürümleme [SemVer](https://semver.org/lang/tr/) iledir.
Her sürümün otomatik notları ayrıca GitHub Releases sayfasında yayınlanır.

## [Yayınlanmamış]

### Eklendi
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
