package com.jagadaun.app.ui.nearby

import org.junit.Assert.assertEquals
import org.junit.Test

/** Attribution must stay readable: raw licence URLs are not acceptable in a card. */
class LicenseLabelTest {
    @Test
    fun `scheme-less creative commons url becomes a short label`() {
        assertEquals("CC BY-NC 4.0", licenseLabel("creativecommons.org/licenses/by-nc/4.0"))
        assertEquals("CC BY-SA 4.0", licenseLabel("https://creativecommons.org/licenses/by-sa/4.0/"))
        assertEquals("CC0 1.0", licenseLabel("creativecommons.org/publicdomain/zero/1.0"))
        assertEquals("Domain publik", licenseLabel("https://creativecommons.org/publicdomain/mark/1.0/"))
    }

    @Test
    fun `plain licence names pass through unchanged`() {
        assertEquals("CC BY-SA", licenseLabel("CC BY-SA"))
        assertEquals("All rights reserved", licenseLabel("  All rights reserved "))
    }
}
