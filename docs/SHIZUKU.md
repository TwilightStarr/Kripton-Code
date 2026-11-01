# Shizuku ile sistem dosyaları

Masaüstü modu > Dosyalar penceresi, kullanıcı **Ayarlar > Shizuku ile sistem dosyaları**'nı açarsa Shizuku üzerinden
salt-okunur dosya erişimi kazanır.

## Ne yapar / yapmaz
- Yalnız **okur** (listeleme, boyut, açma). Silme, yazma, taşıma yoktur.
- Varsayılan **kapalı**. Kapalıyken Shizuku dinleyicisi eklenmez, servise bağlanılmaz.
- Dosya hiçbir yere kendiliğinden gönderilmez; yalnız kullanıcı onu bir AI sekmesine bırakınca o sekmenin web sayfasına verilir.
- Shizuku izni Shizuku'nun kendi onay ekranından, kullanıcı tarafından verilir.

## Erişim düzeyi (Android sınırı, uygulamanın seçimi değil)
| Shizuku modu | Okunabilen |
|---|---|
| root (uid 0) | `/data/data/<paket>` dâhil tüm sistem |
| adb (uid 2000) | `/storage/emulated/0/Android/data/<paket>`, `/data/local/tmp`, genel depolama; diğer uygulamaların `/data/data`'sı **kapalı** |

## Mimari
`ShizukuFiles` (uygulama) --binder--> `PrivFileService` (Shizuku'nun başlattığı süreç, `IPrivFileService.aidl`).
Düz metin protokolü `PrivProtocol` içindedir ve birim testlidir. Dosya içeriği `ParcelFileDescriptor` ile akar (Binder 1 MB sınırına takılmaz).

## Dağıtım notu (Play politikası)
Bu özellik GitHub/Obtainium APK dağıtımı için tasarlandı. Google Play'de yayınlanacaksa diğer uygulamaların verisine
erişim "kişisel/hassas veri" ve cihaz kısıtlarını aşma politikalarıyla çakışabilir; Play sürümünde bu bayrağı derleme
zamanında kaldırmanız (ya da yalnız kendi paket verisine kısıtlamanız) önerilir.
