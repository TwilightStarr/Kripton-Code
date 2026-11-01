package com.kriptoncod.app.data.shizuku;

import android.os.ParcelFileDescriptor;

/**
 * Shizuku'nun başlattığı ayrıcalıklı süreçte (adb = shell/2000, root = 0) çalışan salt-okunur dosya servisi.
 * Yanıtlar düz metindir (bkz. PrivProtocol); hata satırları "ERR:<KOD>" ile başlar.
 */
interface IPrivFileService {
    /** Shizuku'nun zorunlu kıldığı yıkım çağrısı (işlem kodu sabittir). */
    void destroy() = 16777114;

    /** Servisin çalıştığı süreç kimliği: 0 = root, 2000 = adb. */
    int uid() = 1;

    /** [offset]'ten başlayarak en çok [limit] satır: "d|f \t boyut \t değişiklik(ms) \t ad". */
    String list(String path, int offset, int limit) = 2;

    /** "d|f \t boyut \t değişiklik(ms)" ya da "ERR:<KOD>". */
    String stat(String path) = 3;

    /** Dosyayı salt okunur açar; açılamazsa null. */
    ParcelFileDescriptor openRead(String path) = 4;

    /** Kurulu paket adları, satır satır ("ERR:<KOD>" olabilir). */
    String packages() = 5;
}
