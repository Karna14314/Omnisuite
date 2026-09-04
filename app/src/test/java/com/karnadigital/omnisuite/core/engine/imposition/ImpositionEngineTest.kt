package com.karnadigital.omnisuite.core.engine.imposition

import org.junit.Assert.*
import org.junit.Test

class ImpositionEngineTest {

    @Test
    fun `test N-Up 2x2 layout calculation`() {
        val config = ImpositionConfig(
            mode = ImpositionToolMode.N_UP,
            gridRows = 2,
            gridCols = 2,
            targetPaperSize = PaperSize(PaperPreset.A4),
            isLandscape = false
        )

        val layouts = ImpositionEngine.calculateLayout(
            pageCount = 8,
            sourceWidthPt = 595.28f,
            sourceHeightPt = 841.89f,
            config = config
        )

        // 8 pages with 2x2 (4 pages/sheet) should produce 2 sheets
        assertEquals(2, layouts.size)
        assertEquals(4, layouts[0].placements.size)
        assertEquals(4, layouts[1].placements.size)

        // Page indices should be in sequence 0, 1, 2, 3 on sheet 0
        assertEquals(0, layouts[0].placements[0].sourcePageIndex)
        assertEquals(1, layouts[0].placements[1].sourcePageIndex)
        assertEquals(2, layouts[0].placements[2].sourcePageIndex)
        assertEquals(3, layouts[0].placements[3].sourcePageIndex)

        // Page indices should be 4, 5, 6, 7 on sheet 1
        assertEquals(4, layouts[1].placements[0].sourcePageIndex)
        assertEquals(7, layouts[1].placements[3].sourcePageIndex)
    }

    @Test
    fun `test Booklet Saddle Stitch page order for Western Left Binding`() {
        val config = ImpositionConfig(
            mode = ImpositionToolMode.BOOKLET,
            bindingDirection = BindingDirection.LEFT_WESTERN,
            targetPaperSize = PaperSize(PaperPreset.A4)
        )

        val layouts = ImpositionEngine.calculateLayout(
            pageCount = 8,
            sourceWidthPt = 595.28f,
            sourceHeightPt = 841.89f,
            config = config
        )

        // 8 pages = 4 sheets (2 sheets per 4-page signature: Outer front/back, Inner front/back)
        assertEquals(4, layouts.size)

        // Sheet 0 (Outer front): Left = Page 8 (idx 7), Right = Page 1 (idx 0)
        val sheet0 = layouts[0]
        assertEquals(2, sheet0.placements.size)
        assertEquals(7, sheet0.placements[0].sourcePageIndex)
        assertEquals(0, sheet0.placements[1].sourcePageIndex)

        // Sheet 1 (Outer back): Left = Page 2 (idx 1), Right = Page 7 (idx 6)
        val sheet1 = layouts[1]
        assertEquals(2, sheet1.placements.size)
        assertEquals(1, sheet1.placements[0].sourcePageIndex)
        assertEquals(6, sheet1.placements[1].sourcePageIndex)
    }

    @Test
    fun `test Booklet Saddle Stitch page order for RTL Right Binding`() {
        val config = ImpositionConfig(
            mode = ImpositionToolMode.BOOKLET,
            bindingDirection = BindingDirection.RIGHT_RTL,
            targetPaperSize = PaperSize(PaperPreset.A4)
        )

        val layouts = ImpositionEngine.calculateLayout(
            pageCount = 8,
            sourceWidthPt = 595.28f,
            sourceHeightPt = 841.89f,
            config = config
        )

        // Sheet 0 (Front outer RTL): Left = Page 1 (idx 0), Right = Page 8 (idx 7)
        val sheet0 = layouts[0]
        assertEquals(2, sheet0.placements.size)
        assertEquals(0, sheet0.placements[0].sourcePageIndex)
        assertEquals(7, sheet0.placements[1].sourcePageIndex)
    }

    @Test
    fun `test Cards Duplex layout front and back alignment`() {
        val config = ImpositionConfig(
            mode = ImpositionToolMode.CARDS,
            cardMode = CardMode.DUPLEX,
            gridRows = 2,
            gridCols = 2,
            targetPaperSize = PaperSize(PaperPreset.A4)
        )

        val layouts = ImpositionEngine.calculateLayout(
            pageCount = 8,
            sourceWidthPt = 250f,
            sourceHeightPt = 150f,
            config = config
        )

        // 8 pages with duplex 2x2 cards = 2 sheets (Sheet 0 Front, Sheet 1 Back)
        assertEquals(2, layouts.size)

        // Sheet 0 (Front): Row 0 Col 0 -> Page 0; Row 0 Col 1 -> Page 2
        val frontPlacements = layouts[0].placements
        assertEquals(0, frontPlacements[0].sourcePageIndex)
        assertEquals(2, frontPlacements[1].sourcePageIndex)

        // Sheet 1 (Back): Mirrored horizontal alignment for back-to-back print registration
        val backPlacements = layouts[1].placements
        // Back placements: item 0 (col 0 mirrored to col 1) gets Page 1; item 1 (col 1 mirrored to col 0) gets Page 3
        assertEquals(1, backPlacements[0].sourcePageIndex)
        assertEquals(3, backPlacements[1].sourcePageIndex)
    }

    @Test
    fun `test 1-Sheet 8-Page Mini Zine layout`() {
        val config = ImpositionConfig(
            mode = ImpositionToolMode.ZINE,
            zineType = ZineType.ONE_SHEET_8PAGE,
            targetPaperSize = PaperSize(PaperPreset.A4)
        )

        val layouts = ImpositionEngine.calculateLayout(
            pageCount = 8,
            sourceWidthPt = 595.28f,
            sourceHeightPt = 841.89f,
            config = config
        )

        assertEquals(1, layouts.size)
        val sheet = layouts[0]
        assertEquals(8, sheet.placements.size)

        // Top row (4 placements) should be rotated 180 degrees
        for (i in 0 until 4) {
            assertEquals(180f, sheet.placements[i].rotationDegrees, 0.01f)
        }

        // Bottom row (4 placements) should be upright (0 degrees)
        for (i in 4 until 8) {
            assertEquals(0f, sheet.placements[i].rotationDegrees, 0.01f)
        }
    }
}
