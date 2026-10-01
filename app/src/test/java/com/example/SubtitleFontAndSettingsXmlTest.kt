package com.example

import com.example.player.SubtitleFontManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import android.content.Context

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubtitleFontAndSettingsXmlTest {

  @Test
  fun testVideoEmbeddedFontPreservation() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    
    // 1. Clear any previous embedded fonts
    SubtitleFontManager.clearVideoEmbeddedFonts()
    
    // 2. Register video embedded font (e.g. from MKV attachment)
    SubtitleFontManager.registerVideoEmbeddedFont("AnimeCustomFont-Bold.ttf")
    
    // 3. Verify isFontAvailable recognizes it
    assertTrue(SubtitleFontManager.isFontAvailable(context, "AnimeCustomFont-Bold"))
    assertTrue(SubtitleFontManager.isFontAvailable(context, "AnimeCustomFont-Bold.ttf"))
    assertTrue(SubtitleFontManager.isFontAvailable(context, "animecustomfont-bold"))
    
    // 4. Test ASS script with this style
    val assScript = """
      [Script Info]
      Title: Test
      
      [V4+ Styles]
      Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
      Style: Default,AnimeCustomFont-Bold,48,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1
      
      [Events]
      Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
      Dialogue: 0,0:00:01.00,0:00:04.00,Default,,0,0,0,,Hello Anime Font
    """.trimIndent()
    
    val sanitized = SubtitleFontManager.sanitizeAssForFallback(context, assScript)
    
    // Font should NOT be replaced by fallback because it exists in the video's embedded fonts
    assertTrue("Declared embedded font should be preserved", sanitized.contains("AnimeCustomFont-Bold"))
    assertFalse("Should not fallback to Go Noto when font is embedded", sanitized.contains(SubtitleFontManager.BUNDLED_FALLBACK_FONT_FAMILY))
  }

  @Test
  fun testMissingFontFallsBackToBundledFallback() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    
    SubtitleFontManager.clearVideoEmbeddedFonts()
    
    val assScript = """
      [Script Info]
      Title: Test
      
      [V4+ Styles]
      Format: Name, Fontname, Fontsize, PrimaryColour, SecondaryColour, OutlineColour, BackColour, Bold, Italic, Underline, StrikeOut, ScaleX, ScaleY, Spacing, Angle, BorderStyle, Outline, Shadow, Alignment, MarginL, MarginR, MarginV, Encoding
      Style: Default,TotallyNonExistentFont12345,48,&H00FFFFFF,&H000000FF,&H00000000,&H00000000,0,0,0,0,100,100,0,0,1,2,2,2,10,10,10,1
      
      [Events]
      Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
      Dialogue: 0,0:00:01.00,0:00:04.00,Default,,0,0,0,,Hello Missing Font
    """.trimIndent()
    
    val sanitized = SubtitleFontManager.sanitizeAssForFallback(context, assScript)
    
    // Missing font should be sanitized to bundled fallback
    assertTrue("Should fallback to Go Noto Current-Regular", sanitized.contains(SubtitleFontManager.BUNDLED_FALLBACK_FONT_FAMILY))
    assertFalse("Unknown font should be replaced", sanitized.contains("TotallyNonExistentFont12345"))
  }

  @Test
  fun testSettingsXmlExportAndImport() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val initialState = com.example.ui.state.UiState(
        themeMode = com.example.ui.state.ThemeMode.DARK,
        glassBlurTransparency = 78f,
        language = com.example.ui.state.AppLanguage.HINDI,
        frameStepAmount = 5
    )

    val tempDir = context.cacheDir
    val exportResult = com.example.util.LumoraSettingsXmlManager.exportSettingsToFile(
        context,
        initialState,
        tempDir
    )

    assertTrue("Export should succeed", exportResult.isSuccess)
    val exportedFile = exportResult.getOrThrow()
    assertTrue("File should exist", exportedFile.exists())
    assertEquals(com.example.util.LumoraSettingsXmlManager.EXPORT_FILE_NAME, exportedFile.name)

    val parseResult = com.example.util.LumoraSettingsXmlManager.parseSettingsFile(
        context,
        exportedFile,
        com.example.ui.state.UiState()
    )

    assertTrue("Import parse should succeed", parseResult.isSuccess)
    val parsed = parseResult.getOrThrow()
    assertEquals(com.example.ui.state.ThemeMode.DARK, parsed.updatedUiState.themeMode)
    assertEquals(78f, parsed.updatedUiState.glassBlurTransparency, 0.01f)
    assertEquals(com.example.ui.state.AppLanguage.HINDI, parsed.updatedUiState.language)
    assertEquals(5, parsed.updatedUiState.frameStepAmount)
  }

  @Test
  fun testSettingsXmlCorruptDefensiveHandling() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val corruptFile = java.io.File(context.cacheDir, "corrupt.xml")
    corruptFile.writeText("<<>>not_valid_xml<<<")

    val parseResult = com.example.util.LumoraSettingsXmlManager.parseSettingsFile(
        context,
        corruptFile,
        com.example.ui.state.UiState()
    )

    assertFalse("Corrupted file must fail defensively without throwing crash", parseResult.isSuccess)
  }
}
