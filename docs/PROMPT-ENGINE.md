# Gelişmiş prompt motoru

Her prompt metninin **en başına** `[[anahtar: değer]]` satırları yazılabilir; ilk yönerge olmayan satırdan itibaren görevin kendisi başlar. Veritabanı değişikliği gerekmez. Yönergeler AI'a gönderilmeden önce metinden çıkarılır.

## Değişkenler
```
[[değişken: uygulama = Kervan Çağı]]
[[değişken: dil = Kotlin]]
```
- `{{uygulama}}`, `{{dil|Kotlin}}` (varsayılanlı) olarak kullanılır. Ad büyük/küçük harf duyarsızdır.
- Bir değişken, tanımlandığı prompttan **sonraki** (ve aynı) tüm promptlarda geçerlidir; sonraki tanım öncekini ezer.
- Yalnız değişken tanımlayan prompt (gövde boş) AI çağırmaz, kota harcamaz ve birleştirmeye girmez.
- Değer başka değişkenlere başvurabilir (`{{uygulama}} v2`); döngü ve aşırı derinlik korumalıdır.
- Yerleşikler (TR/EN): `{{run_id}}`/`{{çalıştırma}}`, `{{title}}`/`{{başlık}}`, `{{catalog}}`/`{{katalog}}`, `{{index}}`/`{{sıra}}`, `{{date}}`/`{{tarih}}`, `{{attempt}}`/`{{deneme}}`.
- Tanınmayan `{{...}}` (Vue/Handlebars/Jinja kodu) olduğu gibi kalır. Kaçış: `\{{ad}}`.

## Çıktıyı sonrakine bağlama
| Yazım | Anlamı |
| --- | --- |
| `{{çıktı:Başlık}}` | önceki düğümün tam çıktısı (varsayılan en çok 12.000 karakter; `{{çıktı:Başlık\|4000}}`) |
| `{{özet:Başlık}}` | arayüz özeti (dosya yolları + bildirim satırları) |
| `{{dosya:Başlık#yol/a.kt}}` | çıktıdaki tek dosya |
| `{{yollar:Başlık}}` | çıktıdaki dosya yolları |
| `{{blok:Başlık}}` | ilk kod bloğunun içeriği |
| `[[girdi: A, B]]` | A ve B'nin arayüz özetini göreve başa ekler (eski yönerge) |

- Başvurulan düğüm bitene kadar bu düğüm **beklenir** (yalnız daha önce sıralanmış promptlar). Bir düğüm yeniden denenirse ona bağlı düğümler de sıfırlanır.
- Kaynak yoksa/başarısızsa metne `[kaynak çıktı yok: X]` yazılır. `[[kaynak: zorunlu]]` ile bu durumda düğüm **AI çağrılmadan** düşer.
- Değişken değerinin içinde çıktı başvurusu desteklenmez.

## Doğrulama ve yeniden deneme
```
[[doğrula: min:200, içerir:"fun main", dosya, dosya:src/App.kt, yer-tutucu-yok, kesilmemiş]]
[[tekrar: 2]]            kural bozulursa en çok 2 kez yeniden üret (üst sınır 10)
[[tekrar-bekle: 10sn]]   denemeler arası bekleme (10sn, 2dk…; en çok 2 dk)
[[tekrar-hesap: aynı]]   varsayılan: değiştir (başka hesap; yoksa aynı hesap)
[[tekrar-ipucu: hayır]]  varsayılan: evet (reddedilme nedenleri isteme eklenir)
[[hatada: devam]]        varsayılan: dur (düğüm düşer); devam = son çıktıyı yine de kabul et
[[hata-tekrar: 5]]       sunucu/ağ hatasında bu düğüm için en çok deneme (genel sınır 60)
```
Kurallar: `min:N`, `max:N`, `içerir:X`, `içermez:X`, `regex:DESEN`, `dosya` / `dosya:yol`, `yer-tutucu-yok`, `kesilmemiş`.
Her yeniden üretim gerçek bir istektir (kota harcar); bu yüzden `tekrar` varsayılanı 0'dır. Kalite denemeleri sunucu hata sayacından düşülür.

İngilizce: `var`, `validate`, `retry`, `retry-delay`, `retry-account: same`, `retry-hint: no`, `on-fail: continue`, `error-retry`, `sources: required`.

## Notlar
- Prompt düzenleyici hatalı yönergeleri, bilinmeyen/sonradan sıralı kaynakları kırmızı uyarıyla gösterir.
- Planlayıcı AI ile katalog çıkarılıyorsa yönerge satırlarını değiştirebilir; yönergeli setlerde hazır katalog (zip) yolu daha güvenilirdir.
- Kimi hesaplarıyla çalışan düğümlerde doğrulama kuralları henüz uygulanmaz.
