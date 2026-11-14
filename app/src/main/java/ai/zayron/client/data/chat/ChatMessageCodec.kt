package ai.zayron.client.data.chat

import ai.zayron.client.ui.chat.ChatAttachmentReference
import ai.zayron.client.ui.chat.ChatMessage
import ai.zayron.client.ui.chat.ChatRole
import ai.zayron.client.ui.chat.DeliveryState
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object ChatMessageCodec {
    fun encode(messages: List<ChatMessage>): String {
        val array = JSONArray()
        messages.forEach { message ->
            val attachments = JSONArray()
            message.attachments.forEach { attachment ->
                attachments.put(
                    JSONObject()
                        .put("id", attachment.id)
                        .put("displayName", attachment.displayName)
                        .put("mimeType", attachment.mimeType)
                        .put("sizeBytes", attachment.sizeBytes)
                        .put("storagePath", attachment.storagePath)
                )
            }
            array.put(
                JSONObject()
                    .put("id", message.id)
                    .put("text", message.text)
                    .put("role", message.role.name)
                    .put("deliveryState", message.deliveryState.name)
                    .put("attachments", attachments)
            )
        }
        return array.toString()
    }

    fun decode(serialized: String?): List<ChatMessage> {
        if (serialized.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(serialized)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val text = item.optString("text")
                    val attachmentArray = item.optJSONArray("attachments") ?: JSONArray()
                    val attachments = buildList {
                        for (attachmentIndex in 0 until attachmentArray.length()) {
                            val attachment = attachmentArray.optJSONObject(attachmentIndex) ?: continue
                            val id = attachment.optString("id")
                            val name = attachment.optString("displayName")
                            val mime = attachment.optString("mimeType")
                            val path = attachment.optString("storagePath")
                            val size = attachment.optLong("sizeBytes", -1L)
                            if (id.isNotBlank() && name.isNotBlank() && mime.isNotBlank() &&
                                path.isNotBlank() && size > 0L
                            ) add(ChatAttachmentReference(id, name, mime, size, path))
                        }
                    }
                    if (text.isBlank() && attachments.isEmpty()) continue
                    add(
                        ChatMessage(
                            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                            text = text,
                            role = enumValueOrDefault(item.optString("role"), ChatRole.USER),
                            deliveryState = enumValueOrDefault(
                                item.optString("deliveryState"), DeliveryState.LOCAL_ONLY
                            ),
                            attachments = attachments
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T =
        try { enumValueOf<T>(value) } catch (_: IllegalArgumentException) { default }
}
