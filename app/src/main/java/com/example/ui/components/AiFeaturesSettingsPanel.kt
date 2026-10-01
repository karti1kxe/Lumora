package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ai.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

/**
 * Settings > Advanced > AI Features.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiFeaturesSettingsPanel(
    isDark: Boolean,
    cardBg: Color,
    borderColor: Color
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val primaryText = if (isDark) DarkTextPrimary else LightTextPrimary
    val secondaryText = if (isDark) DarkTextSecondary else LightTextSecondary

    var settings by remember { mutableStateOf(AiFeaturesSettingsStore.load(context)) }
    fun update(block: (AiFeaturesSettings) -> AiFeaturesSettings) {
        settings = block(settings)
        AiFeaturesSettingsStore.save(context, settings)
    }

    var apiKeyText by remember(settings.providerId) {
        mutableStateOf(if (settings.hasApiKey) AiFeaturesSettingsStore.readApiKey(context) else "")
    }
    var apiKeyVisible by remember { mutableStateOf(false) }
    var apiConfigExpanded by remember { mutableStateOf(false) }
    var verifyState by remember { mutableStateOf<ApiKeyVerificationState>(ApiKeyVerificationState.Idle) }

    var balanceInfo by remember { mutableStateOf<AiProviderService.AccountBalance?>(null) }
    var isBalanceLoading by remember { mutableStateOf(false) }
    var balanceErrorMessage by remember { mutableStateOf<String?>(null) }

    fun checkBalance(provider: AiProvider, key: String) {
        if (key.isBlank()) {
            balanceInfo = null
            balanceErrorMessage = null
            return
        }
        isBalanceLoading = true
        balanceErrorMessage = null
        scope.launch {
            val result = AiProviderService.fetchAccountBalance(provider, key)
            isBalanceLoading = false
            result.fold(
                onSuccess = { b ->
                    balanceInfo = b
                    balanceErrorMessage = null
                },
                onFailure = {
                    balanceInfo = null
                    balanceErrorMessage = "Balance check nahi ho paya"
                }
            )
        }
    }

    var isLanguageSearchOpen by remember { mutableStateOf(false) }
    var languageSearchQuery by remember { mutableStateOf("") }

    var customPromptText by remember { mutableStateOf(settings.customPrompt) }
    var showPromptFilePicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {

        // ================= SECTION: AI FEATURES =================
        SectionHeader("AI FEATURES", secondaryText)
        GlassCard(cardBg, borderColor) {
            // 1. Enable AI Features Toggle
            ToggleRow(
                icon = Icons.Outlined.Memory,
                title = "Enable AI Features",
                subtitle = if (settings.aiFeaturesEnabled) "AI features are active" else "Turn on to configure a provider",
                checked = settings.aiFeaturesEnabled,
                isDark = isDark,
                primaryText = primaryText,
                secondaryText = secondaryText,
                onCheckedChange = { checked -> update { it.copy(aiFeaturesEnabled = checked) } }
            )

            // Smooth expand/collapse animation matching subtitle & audio language settings
            AnimatedVisibility(
                visible = settings.aiFeaturesEnabled,
                enter = fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                        expandVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)),
                exit = fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                        shrinkVertically(animationSpec = tween(220, easing = FastOutSlowInEasing))
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsDivider(borderColor)

                    // 2. Provider Row (using Orientation-style PopupMenu instead of bottom sheet)
                    PlayerChoiceRow(
                        title = "Provider",
                        description = "AI provider used for verification and translation",
                        value = settings.providerId,
                        options = AiProvider.entries.map { it.id to it.displayName },
                        isDark = isDark,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        borderColor = borderColor,
                        onChange = { key ->
                            update { it.copy(providerId = key, selectedModelId = "", discoveredModelIds = emptyList()) }
                            verifyState = ApiKeyVerificationState.Idle
                            balanceInfo = null
                            balanceErrorMessage = null
                        }
                    )

                    SettingsDivider(borderColor)

                    // 4. API Configuration (expandable with smooth animation)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { apiConfigExpanded = !apiConfigExpanded }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("API Configuration", fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = primaryText)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                when (val s = verifyState) {
                                    is ApiKeyVerificationState.Verified -> "Verified"
                                    else -> if (settings.hasApiKey) "Tap to configure" else "Tap to configure"
                                },
                                fontSize = 12.sp,
                                color = if (verifyState is ApiKeyVerificationState.Verified) Color(0xFF10B981) else secondaryText
                            )
                        }
                        StyledIcon(
                            imageVector = if (apiConfigExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = secondaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    AnimatedVisibility(
                        visible = apiConfigExpanded,
                        enter = fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                                expandVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)),
                        exit = fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                                shrinkVertically(animationSpec = tween(220, easing = FastOutSlowInEasing))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isDark) Color(0x14FFFFFF) else Color(0x0A000000))
                                    .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                                    .padding(16.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

                                    if (settings.provider == AiProvider.DEFAULT) {
                                        OutlinedFieldLike(
                                            label = "Base URL (OpenAI-compatible)",
                                            value = settings.customBaseUrl,
                                            onValueChange = { update { s -> s.copy(customBaseUrl = it) } },
                                            isDark = isDark,
                                            borderColor = borderColor,
                                            primaryText = primaryText,
                                            secondaryText = secondaryText
                                        )
                                    }

                                    // API Key field
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (isDark) Color(0xFF0B1220) else Color.White)
                                            .border(1.dp, AccentSkyBlue.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                            .padding(horizontal = 14.dp, vertical = 12.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                if (apiKeyText.isEmpty()) {
                                                    Text("API Key", color = secondaryText, fontSize = 14.sp)
                                                }
                                                BasicTextField(
                                                    value = apiKeyText,
                                                    onValueChange = { apiKeyText = it },
                                                    singleLine = true,
                                                    visualTransformation = if (apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                                    textStyle = androidx.compose.ui.text.TextStyle(color = primaryText, fontSize = 14.sp)
                                                )
                                            }
                                            StyledIcon(
                                                imageVector = if (apiKeyVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                                contentDescription = "Toggle API key visibility",
                                                tint = secondaryText,
                                                modifier = Modifier
                                                    .size(20.dp)
                                                    .clickable { apiKeyVisible = !apiKeyVisible }
                                            )
                                        }
                                    }

                                    // Verify Key button
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (apiKeyText.isBlank()) AccentSkyBlue.copy(alpha = 0.4f) else AccentSkyBlue)
                                            .clickable(enabled = apiKeyText.isNotBlank() && verifyState !is ApiKeyVerificationState.Verifying) {
                                                AiFeaturesSettingsStore.saveApiKey(context, apiKeyText)
                                                update { it.copy(hasApiKey = apiKeyText.isNotBlank()) }
                                                verifyState = ApiKeyVerificationState.Verifying
                                                balanceInfo = null
                                                balanceErrorMessage = null
                                                scope.launch {
                                                    val result = AiProviderService.verifyKeyAndListModels(
                                                        provider = settings.provider,
                                                        apiKey = apiKeyText,
                                                        customBaseUrl = settings.customBaseUrl
                                                    )
                                                    result.fold(
                                                        onSuccess = { models ->
                                                            verifyState = ApiKeyVerificationState.Verified(models)
                                                            // Auto-pick a model that is likely to actually work (free/cheap tier
                                                            // first) so the user isn't stuck manually hunting for one that
                                                            // doesn't 402. Manual selection below can still override it.
                                                            val autoPicked = if (settings.autoSelectBestModel) {
                                                                BestModelSelector.pickBest(models)?.id
                                                            } else null
                                                            update {
                                                                it.copy(
                                                                    discoveredModelIds = models.map { m -> m.id },
                                                                    selectedModelId = autoPicked
                                                                        ?: it.selectedModelId.ifBlank { models.firstOrNull()?.id ?: "" }
                                                                )
                                                            }
                                                            checkBalance(settings.provider, apiKeyText)
                                                        },
                                                        onFailure = { err ->
                                                            verifyState = ApiKeyVerificationState.Failed(err.localizedMessage ?: "Verification failed")
                                                            balanceInfo = null
                                                            balanceErrorMessage = null
                                                            Toast.makeText(context, "Verify failed: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                                                        }
                                                    )
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (verifyState is ApiKeyVerificationState.Verifying) {
                                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                                        } else {
                                            Text("Verify Key", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        }
                                    }

                                    when (val s = verifyState) {
                                        is ApiKeyVerificationState.Verified -> {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                StyledIcon(Icons.Outlined.CheckCircle, null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text(
                                                    "Verified (${s.models.size} models discovered)",
                                                    color = Color(0xFF10B981), fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold
                                                )
                                            }

                                            Spacer(Modifier.height(8.dp))

                                            // Balance / Credit information block
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(if (isDark) Color(0x14FFFFFF) else Color(0x08000000))
                                                    .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                            ) {
                                                if (isBalanceLoading) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        CircularProgressIndicator(
                                                            color = AccentSkyBlue,
                                                            strokeWidth = 2.dp,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                        Text("Checking balance...", fontSize = 11.5.sp, color = secondaryText)
                                                    }
                                                } else if (balanceErrorMessage != null) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(balanceErrorMessage!!, fontSize = 11.5.sp, color = secondaryText)
                                                        if (settings.provider == AiProvider.DEEPSEEK || settings.provider == AiProvider.OPENROUTER) {
                                                            IconButton(
                                                                onClick = { checkBalance(settings.provider, apiKeyText) },
                                                                modifier = Modifier.size(22.dp)
                                                            ) {
                                                                StyledIcon(
                                                                    imageVector = Icons.Outlined.Refresh,
                                                                    contentDescription = "Refresh balance",
                                                                    tint = secondaryText,
                                                                    modifier = Modifier.size(15.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                } else {
                                                    when (val b = balanceInfo) {
                                                        is AiProviderService.AccountBalance.DeepSeek -> {
                                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(
                                                                        text = "Balance: ${b.totalBalance} ${b.currency}".trim(),
                                                                        fontSize = 12.sp,
                                                                        fontWeight = FontWeight.SemiBold,
                                                                        color = primaryText
                                                                    )
                                                                    IconButton(
                                                                        onClick = { checkBalance(settings.provider, apiKeyText) },
                                                                        modifier = Modifier.size(22.dp)
                                                                    ) {
                                                                        StyledIcon(
                                                                            imageVector = Icons.Outlined.Refresh,
                                                                            contentDescription = "Refresh balance",
                                                                            tint = secondaryText,
                                                                            modifier = Modifier.size(15.dp)
                                                                        )
                                                                    }
                                                                }
                                                                Spacer(Modifier.height(6.dp))
                                                                LinearProgressIndicator(
                                                                    progress = { b.progress },
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .height(5.dp)
                                                                        .clip(RoundedCornerShape(3.dp)),
                                                                    color = AccentSkyBlue,
                                                                    trackColor = if (isDark) Color(0x26FFFFFF) else Color(0x1A000000)
                                                                )
                                                            }
                                                        }
                                                        is AiProviderService.AccountBalance.OpenRouter -> {
                                                            Column(modifier = Modifier.fillMaxWidth()) {
                                                                Row(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                                    verticalAlignment = Alignment.CenterVertically
                                                                ) {
                                                                    Text(
                                                                        text = "Remaining: $${b.remaining}",
                                                                        fontSize = 12.sp,
                                                                        fontWeight = FontWeight.SemiBold,
                                                                        color = primaryText
                                                                    )
                                                                    IconButton(
                                                                        onClick = { checkBalance(settings.provider, apiKeyText) },
                                                                        modifier = Modifier.size(22.dp)
                                                                    ) {
                                                                        StyledIcon(
                                                                            imageVector = Icons.Outlined.Refresh,
                                                                            contentDescription = "Refresh balance",
                                                                            tint = secondaryText,
                                                                            modifier = Modifier.size(15.dp)
                                                                        )
                                                                    }
                                                                }
                                                                Spacer(Modifier.height(6.dp))
                                                                LinearProgressIndicator(
                                                                    progress = { b.progress },
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .height(5.dp)
                                                                        .clip(RoundedCornerShape(3.dp)),
                                                                    color = AccentSkyBlue,
                                                                    trackColor = if (isDark) Color(0x26FFFFFF) else Color(0x1A000000)
                                                                )
                                                            }
                                                        }
                                                        is AiProviderService.AccountBalance.Unsupported -> {
                                                            Text(
                                                                text = "Yeh provider balance API se nahi dikhata — apna account ${b.website} par check karein",
                                                                fontSize = 11.5.sp,
                                                                color = secondaryText,
                                                                lineHeight = 16.sp
                                                            )
                                                        }
                                                        null -> {
                                                            Row(
                                                                modifier = Modifier.fillMaxWidth(),
                                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                                verticalAlignment = Alignment.CenterVertically
                                                            ) {
                                                                Text("Check account balance", fontSize = 11.5.sp, color = secondaryText)
                                                                IconButton(
                                                                    onClick = { checkBalance(settings.provider, apiKeyText) },
                                                                    modifier = Modifier.size(22.dp)
                                                                ) {
                                                                    StyledIcon(
                                                                        imageVector = Icons.Outlined.Refresh,
                                                                        contentDescription = "Check balance",
                                                                        tint = secondaryText,
                                                                        modifier = Modifier.size(15.dp)
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        is ApiKeyVerificationState.Failed -> {
                                            Text(s.message, color = Color(0xFFEF4444), fontSize = 12.sp)
                                        }
                                        else -> {}
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            ToggleRow(
                                icon = Icons.Outlined.AutoAwesome,
                                title = "Auto-select Best Model",
                                subtitle = "Pick a working model automatically after Verify Key (prefers free/cheap models to avoid payment errors)",
                                checked = settings.autoSelectBestModel,
                                isDark = isDark,
                                primaryText = primaryText,
                                secondaryText = secondaryText,
                                onCheckedChange = { checked ->
                                    update {
                                        val autoPicked = if (checked) {
                                            BestModelSelector.pickBest(it.discoveredModelIds.map { id -> AiModelInfo(id) })?.id
                                        } else null
                                        it.copy(
                                            autoSelectBestModel = checked,
                                            selectedModelId = autoPicked ?: it.selectedModelId
                                        )
                                    }
                                }
                            )

                            Spacer(Modifier.height(8.dp))

                            // Select Model (using Orientation-style PopupMenu instead of bottom sheet)
                            val modelOptions = settings.discoveredModelIds.ifEmpty {
                                if (settings.selectedModelId.isNotBlank()) listOf(settings.selectedModelId) else emptyList()
                            }.map { it to it }

                            PlayerChoiceRow(
                                title = "Select Model",
                                description = if (modelOptions.isEmpty()) "Verify your key to discover models" else "Model discovered from verified ${settings.provider.displayName}",
                                value = settings.selectedModelId,
                                options = modelOptions,
                                isDark = isDark,
                                primaryText = primaryText,
                                secondaryText = secondaryText,
                                borderColor = borderColor,
                                emptyMessage = "No models discovered. Please verify API key first.",
                                onChange = { key ->
                                    // Manually picking a model is always an explicit override, even
                                    // when auto-select is on.
                                    update { it.copy(selectedModelId = key) }
                                }
                            )

                            Spacer(Modifier.height(4.dp))
                        }
                    }

                    SettingsDivider(borderColor)

                    // 5. AI Features sub-toggle
                    ToggleRow(
                        icon = Icons.Outlined.Tune,
                        title = "AI Features",
                        subtitle = "Enable individual AI features",
                        checked = settings.subFeaturesEnabled,
                        isDark = isDark,
                        primaryText = primaryText,
                        secondaryText = secondaryText,
                        onCheckedChange = { checked -> update { it.copy(subFeaturesEnabled = checked) } }
                    )

                    AnimatedVisibility(
                        visible = settings.subFeaturesEnabled,
                        enter = fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                                expandVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)),
                        exit = fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                                shrinkVertically(animationSpec = tween(220, easing = FastOutSlowInEasing))
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SettingsDivider(borderColor)

                            ToggleRow(
                                drawableRes = R.drawable.lumora_aitranslate,
                                title = "AI Subtitle Translate",
                                subtitle = if (settings.hasUsableModel) "Translate the currently selected subtitle track"
                                           else "Select and verify a provider model first",
                                checked = settings.subtitleTranslateEnabled,
                                enabled = settings.hasUsableModel,
                                isDark = isDark,
                                primaryText = primaryText,
                                secondaryText = secondaryText,
                                onCheckedChange = { checked -> update { it.copy(subtitleTranslateEnabled = checked) } }
                            )

                            SettingsDivider(borderColor)

                            ToggleRow(
                                icon = Icons.Outlined.DriveFileRenameOutline,
                                title = "Bulk Rename",
                                subtitle = "AI-assisted bulk rename (future feature)",
                                checked = settings.bulkRenameEnabled,
                                enabled = false,
                                isDark = isDark,
                                primaryText = primaryText,
                                secondaryText = secondaryText,
                                onCheckedChange = { checked -> update { it.copy(bulkRenameEnabled = checked) } }
                            )

                            AnimatedVisibility(
                                visible = settings.subtitleTranslateEnabled && settings.hasUsableModel,
                                enter = fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)) +
                                        expandVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)),
                                exit = fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing)) +
                                        shrinkVertically(animationSpec = tween(220, easing = FastOutSlowInEasing))
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Spacer(Modifier.height(8.dp))

                                    // 6. Source language
                                    PlayerChoiceRow(
                                        title = "Source Language",
                                        description = "Subtitle language or Auto Detect",
                                        value = settings.sourceLanguageCode,
                                        options = listOf("auto" to "Auto Detect") + TranslateLanguages.ALL.map { it.code to it.label },
                                        isDark = isDark,
                                        primaryText = primaryText,
                                        secondaryText = secondaryText,
                                        borderColor = borderColor,
                                        onChange = { key -> update { it.copy(sourceLanguageCode = key) } }
                                    )
                                    Spacer(Modifier.height(10.dp))

                                    // 7. Target language
                                    Text("Target Language", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = secondaryText)
                                    Spacer(Modifier.height(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (isDark) Color(0x14FFFFFF) else Color(0x08000000))
                                            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                                            .padding(14.dp)
                                    ) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            // Top Header Row with Title, Current Selection, and Search Icon Button
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(36.dp)
                                                            .clip(CircleShape)
                                                            .background(AccentSkyBlue.copy(alpha = 0.18f)),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Text("\uD83C\uDF10", fontSize = 16.sp) // Globe icon
                                                    }
                                                    Spacer(Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "Target Language",
                                                            fontSize = 14.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = primaryText
                                                        )
                                                        Spacer(Modifier.height(2.dp))
                                                        val selectedLang = TranslateLanguages.ALL.firstOrNull { it.code == settings.translateLanguageCode }
                                                        Text(
                                                            text = selectedLang?.label ?: "English",
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = AccentSkyBlue,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }
                                                }

                                                Spacer(Modifier.width(8.dp))

                                                // Search button at the top
                                                Surface(
                                                    shape = CircleShape,
                                                    color = if (isLanguageSearchOpen) AccentSkyBlue.copy(alpha = 0.22f)
                                                            else if (isDark) Color.White.copy(alpha = 0.08f)
                                                            else Color.Black.copy(alpha = 0.06f),
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isLanguageSearchOpen) AccentSkyBlue else borderColor.copy(alpha = 0.6f)
                                                    ),
                                                    onClick = {
                                                        isLanguageSearchOpen = !isLanguageSearchOpen
                                                        if (!isLanguageSearchOpen) languageSearchQuery = ""
                                                    }
                                                ) {
                                                    Box(
                                                        modifier = Modifier.size(34.dp),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        StyledIcon(
                                                            imageVector = if (isLanguageSearchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
                                                            contentDescription = if (isLanguageSearchOpen) "Close search" else "Search languages",
                                                            tint = if (isLanguageSearchOpen) AccentSkyBlue else secondaryText,
                                                            modifier = Modifier.size(17.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            // Animated Search Bar
                                            AnimatedVisibility(
                                                visible = isLanguageSearchOpen,
                                                enter = fadeIn(tween(200)) + expandVertically(tween(220)),
                                                exit = fadeOut(tween(180)) + shrinkVertically(tween(200))
                                            ) {
                                                Column(modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .background(if (isDark) Color(0xFF0F172A) else Color.White)
                                                            .border(1.dp, AccentSkyBlue.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                                    ) {
                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                            StyledIcon(
                                                                imageVector = Icons.Outlined.Search,
                                                                contentDescription = null,
                                                                tint = AccentSkyBlue,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                            Spacer(Modifier.width(8.dp))
                                                            BasicTextField(
                                                                value = languageSearchQuery,
                                                                onValueChange = { languageSearchQuery = it },
                                                                singleLine = true,
                                                                textStyle = androidx.compose.ui.text.TextStyle(
                                                                    color = primaryText,
                                                                    fontSize = 13.sp
                                                                ),
                                                                modifier = Modifier.weight(1f),
                                                                decorationBox = { innerTextField ->
                                                                    if (languageSearchQuery.isEmpty()) {
                                                                        Text(
                                                                            "Search all world languages...",
                                                                            color = secondaryText.copy(alpha = 0.6f),
                                                                            fontSize = 12.5.sp
                                                                        )
                                                                    }
                                                                    innerTextField()
                                                                }
                                                            )
                                                            if (languageSearchQuery.isNotEmpty()) {
                                                                StyledIcon(
                                                                    imageVector = Icons.Outlined.Close,
                                                                    contentDescription = "Clear",
                                                                    tint = secondaryText,
                                                                    modifier = Modifier
                                                                        .size(16.dp)
                                                                        .clickable { languageSearchQuery = "" }
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(Modifier.height(10.dp))

                                            // Language Chips: Search Results or Left/Right Slide Presets
                                            if (languageSearchQuery.isNotBlank()) {
                                                val query = languageSearchQuery.trim()
                                                val filteredLanguages = remember(query) {
                                                    TranslateLanguages.ALL.filter {
                                                        it.label.contains(query, ignoreCase = true) ||
                                                        it.code.contains(query, ignoreCase = true)
                                                    }
                                                }

                                                Text(
                                                    text = "${filteredLanguages.size} matching languages:",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = secondaryText
                                                )
                                                Spacer(Modifier.height(6.dp))

                                                if (filteredLanguages.isEmpty()) {
                                                    Text(
                                                        text = "No languages match \"$query\"",
                                                        fontSize = 12.sp,
                                                        color = secondaryText,
                                                        modifier = Modifier.padding(vertical = 8.dp)
                                                    )
                                                } else {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .horizontalScroll(rememberScrollState()),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        filteredLanguages.forEach { item ->
                                                            val isSelected = item.code == settings.translateLanguageCode
                                                            val chipBg = if (isSelected) {
                                                                if (isDark) Color(0xFF0284C7).copy(alpha = 0.40f) else Color(0xFFE0F2FE)
                                                            } else {
                                                                if (isDark) Color(0x1AFFFFFF) else Color(0xFFF1F5F9)
                                                            }
                                                            val chipBorder = if (isSelected) AccentSkyBlue else borderColor

                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(12.dp))
                                                                    .background(chipBg)
                                                                    .border(1.dp, chipBorder, RoundedCornerShape(12.dp))
                                                                    .clickable {
                                                                        update { it.copy(translateLanguageCode = item.code) }
                                                                    }
                                                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                                                            ) {
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    if (isSelected) {
                                                                        StyledIcon(
                                                                            imageVector = Icons.Outlined.Check,
                                                                            contentDescription = null,
                                                                            tint = AccentSkyBlue,
                                                                            modifier = Modifier.size(13.dp)
                                                                        )
                                                                        Spacer(Modifier.width(4.dp))
                                                                    }
                                                                    Text(
                                                                        text = item.label,
                                                                        fontSize = 11.5.sp,
                                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                                        color = if (isSelected) AccentSkyBlue else primaryText
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            } else {
                                                // Horizontal scrollable row for quick slide / presets (left-right slide)
                                                Text(
                                                    text = "Quick Presets:",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = secondaryText
                                                )
                                                Spacer(Modifier.height(6.dp))

                                                val presetCodes = listOf(
                                                    "hi", "en", "es", "fr", "de", "ja", "ko", "zh", "ru", "ar",
                                                    "pt", "it", "bn", "ur", "mr", "te", "ta", "gu", "pa", "tr", "id", "vi", "th"
                                                )
                                                val orderedLanguages = remember(settings.translateLanguageCode) {
                                                    val selectedEntry = TranslateLanguages.ALL.firstOrNull { it.code == settings.translateLanguageCode }
                                                    val presets = presetCodes.mapNotNull { code -> TranslateLanguages.ALL.firstOrNull { it.code == code } }
                                                    if (selectedEntry != null && !presets.contains(selectedEntry)) {
                                                        listOf(selectedEntry) + presets
                                                    } else if (selectedEntry != null) {
                                                        listOf(selectedEntry) + (presets - selectedEntry)
                                                    } else {
                                                        presets
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState()),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    orderedLanguages.forEach { item ->
                                                        val isSelected = item.code == settings.translateLanguageCode
                                                        val chipBg = if (isSelected) {
                                                            if (isDark) Color(0xFF0284C7).copy(alpha = 0.40f) else Color(0xFFE0F2FE)
                                                        } else {
                                                            if (isDark) Color(0x1AFFFFFF) else Color(0xFFF1F5F9)
                                                        }
                                                        val chipBorder = if (isSelected) AccentSkyBlue else borderColor

                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(chipBg)
                                                                .border(1.dp, chipBorder, RoundedCornerShape(12.dp))
                                                                .clickable {
                                                                    update { it.copy(translateLanguageCode = item.code) }
                                                                }
                                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                                        ) {
                                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                                if (isSelected) {
                                                                    StyledIcon(
                                                                        imageVector = Icons.Outlined.Check,
                                                                        contentDescription = null,
                                                                        tint = AccentSkyBlue,
                                                                        modifier = Modifier.size(13.dp)
                                                                    )
                                                                    Spacer(Modifier.width(4.dp))
                                                                }
                                                                Text(
                                                                    text = item.label,
                                                                    fontSize = 11.5.sp,
                                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                                    color = if (isSelected) AccentSkyBlue else primaryText
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    Spacer(Modifier.height(12.dp))

                                    Text("Custom Prompt & Translation Rules", fontSize = 13.5.sp, fontWeight = FontWeight.Bold, color = primaryText)
                                    Text("Leave blank for automatic default translation", fontSize = 11.5.sp, color = secondaryText)
                                    Spacer(Modifier.height(8.dp))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(120.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (isDark) Color(0x14FFFFFF) else Color(0x08000000))
                                            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
                                            .verticalScroll(rememberScrollState())
                                            .padding(14.dp)
                                    ) {
                                        if (customPromptText.isEmpty()) {
                                            Text("Custom Prompt (Optional)", color = secondaryText, fontSize = 13.sp)
                                        }
                                        BasicTextField(
                                            value = customPromptText,
                                            onValueChange = { customPromptText = it },
                                            textStyle = androidx.compose.ui.text.TextStyle(color = primaryText, fontSize = 13.sp, lineHeight = 18.sp),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }

                                    Spacer(Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedActionButton(
                                            label = "Upload .txt",
                                            icon = Icons.Outlined.UploadFile,
                                            isDark = isDark,
                                            borderColor = borderColor,
                                            primaryText = primaryText,
                                            modifier = Modifier.weight(1f),
                                            onClick = { showPromptFilePicker = true }
                                        )
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(46.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(AccentSkyBlue)
                                                .clickable {
                                                    update { it.copy(customPrompt = customPromptText) }
                                                    Toast.makeText(context, "Prompt saved", Toast.LENGTH_SHORT).show()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                StyledIcon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Save Prompt", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ---------------- Pickers & Modals ----------------

    if (showPromptFilePicker) {
        FloatingDirectoryPickerSheet(
            action = DirectoryPickerAction.IMPORT_SETTINGS,
            itemCount = 1,
            itemDescription = "Prompt text file",
            isDark = isDark,
            customTitle = "Select Prompt .txt File",
            customActionLabel = "Select",
            allowedFileExtensions = setOf("txt"),
            onFileSelected = { file ->
                showPromptFilePicker = false
                try {
                    customPromptText = file.readText()
                    update { it.copy(customPrompt = customPromptText) }
                    Toast.makeText(context, "Prompt loaded from ${file.name}", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Could not read file: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            },
            onDismiss = { showPromptFilePicker = false }
        )
    }
}

// ---------------- Shared small building blocks ----------------

@Composable
private fun SectionHeader(text: String, color: Color) {
    Text(text, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = color, letterSpacing = 0.5.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun GlassCard(cardBg: Color, borderColor: Color, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun SettingsDivider(borderColor: Color) {
    HorizontalDivider(
        color = borderColor.copy(alpha = 0.5f),
        modifier = Modifier.padding(vertical = 14.dp)
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    isDark: Boolean,
    primaryText: Color,
    secondaryText: Color,
    onCheckedChange: (Boolean) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    drawableRes: Int? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (enabled) primaryText else secondaryText
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = secondaryText,
                lineHeight = 16.sp
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AccentSkyBlue,
                checkedThumbColor = Color.White
            )
        )
    }
}

/**
 * Dropdown Popup Choice Row — exactly matches the Orientation popup in player settings
 * (Screenshot 3).
 */
@Composable
private fun PlayerChoiceRow(
    title: String,
    description: String,
    value: String,
    options: List<Pair<String, String>>,
    isDark: Boolean,
    primaryText: Color = DarkTextPrimary,
    secondaryText: Color = DarkTextSecondary,
    borderColor: Color = DarkGlassBorder,
    emptyMessage: String = "No options available",
    onChange: (String) -> Unit
) {
    var expanded by remember(value) { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == value }?.second ?: value.ifBlank { "Choose" }

    val dropdownBg = if (isDark) {
        Color(0xFF0F172A).copy(alpha = 0.96f)
    } else {
        Color(0xFFFFFFFF).copy(alpha = 0.98f)
    }

    val dropdownBorder = if (isDark) {
        Color(0xFF38BDF8).copy(alpha = 0.55f)
    } else {
        Color(0xFF0284C7).copy(alpha = 0.45f)
    }

    val unselectedTextColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val accentColor = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
    val selectedItemBg = if (isDark) Color(0x3338BDF8) else Color(0xFFE0F2FE)

    val menuShape = RoundedCornerShape(16.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { expanded = true }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryText
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = secondaryText,
                lineHeight = 16.sp
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Box {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (expanded) {
                    if (isDark) Color(0x3338BDF8) else Color(0x220284C7)
                } else if (isDark) {
                    Color(0xFF1E293B).copy(alpha = 0.55f)
                } else {
                    Color(0xFF0284C7).copy(alpha = 0.10f)
                },
                border = if (expanded) {
                    BorderStroke(1.2.dp, accentColor)
                } else {
                    BorderStroke(1.dp, borderColor.copy(alpha = 0.60f))
                },
                onClick = { expanded = true }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = selectedLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 130.dp)
                    )
                    StyledIcon(
                        imageVector = Icons.Outlined.ArrowDropDown,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                shape = menuShape,
                containerColor = dropdownBg,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                border = BorderStroke(1.2.dp, dropdownBorder),
                modifier = Modifier
                    .widthIn(min = 180.dp, max = 240.dp)
                    .clip(menuShape)
            ) {
                if (options.isEmpty()) {
                    Text(
                        text = emptyMessage,
                        fontSize = 13.sp,
                        color = secondaryText,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                } else {
                    options.forEach { (key, label) ->
                        val isSelected = key == value
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) selectedItemBg else Color.Transparent)
                                .clickable {
                                    expanded = false
                                    onChange(key)
                                }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) accentColor else unselectedTextColor,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            if (isSelected) {
                                Spacer(modifier = Modifier.width(8.dp))
                                StyledIcon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PillButton(
    label: String,
    isDark: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = AccentSkyBlue.copy(alpha = if (enabled) 0.15f else 0.06f),
        border = BorderStroke(1.dp, AccentSkyBlue.copy(alpha = if (enabled) 0.45f else 0.15f)),
        onClick = onClick,
        enabled = enabled
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                StyledIcon(icon, null, tint = AccentSkyBlue, modifier = Modifier.size(15.dp))
            }
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentSkyBlue, maxLines = 1, overflow = TextOverflow.Ellipsis)
            StyledIcon(Icons.Outlined.ExpandMore, null, tint = AccentSkyBlue, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun OutlinedFieldLike(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isDark: Boolean,
    borderColor: Color,
    primaryText: Color,
    secondaryText: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDark) Color(0xFF0B1220) else Color.White)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        if (value.isEmpty()) Text(label, color = secondaryText, fontSize = 13.sp)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = primaryText, fontSize = 13.sp)
        )
    }
}

@Composable
private fun OutlinedActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isDark: Boolean,
    borderColor: Color,
    primaryText: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StyledIcon(icon, null, tint = primaryText, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = primaryText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}
