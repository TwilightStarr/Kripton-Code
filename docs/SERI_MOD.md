# Seri Mod (tek AI, tek sohbet)

Prompt seti ekranında **Seri modda çalıştır** düğmesi. Hesap (web oturumu) ve repo seçilir.

Akış (her prompt için):
1. Prompt aynı sohbete gönderilir (ilk mesajda projenin güncel hâli repodan zip olarak eklenir; eklenemezse metin olarak verilir).
2. Yanıt kesilirse "devam et" gönderilir; kota dolarsa süre dolunca kaldığı yerden sürer; hata olursa 1-2 dk sonra sayfa yenilenip aynı sohbette tekrar denenir.
3. Çıktı repoya doğrudan commit'lenir.
4. GitHub Actions beklenir. Hata/uyarı varsa günlük AYNI sohbete yapıştırılır, düzeltme commit'lenir, tekrar beklenir.
5. Temizse sonraki prompta geçilir.

Prompt başı yönergeleri (kendi satırlarında, AI'a gönderilmez):
- `[[yeni-sohbet]]` bu adım yeni sohbette başlar; projenin GÜNCEL hâli (repodan) yeni sohbete dosya olarak verilir.
- `[[dosyasız]]` bu adımdan dosya beklenmez (commit ve CI atlanır).

Durum diske yazılır (`files/serial/`); uygulama kapansa da kaldığı yerden sürer. Müdahale gerekirse (oturum düştü, token izni yok,
12 düzeltme turunda build temizlenmedi) çalışma "Müdahale bekliyor" olur; sorunu giderip **Devam et**'e basın.
