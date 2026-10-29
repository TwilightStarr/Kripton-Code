# AI sohbetleri (WhatsApp benzeri)
- Ana ekrandaki **AI sohbetleri** düğmesi: web hesaplarının (Claude, ChatGPT, Kimi) listesi; avatar, ad, durum, açık olana yeşil nokta.
- Satıra dokununca o AI'ın web arayüzü ekranı dolduracak boyutta (donanım katmanlı, akıcı kaydırma, klavyeyle yeniden boyutlanır) açılır.
- Oturum düşmüşse giriş sayfası açılır; burada giriş yapıp çıkınca oturum kaydedilir ve hesap "hazır" olur.
- Ekrandan çıkınca oturum kapanır (çalışmalar o hesabı bekleyip kullanabilsin). Hesap bir çalışmada kullanılıyorsa sayfa yeniden açılmaz/boyutlanmaz (iş bozulmasın); Canlı web'e yönlendirilir.

# Bu sürümdeki değişiklikler (Canlı web)

## Canlı web: tam ekran
- "Sayfa" sekmesine **⤢ Tam ekran** düğmesi eklendi: AI'ın web sayfası tüm ekranı kaplar, sistem çubukları gizlenir.
- Üstte tek bir küçük **✕** düğmesi (yanında Sığdır / 0,5× / 1× / Dokunma) kalır; geri tuşu da tam ekrandan çıkarır.
- Tam ekrana geçerken WebView yeniden kurulmaz (oturum/yazılan prompt bozulmaz).

## Web oturumu düşünce
- Kayıtlı oturum kabul edilmeyip sayfa giriş ekranına (ör. claude.ai/login) düşerse artık 45 sn beklenmez: hemen "oturum düşmüş" bildirilir, hesap yeniden giriş ister ve Canlı web'de uyarı görünür.
- Oturum kapanırken GİRİŞSİZ (anonim) çerezler, çalışan eski oturumun üzerine yazılmaz.

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
