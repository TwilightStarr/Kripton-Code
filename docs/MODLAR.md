# Mod Merkezi

Antigravity Tools'taki "zamanlama modu + hazır ayar (preset)" mantığının Kripton Code karşılığı.
Daha önce kodda sabit olan sınırlar artık bir **mod** içinde toplanır; mod tek dokunuşla seçilir.

## Nereden?
- Ana ekran: **Etkin mod** kartı (çiplerle anında değiştir).
- Run ve Seri mod başlatma pencereleri: mod çipleri.
- Ayarlar > Modlar, ya da ana ekrandaki karta dokun: tam ekran Mod Merkezi.

## Hazır modlar
| Mod | Amaç |
| --- | --- |
| Dengeli | Eski varsayılan davranış (hiçbir şey değişmez) |
| Hızlı | Tam paralel, kısa beklemeler, az deneme |
| Sabırlı (gece) | Az paralel, 100 deneme, uzun beklemeler |
| Kota tasarrufu | Tek düğüm, hatalı hesabı çabuk dinlendirir, ek AI çağrıları kapalı |
| Titiz (kalite) | Test yazdırır, uyarıları da düzeltir, çok düzeltme turu |

Hazır modlar silinmez; "Kopyala ve düzenle" ile kendi modunu çıkarırsın (en çok 30 özel mod).
Modlar metin olarak dışa aktarılır/panodan içe aktarılır (içe alınanlar yeni kopya olur).

## Ayarlar
Akış: paralel düğüm, deneme sayısı, ilk/en uzun bekleme, hesap yokken bekleme.
Havuz: devre kesici (art arda hata, dinlendirme), sağlıksız eşiği, sağlık penceresi.
Seri: CI düzeltme turu, uyarı turu, devam et turu, bitti mi sorusu, GitHub/oturum/sayfa hata sınırları.
Otomasyon: ara kayıt (WIP dalı), eksik testi AI yazsın.

Değişiklik çalışan işlerde bir sonraki adımda geçerli olur (`ModeRuntime`).

## İlerleme düzeltmeleri
- Run: plan bitince %100'e sıçrayıp düşme giderildi; planlama %5, düğümler %80 (birleştirme 0,25 ağırlık), gönderme/CI son %15. Başarısız düğüm "bitti" sayılmaz; tahmini süre gösterilir. Bildirim, widget ve Run ekranı aynı hesabı kullanır (`RunProgress`).
- Seri: süren prompt artık bitmiş sayılmaz; prompt/commit/CI/düzeltme aşamaları yüzdeye girer (`SerialProgress`).
