package com.jagadaun.app.ui.identify

import com.jagadaun.app.domain.HealthCandidate
import com.jagadaun.app.domain.HealthGuides
import com.jagadaun.app.domain.HealthResult
import org.junit.Assert.assertSame
import org.junit.Test

class AnalysisHealthGuideTest {
    private val aphid = HealthCandidate("APHIGO", "Aphis gossypii", 0.8)

    @Test
    fun `uncertain result uses general steps even for a recognized high score candidate`() {
        assertSame(
            HealthGuides.general,
            analysisHealthGuide(HealthResult(listOf(aphid), uncertain = true), aphid),
        )
    }

    @Test
    fun `low score alternative never borrows the confident top candidate guide`() {
        val weakAlternative = aphid.copy(score = 0.1)
        assertSame(
            HealthGuides.general,
            analysisHealthGuide(HealthResult(listOf(aphid, weakAlternative), uncertain = false), weakAlternative),
        )
    }

    @Test
    fun `no result no selection and unknown code all use general steps`() {
        val result = HealthResult(listOf(aphid), uncertain = false)
        val unknown = aphid.copy(code = "UNKNOWN")
        assertSame(HealthGuides.general, analysisHealthGuide(null, aphid))
        assertSame(HealthGuides.general, analysisHealthGuide(result, null))
        assertSame(HealthGuides.general, analysisHealthGuide(HealthResult(listOf(unknown), false), unknown))
    }

    @Test
    fun `selection from an old photo cannot activate specific steps`() {
        val current = aphid.copy(code = "PSECCI")
        assertSame(
            HealthGuides.general,
            analysisHealthGuide(HealthResult(listOf(current), uncertain = false), aphid),
        )
    }

    @Test
    fun `recognized nonuncertain selected hypothesis receives sourced specific steps`() {
        assertSame(
            HealthGuides.forCode(aphid.code),
            analysisHealthGuide(HealthResult(listOf(aphid), uncertain = false), aphid),
        )
    }
}
