package com.recallflow.app.platform

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.recallflow.app.data.AttachmentEntity
import com.recallflow.app.data.AttachmentType
import java.io.File
import java.util.UUID

class AttachmentStore(private val context: Context) {
    private val attachmentDir: File = File(context.filesDir, "attachments").apply { mkdirs() }

    fun importUri(itemId: String, source: Uri): AttachmentEntity? {
        val resolver = context.contentResolver
        val name = resolver.query(source, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
            ?: "资料-${System.currentTimeMillis()}"
        val mime = resolver.getType(source) ?: "application/octet-stream"
        val type = when {
            mime.startsWith("image/") -> AttachmentType.IMAGE
            mime == "application/pdf" -> AttachmentType.PDF
            else -> AttachmentType.DOCUMENT
        }
        val extension = name.substringAfterLast('.', "")
        val fileName = "${UUID.randomUUID()}${if (extension.isBlank()) "" else ".${extension}"}"
        val target = File(attachmentDir, fileName)
        return runCatching {
            resolver.openInputStream(source)?.use { input -> target.outputStream().use(input::copyTo) }
                ?: return null
            AttachmentEntity(
                itemId = itemId,
                type = type,
                displayName = name,
                mimeType = mime,
                relativePath = "attachments/$fileName",
                sizeBytes = target.length()
            )
        }.getOrElse {
            target.delete()
            null
        }
    }

    fun addLink(itemId: String, url: String, title: String): AttachmentEntity = AttachmentEntity(
        itemId = itemId,
        type = AttachmentType.LINK,
        displayName = title.ifBlank { url },
        url = url.trim()
    )

    fun openUri(attachment: AttachmentEntity): Uri? {
        if (attachment.type == AttachmentType.LINK) return attachment.url?.let(Uri::parse)
        val path = attachment.relativePath ?: return null
        val file = File(context.filesDir, path)
        if (!file.exists()) return null
        return FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    }

    fun delete(attachment: AttachmentEntity) {
        attachment.relativePath?.let { File(context.filesDir, it).delete() }
    }
}
