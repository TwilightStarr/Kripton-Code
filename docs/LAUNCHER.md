# Launcher (masaüstü modu)

## Kripton neden var?
Kripton Code, birden çok yapay zekâyı (web hesapları ve API anahtarlarıyla) tek bir düzende çalıştırıp kod üreten bir Android uygulamasıdır:
plan → düğümler → paralel üretim → birleştirme → GitHub'a gönderme → CI düzeltme. Anahtarlar ve çerezler cihazda kalır.

## Launcher kullanıcıya ne sağlar?
Telefon ya da tablette **ana ekran** olarak ayarlandığında Kripton, AI'lara ve işlerine en kısa yolu sunar.

- **AI'lara tek dokunuş:** ana ekranın altındaki AI çubuğu (durum noktalı); "AI'a sor" sayfası: AI seç, yaz ya da dosya ekle, gönder.
  Herhangi bir uygulamada **Paylaş > Kripton Code** metni doğrudan bu sayfada açar. Aramaya yazılan her şey "X'e sor" olarak sunulur.
- **Ana ekran:** sayfalar; uygulama kısayolları, Kripton panoları (saat, AI'a sor, çalışma durumu, depolama, bildirim özeti) ve
  başka uygulamaların Android widget'ları. Uzun basış: taşı / kaldır / alt çubuğa sabitle. Boşluğa uzun bas: widget ekle, ayarlar.
- **Çekmece + evrensel arama:** canlı uygulama listesi (kurulum/kaldırma anında güncellenir), harf çubuğu, A-Z / en çok kullanılan / en yeni,
  gizli uygulamalar, uygulama kısayolları. Arama: uygulama, dosya, kişi (isteğe bağlı), hesap makinesi (`12*(3+4)`), sistem ayarları, AI'a sor, web.
  Türkçe harfler (ş, ı, ğ…) fark etmeden eşleşir.
- **Kendi bildirim sistemimiz:** yukarı/aşağı kaydırınca açılan panel. Kripton olayları (çalışma bitti / hata / hesap bekleniyor /
  duraklatıldı) Kripton'un kendi kutusunda durur; sistem bildirimleri ("Bildirim erişimi" verilirse) uygulamaya göre gruplanır, yana
  kaydırınca silinir, satır içi yanıt çalışır. Simgelerde rozet. Hızlı düğmeler: Wi-Fi, Bluetooth, ses, fener, tema, ayarlar.
  Kripton ana ekran ise ve erişim verilmişse Kripton olayları sistem gölgesinde tekrarlanmaz (kapatılabilir).
- **Dosyalar + arşiv:** kendi dosya yöneticisi; zip, tar, tar.gz, gz için içine bak, seçip çıkar, tek dosya önizle, zip/tar.gz oluştur,
  ilerleme çubuğu ve iptal. Güvenli: `../` yolları engellenir, üzerine yazılmaz. Parolalı, 7z ve rar desteklenmez ("Şununla aç" ile harici uygulama).
- Duvar kağıdı (kendi görselin ya da sistem), karartma, sütun/simge boyutu, hareketler: **Ana ekran uzun basış > Launcher ayarları**.

## Gizlilik
Bildirimler bellekte tutulur, diske yazılmaz, ağa gitmez. Dosya araması yalnız ad ve yol dizinler. Kişi araması varsayılan kapalıdır ve
izin ister. Widget'lar kendi uygulamalarının sürecinde çalışır.

## Kod haritası
`launcher/` (saf mantık: `search/`, `home/`, `notify/`, `LauncherPrefs`), `launcher/ui/` (Compose ekranları),
`data/files/ArchiveTool.kt`, `ui/desktop/ArchiveBrowser.kt`, kabuk bağlantısı `ui/desktop/DesktopShell.kt`.
Ayarlar `ThemeStore` içindedir (`launcherPrefsFlow`, `homeLayoutFlow`).
