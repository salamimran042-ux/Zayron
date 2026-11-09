package ai.zayron.client.data.skills

/**
 * Conservative, on-device intent routing for the six capabilities in the
 * single Zayron assistant. It never uploads text or starts a remote job.
 *
 * Only clear action phrases are routed; ordinary questions remain chat.
 */
enum class ZayronSkill(val workspaceTitle: String) {
    IMAGE_TO_VIDEO("Video Generation"),
    IMAGE_INPAINT_ERASER("Image Editing"),
    DOCUMENT_AI("Document AI"),
    APP_BUILDER("App Builder"),
    VOICE_ASSISTANT("Voice Assistant")
}

object SkillIntentRouter {
    private val routes = listOf(
        ZayronSkill.IMAGE_TO_VIDEO to listOf(
            "create a video", "make a video", "generate a video",
            "turn this image into a video", "animate this image", "image-to-video"
        ),
        ZayronSkill.IMAGE_INPAINT_ERASER to listOf(
            "inpaint", "erase the background", "remove the background",
            "remove an object", "erase this object", "edit this image"
        ),
        ZayronSkill.DOCUMENT_AI to listOf(
            "summarize this pdf", "summarize this document", "read this pdf",
            "analyze this document", "extract text from", "document ai",
            "ask this document", "review this pdf"
        ),
        ZayronSkill.APP_BUILDER to listOf(
            "build an app", "create an app", "make an app",
            "generate an android app", "app builder", "build me an android app"
        ),
        ZayronSkill.VOICE_ASSISTANT to listOf(
            "voice assistant", "start voice mode", "talk to me",
            "transcribe this audio", "speech to text", "use voice assistant"
        )
    )

    /** Returns null for general conversation or ambiguous wording. */
    fun classify(message: String): ZayronSkill? {
        val normalized = message.trim().lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), " ")
            .replace(Regex("\\s+"), " ")
        if (normalized.isBlank()) return null
        return routes.firstNotNullOfOrNull { (skill, phrases) ->
            if (phrases.any { phrase -> normalized.contains(phrase) }) skill else null
        }
    }
}
