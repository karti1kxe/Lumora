package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.AssOverridesStore
import com.example.player.AssRawTextStore
import com.example.player.PlayerMediaTrack
import com.example.player.RawSubtitleSource
import com.example.ui.state.PlayerSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Draft of the raw subtitle editor. It lives in the style panel (not in the editor composable) so
 * collapsing the section does not throw the typed text away; it is dropped when the panel closes.
 * Nothing in here is persisted: only Apply saves.
 */
internal class RawEditorDraft {
    var key by mutableStateOf<String?>(null)
    var text by mutableStateOf(TextFieldValue(""))

    /** The text that is currently SAVED (applied edit, otherwise the original file text). */
    var baseline by mutableStateOf("")
}

/**
 * "View Raw [Script Info] & Subtitle text": the whole text of the active subtitle (ASS/SSA,
 * SRT, VTT, ... uploaded, pasted, attached or embedded) in an editable, non-wrapping text area.
 *
 * - Every edit is previewed live on the video (nothing is saved).
 * - Apply saves the text for this subtitle; Reset puts the original file text back in the editor.
 * - Leaving without Apply discards the preview, the subtitle goes back to its saved look.
 */
@Composable
internal fun RawSubtitleEditorContent(
    settings: PlayerSettings,
    context: Context,
    activeSubtitleTrack: PlayerMediaTrack?,
    draft: RawEditorDraft,
    onLoadRawSubtitleSource: (suspend (Int) -> RawSubtitleSource?)?,
    onRawSubtitlePreview: (String?, String?) -> Unit,
    onUpdateSettings: ((PlayerSettings) -> PlayerSettings) -> Unit
) {
    val trackId = activeSubtitleTrack?.id
    val loadSource by rememberUpdatedState(onLoadRawSubtitleSource)
    val previewCallback by rememberUpdatedState(onRawSubtitlePreview)
    val latestSettings by rememberUpdatedState(settings)
    var source by remember { mutableStateOf<RawSubtitleSource?>(null) }
    var loadFinished by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // Whatever happens (panel closed, section collapsed, other track), an unapplied preview must go.
    DisposableEffect(Unit) {
        onDispose { previewCallback(null, null) }
    }

    LaunchedEffect(trackId) {
        loadFinished = false
        statusMessage = null
        previewCallback(null, null)
        val loader = loadSource
        val result = if (trackId == null || loader == null) {
            null
        } else {
            try {
                loader(trackId)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                null
            }
        }
        if (result == null) {
            source = null
        } else {
            val overrides = AssOverridesStore.get(latestSettings.assStyleOverridesJson, result.key)
            val applied = if (overrides.rawRevision > 0L) {
                withContext(Dispatchers.IO) { AssRawTextStore.read(context, result.key) }
            } else {
                null
            }
            val saved = applied ?: result.text
            if (draft.key != result.key) {
                // New subtitle: start from what is saved for it.
                draft.key = result.key
                draft.baseline = saved
                draft.text = TextFieldValue(saved)
            } else if (draft.baseline != saved) {
                // Same subtitle, but its saved text changed meanwhile.
                draft.baseline = saved
            }
            source = result
        }
        loadFinished = true
    }

    val current = source
    if (current == null) {
        RawInfoCard(
            when {
                trackId == null -> "Select a subtitle track to view its raw text."
                !loadFinished -> "Reading subtitle text…"
                else -> "The selected subtitle's text could not be read (image based, streamed or unsupported subtitle). Existing subtitle settings keep working normally."
            }
        )
        return
    }

    val currentText = draft.text.text
    val baseline = draft.baseline
    val isDirty = currentText != baseline

    // Live preview of the typed text on the video (debounced, never saved).
    LaunchedEffect(current.key, currentText, baseline) {
        delay(350)
        if (currentText == baseline) {
            // Back to the saved state. Give a just-applied edit time to reach the player first,
            // so the subtitle does not flicker back to the original for a moment.
            delay(450)
            previewCallback(null, null)
        } else {
            val hasApplied = AssOverridesStore.get(latestSettings.assStyleOverridesJson, current.key).rawRevision > 0L
            if (currentText == current.text && !hasApplied) {
                previewCallback(null, null)
            } else {
                previewCallback(current.key, currentText)
            }
        }
    }

    fun applyNow() {
        val text = draft.text.text
        if (text == draft.baseline) return
        scope.launch {
            val backToOriginal = text == current.text
            val ok = withContext(Dispatchers.IO) {
                if (backToOriginal) {
                    AssRawTextStore.delete(context, current.key)
                    true
                } else {
                    AssRawTextStore.write(context, current.key, text)
                }
            }
            if (!ok) {
                statusMessage = "Could not save the edited text."
                return@launch
            }
            onUpdateSettings { latest ->
                val existing = AssOverridesStore.get(latest.assStyleOverridesJson, current.key)
                latest.copy(
                    assStyleOverridesJson = AssOverridesStore.put(
                        latest.assStyleOverridesJson,
                        current.key,
                        existing.copy(rawRevision = if (backToOriginal) 0L else System.currentTimeMillis())
                    )
                )
            }
            draft.baseline = text
            statusMessage = if (backToOriginal) "Original text restored." else "Applied."
        }
    }

    fun resetToFile() {
        draft.text = TextFieldValue(current.text)
        statusMessage = null
    }

    val formatLabel = current.extension.uppercase()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "\"${current.title}\" ($formatLabel). Edits show live on the video; press Apply to keep them. Leaving without Apply discards them.",
            fontSize = 11.5.sp,
            color = LocalSheetSecondaryTextColor.current
        )

        val horizontal = rememberScrollState()
        val vertical = rememberScrollState()
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SheetButtonBgColor)
                .border(1.dp, SheetButtonBorderColor, RoundedCornerShape(12.dp))
        ) {
            val viewportWidth = maxWidth
            val viewportHeight = maxHeight
            // The text sits inside horizontal + vertical scroll containers, which hand it
            // unbounded space: lines are never wrapped or broken, the user scrolls sideways.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(vertical)
                    .horizontalScroll(horizontal)
            ) {
                BasicTextField(
                    value = draft.text,
                    onValueChange = { draft.text = it },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = SheetButtonTextColor
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .widthIn(min = viewportWidth)
                        .heightIn(min = viewportHeight)
                        .padding(10.dp)
                )
            }
        }

        statusMessage?.let {
            Text(text = it, fontSize = 11.5.sp, color = LocalSheetSecondaryTextColor.current)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RawEditorButton(
                label = "RESET TO FILE",
                filled = false,
                enabled = currentText != current.text,
                onClick = { resetToFile() }
            )
            RawEditorButton(
                label = "APPLY",
                filled = true,
                enabled = isDirty,
                onClick = { applyNow() }
            )
        }
    }
}

@Composable
private fun RawEditorButton(
    label: String,
    filled: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.45f)
            .clip(shape)
            .background(if (filled) MaterialTheme.colorScheme.primary else SheetButtonBgColor)
            .border(1.dp, if (filled) MaterialTheme.colorScheme.primary else SheetButtonBorderColor, shape)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (filled) androidx.compose.ui.graphics.Color.White else SheetButtonTextColor
        )
    }
}

@Composable
private fun RawInfoCard(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SheetButtonBgColor)
            .border(1.dp, SheetButtonBorderColor, RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Text(text = message, fontSize = 12.5.sp, color = SheetButtonTextColor)
    }
}
