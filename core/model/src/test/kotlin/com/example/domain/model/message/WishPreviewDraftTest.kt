package com.example.domain.model.message

import org.junit.Assert.assertEquals
import org.junit.Test

class WishPreviewDraftTest {

    @Test
    fun variantText_returnsRequestedVariantAndFallsBackToStandard() {
        val variants = WishPreviewVariants(
            short = "Short draft",
            standard = "Standard draft",
            long = "Long draft",
            formal = "Formal draft",
            funny = "Funny draft",
            emotional = "Emotional draft",
        )

        assertEquals("Funny draft", variants.textFor(" funny "))
        assertEquals("Emotional draft", variants.textFor("EMOTIONAL"))
        assertEquals("Standard draft", variants.textFor("unknown"))
    }
}
