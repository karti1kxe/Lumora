package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AccentGradient
import com.example.ui.theme.AccentSkyBlue

/**
 * Inline Rename Field:
 * Displays a small white rounded box with the current filename pre-filled,
 * and a clear checkmark (✓) button on the right end to confirm changes immediately.
 */
@Composable
fun InlineRenameField(
    initialText: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    isDark: Boolean = false
) {
    var textFieldValue by remember(initialText) {
        mutableStateOf(
            TextFieldValue(
                text = initialText,
                selection = TextRange(0, initialText.length)
            )
        )
    }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {}
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(10.dp),
                ambientColor = Color.Black.copy(alpha = 0.25f),
                spotColor = AccentSkyBlue.copy(alpha = 0.35f)
            )
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .border(1.5.dp, AccentSkyBlue, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = { textFieldValue = it },
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .testTag("inline_rename_text_field"),
                textStyle = TextStyle(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A),
                    lineHeight = 18.sp
                ),
                singleLine = true,
                cursorBrush = SolidColor(Color(0xFF0284C7)),
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done,
                    keyboardType = KeyboardType.Text
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val trimmed = textFieldValue.text.trim()
                        if (trimmed.isNotBlank()) {
                            onConfirm(trimmed)
                        } else {
                            onCancel()
                        }
                    }
                )
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Clear Checkmark Action Button (✓)
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AccentGradient)
                    .clickable {
                        val trimmed = textFieldValue.text.trim()
                        if (trimmed.isNotBlank()) {
                            onConfirm(trimmed)
                        } else {
                            onCancel()
                        }
                    }
                    .testTag("inline_rename_confirm_button"),
                contentAlignment = Alignment.Center
            ) {
                StyledIcon(imageVector = Icons.Rounded.Check,
                    contentDescription = "Save Name",
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
