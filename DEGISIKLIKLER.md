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
