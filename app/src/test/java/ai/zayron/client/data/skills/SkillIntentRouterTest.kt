package ai.zayron.client.data.skills

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SkillIntentRouterTest {
    @Test fun routesClearImageToVideoRequests() {
        assertEquals(ZayronSkill.IMAGE_TO_VIDEO, SkillIntentRouter.classify("Please create a video from this picture"))
    }

    @Test fun routesImageEditingRequests() {
        assertEquals(ZayronSkill.IMAGE_INPAINT_ERASER, SkillIntentRouter.classify("Remove the background from this image"))
    }

    @Test fun routesDocumentRequests() {
        assertEquals(ZayronSkill.DOCUMENT_AI, SkillIntentRouter.classify("Can you summarize this PDF?"))
    }

    @Test fun routesAppBuilderRequests() {
        assertEquals(ZayronSkill.APP_BUILDER, SkillIntentRouter.classify("Help me build an app"))
    }

    @Test fun routesVoiceRequests() {
        assertEquals(ZayronSkill.VOICE_ASSISTANT, SkillIntentRouter.classify("Start voice mode"))
    }

    @Test fun leavesOrdinaryQuestionsInConversation() {
        assertNull(SkillIntentRouter.classify("What is a video and how is it made?"))
        assertNull(SkillIntentRouter.classify("Explain how databases work"))
    }

    @Test fun blankMessagesAreNotRouted() {
        assertNull(SkillIntentRouter.classify("   "))
    }
}
