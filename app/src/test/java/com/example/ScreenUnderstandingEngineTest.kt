package com.example

import android.graphics.Rect
import com.example.automation.ScreenElement
import com.example.automation.ScreenSnapshot
import com.example.automation.ScreenUnderstandingEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ScreenUnderstandingEngineTest {

    private val engine = ScreenUnderstandingEngine()

    @Test
    fun testGenerateConciseSummaryWithPopulatedSnapshot() {
        val snapshot = ScreenSnapshot(
            packageName = "com.google.android.youtube",
            activityName = "WatchActivity",
            visibleTexts = listOf("Naruto Shippuden Episode 1", "Subscribe", "Comments (250)"),
            clickableElements = listOf(
                ScreenElement(text = "Play", isClickable = true),
                ScreenElement(text = "Pause", isClickable = true),
                ScreenElement(contentDescription = "Search", isClickable = true),
                ScreenElement(text = "Subscribe", isClickable = true)
            ),
            editableFields = listOf(
                ScreenElement(text = "Add a comment...", isEditable = true)
            ),
            scrollableContainers = listOf(
                ScreenElement(className = "androidx.recyclerview.widget.RecyclerView", isScrollable = true)
            ),
            allElements = listOf(
                ScreenElement(text = "Naruto Shippuden Episode 1", isClickable = true),
                ScreenElement(contentDescription = "Search", isClickable = true),
                ScreenElement(text = "Add a comment...", isEditable = true)
            )
        )

        val summary = engine.generateConciseSummary(snapshot)
        assertTrue(summary.contains("YouTube", ignoreCase = true))
        assertTrue(summary.contains("Naruto", ignoreCase = true))
    }

    @Test
    fun testGenerateConciseSummaryEmptySnapshot() {
        val snapshot = ScreenSnapshot(
            packageName = "",
            activityName = "",
            visibleTexts = emptyList(),
            clickableElements = emptyList(),
            editableFields = emptyList(),
            allElements = emptyList()
        )

        val summary = engine.generateConciseSummary(snapshot)
        assertEquals("The screen is currently blank or inaccessible. Please verify Accessibility permissions.", summary)
    }

    @Test
    fun testFindSemanticTargetSynonyms() {
        val element = ScreenElement(
            text = "Search",
            contentDescription = "Search",
            viewIdResourceName = "com.spotify.music:id/search_button",
            isClickable = true,
            bounds = Rect(100, 200, 500, 300)
        )
        val snapshot = ScreenSnapshot(
            packageName = "com.spotify.music",
            clickableElements = listOf(element),
            allElements = listOf(element)
        )

        val target = engine.findSemanticTarget("search", snapshot)
        assertNotNull(target)
        assertEquals(300, target?.element?.bounds?.centerX())
        assertEquals(250, target?.element?.bounds?.centerY())
    }

    @Test
    fun testResolveSmartResultFirstItem() {
        val snapshot = ScreenSnapshot(
            packageName = "com.google.android.youtube",
            allElements = listOf(
                ScreenElement(
                    text = "Home",
                    isClickable = true,
                    bounds = Rect(0, 0, 100, 50)
                ),
                ScreenElement(
                    text = "Alan Walker - Faded (Official Video)",
                    isClickable = true,
                    bounds = Rect(50, 200, 800, 400)
                ),
                ScreenElement(
                    text = "Alan Walker - Spectre",
                    isClickable = true,
                    bounds = Rect(50, 450, 800, 650)
                )
            )
        )

        val target = engine.resolveSmartResult("first", snapshot)
        assertNotNull(target)
        assertTrue(target!!.displayLabel.contains("Alan Walker - Faded"))
    }

    @Test
    fun testResolveSmartResultByQueryMatch() {
        val snapshot = ScreenSnapshot(
            packageName = "com.google.android.youtube",
            allElements = listOf(
                ScreenElement(
                    text = "Coldplay - Yellow",
                    isClickable = true,
                    bounds = Rect(50, 200, 800, 400)
                ),
                ScreenElement(
                    text = "Interstellar Theme Song (Hans Zimmer)",
                    isClickable = true,
                    bounds = Rect(50, 450, 800, 650)
                )
            )
        )

        val target = engine.resolveSmartResult("Interstellar", snapshot)
        assertNotNull(target)
        assertTrue(target!!.displayLabel.contains("Interstellar"))
    }
}
