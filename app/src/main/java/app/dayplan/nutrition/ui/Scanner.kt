package app.dayplan.nutrition.ui

import android.content.Context
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Сканер штрихкодов из Google Play-сервисов: свой экран камеры, разрешение на камеру приложению не нужно.
 * При первом запуске сервисы докачивают модуль сканера.
 */
object Scanner {
    private val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A, Barcode.FORMAT_DATA_MATRIX)
        .enableAutoZoom()
        .build()

    /** onResult(raw) — отсканированная строка; null — сканирование отменено. */
    fun scan(context: Context, onResult: (String?) -> Unit, onError: (String) -> Unit) {
        GmsBarcodeScanning.getClient(context, options).startScan()
            .addOnSuccessListener { onResult(it.rawValue) }
            .addOnCanceledListener { onResult(null) }
            .addOnFailureListener { e ->
                if (e is MlKitException && e.errorCode == MlKitException.CODE_SCANNER_CANCELLED) onResult(null)
                else onError(e.message ?: "Сканер недоступен")
            }
    }
}
