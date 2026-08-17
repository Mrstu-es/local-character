package com.localcharacter.app.data.model

import android.content.Context
import android.net.Uri
import com.localcharacter.app.domain.model.ModelDescriptor
import java.io.File
import java.io.FileOutputStream

/** Creates a stable native-readable path when a document provider cannot support mmap or reopen. */
class ModelStorageManager(private val context: Context) {
    private val directory = File(context.filesDir, "models")

    fun ownedPath(uriValue: String): String? {
        val uri = Uri.parse(uriValue)
        if (uri.scheme != "file") return null
        val file = uri.path?.let(::File) ?: return null
        return file.takeIf(::isOwned)?.takeIf(File::isFile)?.absolutePath
    }

    fun createPrivateCopy(source: Uri, model: ModelDescriptor): File {
        if (!directory.exists() && !directory.mkdirs()) error("No se pudo crear el almacenamiento local de modelos.")
        val reserve = 256L * 1024 * 1024
        if (model.sizeBytes > 0 && directory.usableSpace < model.sizeBytes + reserve) {
            error("No hay espacio suficiente para preparar una copia local del GGUF.")
        }
        val target = File(directory, "${model.id}.gguf")
        val temporary = File(directory, "${model.id}.gguf.part")
        check(isOwned(target) && isOwned(temporary))
        runCatching {
            context.contentResolver.openInputStream(source)?.use { input ->
                FileOutputStream(temporary, false).use { output -> input.copyTo(output, 1024 * 1024) }
            } ?: error("Android no pudo leer el GGUF seleccionado.")
            if (model.sizeBytes > 0 && temporary.length() != model.sizeBytes) {
                error("La copia del GGUF quedó incompleta (${temporary.length()} de ${model.sizeBytes} bytes).")
            }
            if (target.exists() && !target.delete()) error("No se pudo reemplazar la copia local anterior.")
            if (!temporary.renameTo(target)) error("No se pudo finalizar la copia local del GGUF.")
        }.onFailure {
            temporary.delete()
            throw it
        }
        return target
    }

    fun deleteIfOwned(uriValue: String) {
        val uri = Uri.parse(uriValue)
        val file = uri.path?.let(::File) ?: return
        if (uri.scheme == "file" && isOwned(file)) file.delete()
    }

    fun toStoredUri(file: File): String = Uri.fromFile(file).toString()

    private fun isOwned(file: File): Boolean {
        val root = directory.canonicalFile.path + File.separator
        return file.canonicalFile.path.startsWith(root)
    }
}
