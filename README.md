<p align="center">
  <img src="docs/logo.svg" width="128" height="128" alt="Krypton Code logosu">
</p>

# Krypton Code

**Türkçe** · [English](README.en.md)

Android için kod odaklı, çoklu-AI üretim aracı. Büyük bir işi ayrıntılı küçük promptlara böl, her promptu farklı bir AI hesabına ata; sonuçlar sırayla üretilsin, zip olarak saklansın ve istersen GitHub'a gönderilip CI (Actions) ile doğrulansın.

## Özellikler

- **E-posta ile web hesabı (riskli, isteğe bağlı):** Claude · ChatGPT · Kimi · Gemini hesaplarını API anahtarı olmadan, kendi giriş sayfalarında e-postayla bağla; çok hesap, kota bekleme ve hesap rotasyonu otomatik. Claude ve Gemini'de hesap kapatma uyarısı zorunludur. Ayrıntı: [docs/FREE-PROVIDERS.md](docs/FREE-PROVIDERS.md).
- **Çoklu AI hesabı:** Google Gemini, Groq, OpenRouter, Mistral, Cerebras, NVIDIA NIM, DeepSeek, Claude, OpenAI, Kimi ve OpenAI-uyumlu özel sağlayıcılar.
- **Akıllı hesap havuzu:** Her prompt en uygun hesaba gider: önce en güçlü model, eşit modeller sırayla (en uzun süredir kullanılmayan), yakın zamanda sorun çıkaran hesaplar sona kalır, **yedek** hesaplar yalnız başka seçenek kalmayınca devreye girer. Hesap başına isteğe bağlı **günlük istek sınırı** ücretsiz kotaların tükenmesini önler; art arda üç sağlayıcı/ağ hatasında devre kesici hesabı 10 dakika dinlendirir. Arka planda (6 saatte bir, kota harcamadan) anahtarı reddedilen hesaplar havuzdan çıkarılır, düzelenler geri alınır. **AI'lar** ekranı her hesabın havuzdaki sırasını, neden dinlendiğini ve ne zaman açılacağını gösterir.
- **Model seçimi:** Anahtar doğrulanınca uygun sohbet modelleri listelenir, ücretsiz olanlar öne çıkarılır.
- **Prompt setleri:** Sıralı promptlar oluştur, yukarı/aşağı taşı, her prompta otomatik veya manuel hesap ata.
- **Arka planda çalışma:** Üretim WorkManager ile sürer; uygulama kapansa da yarım kalan işler kurtarılır.
- **Geçmiş:** Üretilen çıktılar ve hazırlanan zip dosyaları cihazda saklanır.
- **GitHub + CI döngüsü:** Çıktıyı yeni bir dala tek commit olarak gönderir, Actions sonucunu bekler, hata varsa log özetini AI'a verip düzeltmeyi dener.
- **Canlı akış:** API hesaplarında düğüm çıktısı üretilirken yazı yazı akar (SSE, `okhttp-sse`); iş bitince kalıcı çıktı devralır. Akışı desteklemeyen sağlayıcıda otomatik olarak tek yanıta düşer. Web oturumu hesapları akıtılmaz.
- **Bildirimden denetim ve ana ekran widget'ı:** Süren çalışma bildiriminde **Duraklat / İptal**, duraklatılmışta **Devam et / İptal** düğmeleri vardır. Jetpack Glance widget'ı çalışan run'ın durumunu, ilerlemesini ve üretilen düğümü uygulamayı açmadan gösterir.
- **İki dilli arayüz:** Türkçe (varsayılan) ve İngilizce (`values-en`); Android 13+ ta uygulama başına dil seçilebilir.
- **Yerel çökme günlüğü (ACRA):** Çökme ayrıntısı yalnızca cihazda saklanır, ağa hiçbir şey gönderilmez; Ayarlar'dan paylaşılabilir/silinebilir.
- **Kalite araçları:** CI'da ktlint (şimdilik yalnız rapor), debug derlemede LeakCanary, Dependabot ile haftalık bağımlılık güncellemesi.
- **Tema:** Sistem / açık / koyu.
- **Animasyonlar:** Durum anlatan logo (boşta, çalışıyor, bekliyor, başarı, hata, açılış), ekran geçişleri, kademeli belirme, basma efekti, çalışan düğümde nabız halkası. Ayarlar'dan kapatılabilir.
- **Pil kısıtlaması:** Ayarlar'da durum gösterilir; Android'in "kısıtlama yok" izni ve Xiaomi/Huawei/Oppo/Vivo/Samsung gibi cihazların kendi otomatik başlatma ekranları açılabilir.
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

## Proxy (CLIProxyAPI uyumlu) hesap türü

Kendi (ya da arkadaşının) [CLIProxyAPI](https://github.com/router-for-me/CLIProxyAPI) sunucusunu hesap olarak ekleyebilirsin: **Hesaplar → Ekle → Proxy (CLIProxyAPI uyumlu)**.

- **Bağlantı:** taban adres (`http://192.168.1.20:8317`, `/v1` otomatik eklenir) + sunucunun istemci anahtarı (`api-keys`). Anahtar yalnız KeyVault'ta şifreli durur; Room'da yalnız adres vardır.
- **Düz http** yalnız yerel/özel ağ adreslerinde (localhost, LAN, Tailscale, `.local`) kabul edilir; internet adresleri için **https** zorunludur. Adreste kullanıcı bilgisi/sorgu olamaz.
- **Birden çok hesap:** aynı sağlayıcı için istediğin kadar proxy hesabı eklenebilir (ör. "Benim hesabım", "Arkadaşım"); her birinin adresi, anahtarı ve modeli ayrıdır.
- **Sağlayıcı/model:** `/v1/models` listesi çekilir; Claude · Gemini · OpenAI · DeepSeek · Kimi · Diğer çipleriyle süzülür, hesap seçilen modelin sağlayıcısıyla kaydedilir.
- **Çıktı ayrıştırma:** yanıt **model kimliğine göre** seçilen normalizer'dan geçer (`data/ai/normalize`): muhakeme blokları (`<think>`, `thinking` parçaları, `reasoning_content`) atılır, özel belirteçler temizlenir, `finish_reason` sınıflanır. Kod blokları ```` ```yol ````, ```` ```kotlin yol ````, ```` ```kotlin:yol ````, `title="yol"`, ilk satır `// File: yol` ve önceki satır `**File: yol**` biçimlerinde okunur; 4'lü çitler ve iç içe markdown desteklenir.
- **Otomatik yeniden deneme:** boş/okunamayan yanıt, uzunlukta kesilen çıktı, kapanmamış kod bloğu ve (birleştirme isteklerinde) eksik kod bloğu aynı hesapla en çok 3 kez denenir; sonda eksik kod asla kaydedilmez.

## Düğüm grafiği ve katalog formatı

Bir prompt seti ya da zip çalıştırıldığında girdi kataloglara ayrılır, her katalog ayrı yapay zekalara dağıtılır, katalog sonuçları birleştirilir ve nihai çıktı tek yapay zekayla toplanır. Metin biçimi: `## KATALOG: ad` altında `### PROMPT: başlık` blokları. Zip biçimi: her klasör bir katalog, içindeki `.md`/`.txt` dosyaları birer prompt. Run ekranında düğümlere dokunarak çıktıyı canlı izleyebilir, kaydedebilir, run'ı duraklatıp sürdürebilirsiniz.

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

## Çıktı hattı ve akış yönergeleri

**Her AI için ayrı davranış:** Web sohbetinde Claude/ChatGPT önce zip üretir (indirilir, dosyalara açılır; ham zip `_kripton/raw/`, ikili dosyalar özgün yoluna saklanır); Kimi düz kod+metin verir (düşünme etiketleri temizlenir, yolsuz bloklar `unnamed/` altına kurtarılır). Tüm sağlayıcılarda kesilen yanıt "devam et" ile tamamlanır; `...`/"geri kalanı aynı" gibi yer tutucular aynı sohbette onarım turuyla düzeltilir (Kimi 3, ChatGPT 3, Claude 2 tur). API hesaplarında aynısı `RetryingAiGenerator` ile yapılır.

**Zip:** `_kripton/report.md` düğüm tablosunu ve uyarıları (yer tutucu, kesik çıktı, çakışma, birleştirmede düşen dosyalar) içerir. Birleştirme AI'ı bir dosyayı düşürürse kaynak düğümden tamamlanır; çok kısaltırsa tam sürüm `_kripton/alternatives/` altına konur.

**Akış yönergeleri** (prompt metninin başına, AI'a gönderilmeden önce çıkarılır):

```
[[girdi: Ana sayfa, Menü]]   bu prompt, başlığı verilen ÖNCEKİ promptların çıktısını da görür (onlar bitene kadar bekler)
[[ilet: hayır]]              bu promptun çıktısı katalog/nihai birleştirmeye gitmez
```

**Arayüz:** telefonda sekmeler (Grafik | Akış | Dosyalar), tablet/yatayda (≥700dp) üç bölme: grafik | seçili düğümün çıktısı | akış günlüğü ve dosya listesi.

**Giriş:** Google, gömülü WebView'da girişe izin vermez; "Google ile devam et" yerine sağlayıcının e-posta (kod/şifre) girişini kullanın. Kimi ve Gemini için API anahtarı en güvenilir yoldur.
