package com.ltemapper.app.ui.map

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.DpRect
import com.ltemapper.app.domain.model.CellSignalInfo
import com.ltemapper.app.domain.model.LocationSample
import com.ltemapper.app.domain.model.RanType
import com.ltemapper.app.domain.model.SignalQuality
import com.ltemapper.app.ui.theme.LteMapperTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the map screen against the overlap regressions it actually shipped with: the legend
 * covering the display-mode tabs, and the dashboard sitting under the start button / attribution.
 *
 * Runs on the JVM via Robolectric, so a real device isn't needed to catch layout collisions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class MapScreenLayoutTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val busyState = MapUiState(
        isMapping = true,
        // Deliberately verbose values: long operator name, 7-digit cell id — the exact content
        // that caused text to collide on a real phone.
        currentSignal = CellSignalInfo(
            networkType = RanType.LTE,
            connectionState = "REGISTERED",
            isRegistered = true,
            rsrpDbm = -116,
            rsrqDb = -18,
            pci = 28,
            earfcn = 1675,
            cellId = 7066706L,
            tac = 12345,
            operatorName = "Free Mobile Long Operator Name",
        ),
        currentSignalQuality = SignalQuality.VERY_WEAK,
        currentLocation = LocationSample(48.85, 2.35, 9f),
        measurementCount = 128_456,
        distanceMeters = 37_204.0,
        mappingDurationMillis = 42 * 60_000L,
        showDashboard = true,
    )

    private fun setContent(state: MapUiState = busyState, legendVisible: Boolean = true) {
        composeRule.setContent {
            LteMapperTheme(dynamicColor = false) {
                MapScreenContent(
                    state = state,
                    legendVisible = legendVisible,
                    onToggleLegend = {},
                    onSetDisplayMode = {},
                    onSetNetworkFilter = {},
                    onToggleFollowUser = {},
                    onCycleTileSource = {},
                    onToggleDashboard = {},
                    onStartMapping = {},
                    onStopMapping = {},
                    // The real map is an osmdroid AndroidView; a plain Box stands in for it so the
                    // test measures our own layout rather than the tile renderer.
                    mapContent = { Box(Modifier.fillMaxSize()) },
                )
            }
        }
    }

    private fun DpRect.toRect() = Rect(left.value, top.value, right.value, bottom.value)

    private fun assertNoOverlap(a: DpRect, b: DpRect, what: String) {
        // Two empty rectangles never "overlap", so without this the assertions below could pass
        // for a screen that rendered nothing at all.
        assertTrue("$what: first node has no area ($a)", !a.toRect().isEmpty)
        assertTrue("$what: second node has no area ($b)", !b.toRect().isEmpty)
        val overlaps = a.toRect().overlaps(b.toRect())
        assertFalse("$what overlap:\n  a=$a\n  b=$b", overlaps)
    }

    @Test
    fun `legend does not cover the display mode tabs`() {
        setContent(legendVisible = true)
        val tabs = composeRule.onNodeWithText("Heatmap", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val legend = composeRule.onNodeWithText("Signal quality (RSRP, dBm)", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertNoOverlap(tabs, legend, "Legend and display-mode tabs")
    }

    @Test
    fun `legend does not cover the network filter chips`() {
        setContent(legendVisible = true)
        val chips = composeRule.onNodeWithText("All cellular", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val legend = composeRule.onNodeWithText("Signal quality (RSRP, dBm)", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertNoOverlap(chips, legend, "Legend and network filter")
    }

    @Test
    fun `dashboard does not overlap the start-stop button`() {
        setContent()
        val dashboard = composeRule.onNodeWithText("RSRP", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val button = composeRule.onNodeWithText("Stop", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertNoOverlap(dashboard, button, "Dashboard and start/stop button")
    }

    @Test
    fun `attribution does not overlap the start-stop button`() {
        setContent()
        val attribution = composeRule.onNodeWithText("© OpenStreetMap contributors", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val button = composeRule.onNodeWithText("Stop", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertNoOverlap(attribution, button, "Attribution and start/stop button")
    }

    @Test
    fun `map style buttons do not overlap the display mode tabs`() {
        setContent(legendVisible = false)
        val tabs = composeRule.onNodeWithText("Heatmap", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val layersButton = composeRule.onNodeWithContentDescription("Change map style", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertNoOverlap(tabs, layersButton, "Layer button and display-mode tabs")
    }

    @Test
    fun `everything stays inside the screen bounds`() {
        setContent()
        val root = composeRule.onRoot().getUnclippedBoundsInRoot()
        listOf("Stop", "RSRP", "Heatmap").forEach { text ->
            val node = composeRule.onNodeWithText(text, useUnmergedTree = true).getUnclippedBoundsInRoot()
            assertFalse("$text overflows the right edge ($node vs $root)", node.right > root.right)
            assertFalse("$text overflows the bottom edge ($node vs $root)", node.bottom > root.bottom)
        }
    }

    @Test
    fun `collapsed dashboard still shows the essentials without overlap`() {
        setContent(state = busyState.copy(showDashboard = false))
        val rsrp = composeRule.onNodeWithText("RSRP", useUnmergedTree = true).getUnclippedBoundsInRoot()
        val button = composeRule.onNodeWithText("Stop", useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertNoOverlap(rsrp, button, "Collapsed dashboard and start/stop button")
    }
}
