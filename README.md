# Kripton Cod

**Türkçe** · [English](README.en.md)

Android için kod odaklı, çoklu-AI üretim aracı. Büyük bir işi ayrıntılı küçük promptlara böl, her promptu farklı bir AI hesabına ata; sonuçlar sırayla üretilsin, zip olarak saklansın ve istersen GitHub'a gönderilip CI (Actions) ile doğrulansın.

## Özellikler

- **Çoklu AI hesabı:** Google Gemini, Groq, OpenRouter, Mistral, Cerebras, NVIDIA NIM, DeepSeek, Claude, OpenAI, Kimi ve OpenAI-uyumlu özel sağlayıcılar.
- **Kimlik doğrulama:** API anahtarı (tüm sağlayıcılarda) ve Gemini için Google OAuth. Claude, OpenAI ve Kimi yalnızca resmi API anahtarıyla bağlanır: bu şirketlerin web sohbet hesabını (e-posta/şifre) üçüncü taraf uygulamaya açan bir API'si yoktur ve web arayüzünü otomatikleştirmek kullanım koşullarına aykırıdır. Ücretsiz başlamak için Gemini, Groq, OpenRouter, Cerebras, Mistral veya NVIDIA NIM anahtarı kullanabilirsin (bkz. [docs/FREE-PROVIDERS.md](docs/FREE-PROVIDERS.md)).
- **Model seçimi:** Anahtar doğrulanınca uygun sohbet modelleri listelenir, ücretsiz olanlar öne çıkarılır.
- **Prompt setleri:** Sıralı promptlar oluştur, yukarı/aşağı taşı, her prompta otomatik veya manuel hesap ata.
- **Arka planda çalışma:** Üretim WorkManager ile sürer; uygulama kapansa da yarım kalan işler kurtarılır.
- **Geçmiş:** Üretilen çıktılar ve hazırlanan zip dosyaları cihazda saklanır.
- **GitHub + CI döngüsü:** Çıktıyı yeni bir dala tek commit olarak gönderir, Actions sonucunu bekler, hata varsa log özetini AI'a verip düzeltmeyi dener.
- **Tema:** Sistem / açık / koyu.
- **Güvenlik:** Anahtarlar ve token'lar Android Keystore ile AES-GCM şifrelenerek yalnızca cihazda tutulur; yedekleme kapalıdır.

## Gereksinimler

- Android 8.0 (API 26) veya üstü
- Geliştirme için: JDK 17, Android SDK 35 (Android Studio Ladybug veya üstü)

## Kurulum (kullanıcı)

1. [Releases](../../releases) sayfasından en son `KriptonCod-vX.Y.Z.apk` dosyasını indir.
2. Telefonda "Bilinmeyen kaynaklardan yükle" iznini ver ve APK'yı kur.
3. Dosya bütünlüğünü `.sha256` dosyasıyla doğrulayabilirsin.

## Derleme (geliştirici)

```bash
./gradlew assembleDebug          # debug APK: app/build/outputs/apk/debug/
./gradlew lintDebug              # lint
./gradlew testDebugUnitTest      # birim testler
```

Gradle wrapper (`gradle-wrapper.jar` dahil) repoda hazırdır; ayrıca Gradle kurmana gerek yok.

### Google OAuth (isteğe bağlı)

Gemini'yi Google hesabıyla kullanmak için Google Cloud Console'da Android tipinde bir OAuth istemci kimliği oluştur ve uygulamada **Ayarlar → Google OAuth** alanına gir.

## Kullanım

1. **AI'lar** sekmesinden bir sağlayıcı seç, anahtarını doğrula, modeli seç ve kaydet.
2. **Promptlar** sekmesinde bir set oluştur, promptları ekle ve hesap ata.
3. Set ekranındaki **Çalıştır** düğmesine bas. İstersen hedef GitHub reposunu seç; seçmezsen yalnızca zip üretilir.
4. İlerleme ve sonuçlar **Geçmiş** sekmesinde görünür; bir çalıştırmaya dokunup **Zip'i kaydet** ile zip'i telefonuna kaydedebilirsin.
5. GitHub'a göndermek için **Ayarlar → GitHub** altında bir token tanımla (`repo` ve `workflow` izinleri gerekir). Sonuç `kriptoncod/run-<id>` dalına tek commit olarak gider, doğrulama workflow'u çalışır; hata olursa AI düzeltmeyi aynı dala yeniden gönderir (deneme sayısı GitHub ayarlarında).

## Sürüm yayınlama (otomatik)

Etiket itmek yeterlidir:

```bash
git tag v0.2.0
git push origin v0.2.0
```

`Release` workflow'u testleri çalıştırır, APK'yı derler, SHA-256 üretir ve GitHub Releases'e sürüm notlarıyla yükler. `v1.0.0-beta.1` gibi etiketler önsürüm olarak işaretlenir.

Ayrıntılı adımlar için [docs/RELEASING.md](docs/RELEASING.md). İmzalı APK için repo **Settings → Secrets and variables → Actions** altına şunları ekle:

| Secret | Açıklama |
| --- | --- |
| `KEYSTORE_BASE64` | Keystore dosyasının base64 hali (`base64 -w0 release.keystore`) |
| `KEYSTORE_PASSWORD` | Keystore parolası |
| `KEY_ALIAS` | Anahtar takma adı |
| `KEY_PASSWORD` | Anahtar parolası |

Keystore'u `./scripts/create-keystore.sh` ile üretebilirsin. Secret'lar yoksa workflow debug imzalı APK yayınlar (yalnızca test için).

Sürüm notlarının kategorileri için PR'lara `feature`, `bug`, `docs`, `ci` gibi etiketler ver (`.github/release.yml`).

## Proje yapısı

```
app/src/main/java/com/kriptoncod/app/
├── data/      # sağlayıcılar, repo, veritabanı (Room), GitHub, kasa (Keystore)
├── domain/    # modeller ve tercihler
├── ui/        # Compose ekranları, bileşenler, tema, gezinme
└── work/      # WorkManager işçileri (dağıtım, kurtarma, CI döngüsü)
```

## Teknoloji

Kotlin 2.0, Jetpack Compose (Material 3), Room, DataStore, WorkManager, OkHttp, kotlinx.serialization.

## Lisans

[MIT](LICENSE). Değişiklikler için [CHANGELOG.md](CHANGELOG.md).

## Katkı

Hata bildirimleri için Issues sayfasını kullan. PR açarken değişikliğe uygun bir etiket ekle; sürüm notları buna göre gruplanır.
