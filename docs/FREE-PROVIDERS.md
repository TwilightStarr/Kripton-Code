# Ücretsiz AI sağlayıcıları (resmi API anahtarıyla)

Krypton Code yalnızca sağlayıcıların **resmi API'lerini** ve **kendi hesabından aldığın API anahtarını** kullanır. Web sohbet arayüzlerini otomatikleştirmez, başkasının anahtarını paylaşmaz.
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

## Neden e-posta/şifre ile giriş yok?

Claude, ChatGPT ve Kimi'nin web sohbet hesapları üçüncü taraf uygulamalara açılmaz; web arayüzünü otomatikleştirmek kullanım koşullarına aykırıdır ve hesabının kapanmasına yol açabilir. Bu yüzden bu sağlayıcılar yalnızca ücretli resmi API anahtarıyla bağlanır. Ücretsiz kullanım için yukarıdaki tablodaki sağlayıcıları kullan.

## Anahtar güvenliği

- Anahtarlar Android Keystore ile şifrelenip yalnızca cihazda tutulur.
- Anahtarı kimseyle paylaşma, ekran görüntüsüne ve repoya koyma.
- Sızdırdığını düşünürsen sağlayıcı konsolundan anahtarı hemen iptal et.
