# Sürüm yayınlama rehberi

## Bir kerelik kurulum (imzalı APK için)

1. Keystore üret (JDK 17 gerekir):
   ```bash
   ./scripts/create-keystore.sh kripton
   ```
   Betik `release.keystore` dosyasını oluşturur ve secret değerlerini yazdırır.
2. `release.keystore` dosyasını ve parolayı güvenli bir yere yedekle. Kaybedersen aynı uygulamayı güncelleyemezsin. Dosya `.gitignore` ile repoya girmez.
3. GitHub'da **Settings → Secrets and variables → Actions → New repository secret** ile şunları ekle:

   | Secret | Değer |
   | --- | --- |
   | `KEYSTORE_BASE64` | Betiğin yazdırdığı base64 satırı |
   | `KEYSTORE_PASSWORD` | Girdiğin parola |
   | `KEY_PASSWORD` | Aynı parola |
   | `KEY_ALIAS` | Takma ad (varsayılan `kripton`) |
4. **Settings → Actions → General → Workflow permissions** altında "Read and write permissions" seçili olsun (workflow zaten `contents: write` ister; kuruluş politikası kısıtlıyorsa burada açılmalı).

## Her sürümde

1. `CHANGELOG.md` içindeki "Yayınlanmamış" başlığını yeni sürüm adıyla güncelle.
2. Etiketi it:
   ```bash
   git tag v0.2.0
   git push origin v0.2.0
   ```
3. Actions sekmesinde **Release** çalışmasını izle; bitince Releases sayfasında APK ve `.sha256` görünür.

Önsürüm için `v0.2.0-beta.1` biçimini kullan. Aynı etiketle workflow'u yeniden çalıştırırsan mevcut release güncellenir.

## Kurallar

- Etiket biçimi: `vMAJOR.MINOR.PATCH` veya `vMAJOR.MINOR.PATCH-ek`.
- `versionCode = MAJOR*10000 + MINOR*100 + PATCH`; bu yüzden MINOR ve PATCH en fazla 99 olabilir.
- İmza secret'ları yoksa debug imzalı APK yayınlanır (yalnızca test için). Debug ve release imzası farklı olduğu için biri üstüne diğeri güncelleme olarak kurulamaz.

## İlk deneme

Gerçek sürümden önce `v0.0.1-test` etiketini it, sonucu kontrol et, sonra release ve etiketi sil:
```bash
git push origin v0.0.1-test
# kontrolden sonra:
gh release delete v0.0.1-test --cleanup-tag --yes
```
