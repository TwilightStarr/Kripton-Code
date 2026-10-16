# Ücretsiz AI sağlayıcıları (resmi API anahtarıyla)

Bu sayfadaki sağlayıcılar **resmi API anahtarıyla** çalışır. Ayrıca isteğe bağlı olarak web sohbet hesapları e-posta ile bağlanabilir (aşağıya bakın); bu yol sağlayıcı koşullarına aykırı olabilir.
Ücretsiz katman koşulları sık değişir; kayıt olmadan önce sağlayıcının güncel sayfasını oku.

| Sağlayıcı | Anahtar nereden | Uygulamada | Dikkat |
| --- | --- | --- | --- |
| Google Gemini | https://aistudio.google.com/apikey | Hazır (API anahtarı veya Google OAuth) | Ücretsiz katmanda gönderdiğin içerik ürün geliştirmede kullanılabilir; gizli kod/veri gönderme. |
| Groq | https://console.groq.com/keys | Hazır | Günlük/dakikalık istek sınırı vardır. |
| OpenRouter | https://openrouter.ai/settings/keys | Hazır (`:free` modeller öne çıkar) | Ücretsiz modeller değişir; hizmet şartları API erişiminin yeniden satışını yasaklar. |
| Mistral | https://console.mistral.ai/api-keys | Hazır | Ücretsiz katman deneme/geliştirme içindir; telefon doğrulaması isteyebilir. |
| Cerebras | https://cloud.cerebras.ai | Hazır | Ücretsiz katman istek sınırlıdır. |
| NVIDIA NIM | https://build.nvidia.com/settings/api-keys | Hazır (yeni) | Geliştirici hesabı gerekir; dakikada istek sınırı düşüktür (yaklaşık 40) ve deneme/geliştirme amaçlıdır. |

## Özel sağlayıcı ile eklenebilenler

Uygulamadaki **OpenAI-uyumlu (özel)** seçeneği HTTPS taban adresi ve API anahtarı ister. Örnekler:

- Cloudflare Workers AI: `https://api.cloudflare.com/client/v4/accounts/<HESAP_ID>/ai/v1` (Cloudflare API token'ı ile)
- Z.ai (GLM): `https://api.z.ai/api/paas/v4`. Ücretsiz Flash modelleri sunar, ancak model listesi uç noktasının varlığı kaynaklarda tutarsız; doğrulama başarısız olursa kullanma.

## E-posta ile giriş (web oturumu) — RİSKLİ, isteğe bağlı

**AI'lar → + E-posta ile**: sağlayıcının kendi giriş sayfası uygulama içinde açılır, sen e-postanla girersin (şifreyi uygulama görmez); oturum çerezleri cihazda şifreli saklanır. Prompt'lar o hesabın web sohbetine gönderilir. Birden fazla hesap eklenebilir; kota dolunca hesap "Bekliyor" olur ve iş başka hesaba kayar.

| Sağlayıcı | Çıktı |
| --- | --- |
| Claude | Sohbetin ürettiği zip indirilir ve açılır; zip yoksa kod blokları. **Zorunlu uyarı:** hesabın kapatılabilir. |
| ChatGPT | Üretilen dosya/zip indirilir; yoksa kod blokları. |
| Kimi | Uzun kod bloğu; kesilirse otomatik "devam et" ve parça birleştirme. |
| Gemini | Giriş sonrası AI Studio'dan hesabın API anahtarı alınır; çıktılar resmi API ile üretilir (web arayüzü kullanılmaz). **Zorunlu uyarı:** hesabın kapatılabilir. |

Riskler ve sınırlar:
- Claude, ChatGPT, Kimi ve Google koşulları web arayüzünün otomatik kullanımını yasaklayabilir; hesap uyarısız kısıtlanabilir/kapatılabilir. Ana hesabını kullanma.
- Web arayüzleri sık değişir. Seçiciler `web-selectors.json` ile (uygulama güncellemeden) ezilebilir: dosya `files/web-selectors.json`, biçim `WebSelectors.defaultsJson()` çıktısıyla aynıdır. Varsayılanlar canlı siteye karşı doğrulanmadı.
- Arka planda çalışırken Android ön plan servisi bildirimi gösterilir.

## Daha güvenli yol: Proxy

Kendi sunucunda [CLIProxyAPI](https://github.com/router-for-me/CLIProxyAPI) çalıştırıp **Proxy** hesabı olarak ekleyebilirsin; resmi CLI girişleriyle çalışır ve web arayüzünü otomatikleştirmez. (Sağlayıcıların bu kullanıma izin verip vermediğini kendin kontrol et; Anthropic abonelik belirteçlerinin üçüncü taraf uygulamalarda kullanımını yasakladı.)

## Anahtar güvenliği

- Anahtarlar Android Keystore ile şifrelenip yalnızca cihazda tutulur.
- Anahtarı kimseyle paylaşma, ekran görüntüsüne ve repoya koyma.
- Sızdırdığını düşünürsen sağlayıcı konsolundan anahtarı hemen iptal et.
