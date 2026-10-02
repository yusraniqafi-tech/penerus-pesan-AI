package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ForwardingRule
import com.example.data.model.TelegramBot
import com.example.ui.viewmodel.ForwarderViewModel
import org.json.JSONObject

@Composable
fun RulesScreen(
    viewModel: ForwarderViewModel,
    modifier: Modifier = Modifier
) {
    val rules by viewModel.rules.collectAsState()
    val bots by viewModel.bots.collectAsState()

    var showAddForm by remember { mutableStateOf(false) }

    // Form states
    var ruleName by remember { mutableStateOf("") }
    var selectedBot by remember { mutableStateOf<TelegramBot?>(null) }
    var sourceChatId by remember { mutableStateOf("") }
    var targetType by remember { mutableStateOf("TELEGRAM") } // "TELEGRAM", "DISCORD", "WEBHOOK"
    
    // Target fields
    var targetChatId by remember { mutableStateOf("") }
    var targetBotToken by remember { mutableStateOf("") }
    var discordWebhookUrl by remember { mutableStateOf("") }
    var customWebhookUrl by remember { mutableStateOf("") }

    // Advanced filters
    var showAdvancedFilters by remember { mutableStateOf(false) }
    var keywordsInclude by remember { mutableStateOf("") }
    var keywordsExclude by remember { mutableStateOf("") }
    var messageTypeFilter by remember { mutableStateOf("ALL") } // "ALL", "TEXT_ONLY", "MEDIA_ONLY"

    // AI states
    var showAiSection by remember { mutableStateOf(false) }
    var enableAi by remember { mutableStateOf(false) }
    var aiMode by remember { mutableStateOf("SUMMARIZE") } // "SUMMARIZE", "TRANSLATE", "CUSTOM"
    var aiPrompt by remember { mutableStateOf("") }

    // Auto-select first bot if available and nothing selected
    LaunchedEffect(bots) {
        if (selectedBot == null && bots.isNotEmpty()) {
            selectedBot = bots.first()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Form Toggle FAB Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Aturan Penerusan (${rules.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Button(
                    onClick = { showAddForm = !showAddForm },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (showAddForm) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) 
                                       else MaterialTheme.colorScheme.primary,
                        contentColor = if (showAddForm) MaterialTheme.colorScheme.error 
                                     else MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("toggle_add_rule_button")
                ) {
                    Icon(
                        imageVector = if (showAddForm) Icons.Default.Close else Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (showAddForm) "Batal" else "Buat Aturan",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Section: Add Rule Form
        if (showAddForm) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "Aturan Baru",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // 1. Rule Name
                        OutlinedTextField(
                            value = ruleName,
                            onValueChange = { ruleName = it },
                            label = { Text("Nama Aturan") },
                            placeholder = { Text("E.g. Grup Utama -> Discord") },
                            modifier = Modifier.fillMaxWidth().testTag("rule_name_input"),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // 2. Source Bot Selection
                        if (bots.isEmpty()) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)),
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                    Text(
                                        text = "Tambahkan Bot Telegram di tab 'Kelola Bot' terlebih dahulu!",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = "Sumber Bot:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Bot selector rows
                            bots.forEach { bot ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (selectedBot?.id == bot.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                            else Color.Transparent
                                        )
                                        .border(
                                            1.dp,
                                            if (selectedBot?.id == bot.id) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedBot = bot }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    RadioButton(
                                        selected = selectedBot?.id == bot.id,
                                        onClick = { selectedBot = bot }
                                    )
                                    Column {
                                        Text(bot.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("@${bot.username}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 3. Source Chat ID Filter
                        OutlinedTextField(
                            value = sourceChatId,
                            onValueChange = { sourceChatId = it },
                            label = { Text("ID Chat Sumber (Opsional)") },
                            placeholder = { Text("E.g. -100123456789 atau kosongkan untuk semua", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth().testTag("source_chat_id_input"),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // 4. Target Destination Type Tabs
                        Text(
                            text = "Tujuan Penerusan:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("TELEGRAM", "DISCORD", "WEBHOOK").forEach { type ->
                                val active = targetType == type
                                Button(
                                    onClick = { targetType = type },
                                    modifier = Modifier.weight(1f).height(38.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (active) MaterialTheme.colorScheme.primary 
                                                       else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (active) Color.White 
                                                     else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Text(
                                        text = when(type) {
                                            "TELEGRAM" -> "Telegram"
                                            "DISCORD" -> "Discord"
                                            else -> "Webhook"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Destination specific inputs
                        when (targetType) {
                            "TELEGRAM" -> {
                                OutlinedTextField(
                                    value = targetChatId,
                                    onValueChange = { targetChatId = it },
                                    label = { Text("ID Chat Tujuan (Channel/Grup)") },
                                    placeholder = { Text("E.g. -100987654321 atau @channel_username", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth().testTag("target_chat_id_input"),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = targetBotToken,
                                    onValueChange = { targetBotToken = it },
                                    label = { Text("Token Bot Tujuan (Opsional)") },
                                    placeholder = { Text("Kosongkan untuk gunakan bot sumber", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true
                                )
                            }
                            "DISCORD" -> {
                                OutlinedTextField(
                                    value = discordWebhookUrl,
                                    onValueChange = { discordWebhookUrl = it },
                                    label = { Text("URL Webhook Discord") },
                                    placeholder = { Text("https://discord.com/api/webhooks/...", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth().testTag("discord_webhook_input"),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true
                                )
                            }
                            "WEBHOOK" -> {
                                OutlinedTextField(
                                    value = customWebhookUrl,
                                    onValueChange = { customWebhookUrl = it },
                                    label = { Text("URL Webhook Kustom (POST)") },
                                    placeholder = { Text("https://domainanda.com/api/forward", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth().testTag("custom_webhook_input"),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Expandable 5: Advanced Filters
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAdvancedFilters = !showAdvancedFilters }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.FilterAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                Text("Filter Lanjutan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Icon(
                                imageVector = if (showAdvancedFilters) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }

                        AnimatedVisibility(visible = showAdvancedFilters) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = keywordsInclude,
                                    onValueChange = { keywordsInclude = it },
                                    label = { Text("Harus Mengandung Kata Kunci") },
                                    placeholder = { Text("E.g. promo, diskon (pisahkan koma)", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = keywordsExclude,
                                    onValueChange = { keywordsExclude = it },
                                    label = { Text("Abaikan Jika Mengandung") },
                                    placeholder = { Text("E.g. spam, hoax (pisahkan koma)", fontSize = 11.sp) },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = "Saring Tipe Pesan:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    listOf("ALL", "TEXT_ONLY", "MEDIA_ONLY").forEach { filter ->
                                        val filterActive = messageTypeFilter == filter
                                        OutlinedButton(
                                            onClick = { messageTypeFilter = filter },
                                            shape = RoundedCornerShape(8.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                containerColor = if (filterActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) 
                                                               else Color.Transparent,
                                                contentColor = if (filterActive) MaterialTheme.colorScheme.primary 
                                                             else MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            border = BorderStroke(
                                                1.dp, 
                                                if (filterActive) MaterialTheme.colorScheme.primary 
                                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                                            ),
                                            modifier = Modifier.weight(1f).height(34.dp),
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text(
                                                text = when (filter) {
                                                    "ALL" -> "Semua"
                                                    "TEXT_ONLY" -> "Teks Saja"
                                                    else -> "Media Saja"
                                                },
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Expandable 6: AI Transformation
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showAiSection = !showAiSection }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(18.dp))
                                Text("AI Gemini Transformasi", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Icon(
                                imageVector = if (showAiSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }

                        AnimatedVisibility(visible = showAiSection) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Aktifkan Kecerdasan AI", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    Switch(
                                        checked = enableAi,
                                        onCheckedChange = { enableAi = it }
                                    )
                                }
                                
                                if (enableAi) {
                                    val apiKeyOk = viewModel.isGeminiConfigured()
                                    if (!apiKeyOk) {
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f)),
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = "Catatan: API Key Gemini terdeteksi sebagai placeholder. Pastikan Anda mengaturnya di tab panel Secrets di AI Studio agar AI berfungsi dengan nyata.",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.error,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(10.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Pilih Mode AI:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf("SUMMARIZE", "TRANSLATE", "CUSTOM").forEach { mode ->
                                            val modeActive = aiMode == mode
                                            OutlinedButton(
                                                onClick = { aiMode = mode },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.outlinedButtonColors(
                                                    containerColor = if (modeActive) Color(0xFF00E5FF).copy(alpha = 0.08f) 
                                                                   else Color.Transparent,
                                                    contentColor = if (modeActive) Color(0xFF00B0FF) 
                                                                 else MaterialTheme.colorScheme.onSurfaceVariant
                                                ),
                                                border = BorderStroke(1.dp, if (modeActive) Color(0xFF00E5FF) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
                                                modifier = Modifier.weight(1f).height(34.dp),
                                                contentPadding = PaddingValues(0.dp)
                                            ) {
                                                Text(
                                                    text = when (mode) {
                                                        "SUMMARIZE" -> "Ringkas"
                                                        "TRANSLATE" -> "Terjemah"
                                                        else -> "Promp Kustom"
                                                    },
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    when (aiMode) {
                                        "TRANSLATE" -> {
                                            OutlinedTextField(
                                                value = aiPrompt,
                                                onValueChange = { aiPrompt = it },
                                                label = { Text("Bahasa Target") },
                                                placeholder = { Text("E.g. English, Bahasa Indonesia, Japanese", fontSize = 11.sp) },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(10.dp),
                                                singleLine = true
                                            )
                                        }
                                        "CUSTOM" -> {
                                            OutlinedTextField(
                                                value = aiPrompt,
                                                onValueChange = { aiPrompt = it },
                                                label = { Text("Instruksi Promp Kustom") },
                                                placeholder = { Text("E.g. Hapus semua link, ubah harga dari USD ke IDR...", fontSize = 11.sp) },
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        // Submit Button
                        Button(
                            onClick = {
                                val targetConfig = JSONObject().apply {
                                    when (targetType) {
                                        "TELEGRAM" -> {
                                            put("targetChatId", targetChatId)
                                            put("targetBotToken", targetBotToken)
                                        }
                                        "DISCORD" -> {
                                            put("discordWebhookUrl", discordWebhookUrl)
                                        }
                                        "WEBHOOK" -> {
                                            put("webhookUrl", customWebhookUrl)
                                        }
                                    }
                                }.toString()

                                selectedBot?.let { bot ->
                                    viewModel.addRule(
                                        name = ruleName,
                                        sourceBot = bot,
                                        sourceChatId = sourceChatId,
                                        targetType = targetType,
                                        targetConfigJson = targetConfig,
                                        keywordsInclude = keywordsInclude,
                                        keywordsExclude = keywordsExclude,
                                        messageTypeFilter = messageTypeFilter,
                                        aiTransformationType = if (enableAi) aiMode else "NONE",
                                        aiCustomPrompt = aiPrompt
                                    )
                                    // Reset fields & collapse
                                    ruleName = ""
                                    sourceChatId = ""
                                    targetChatId = ""
                                    targetBotToken = ""
                                    discordWebhookUrl = ""
                                    customWebhookUrl = ""
                                    keywordsInclude = ""
                                    keywordsExclude = ""
                                    messageTypeFilter = "ALL"
                                    enableAi = false
                                    aiPrompt = ""
                                    showAddForm = false
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("save_rule_button"),
                            shape = RoundedCornerShape(12.dp),
                            enabled = selectedBot != null && (ruleName.isNotBlank() || bots.isNotEmpty())
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Simpan Aturan Baru", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Section: Rules List
        if (rules.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SettingsInputComponent,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Belum Ada Aturan Dibuat",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Klik 'Buat Aturan' di atas untuk merancang rute penerusan pesan pertama Anda.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                }
            }
        } else {
            items(rules, key = { it.id }) { rule ->
                RuleListItem(
                    rule = rule,
                    onToggle = { viewModel.toggleRuleStatus(rule) },
                    onDelete = { viewModel.deleteRule(rule) }
                )
            }
        }
    }
}

@Composable
fun RuleListItem(
    rule: ForwardingRule,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val targetLabel = when (rule.targetType) {
        "TELEGRAM" -> "Telegram"
        "DISCORD" -> "Discord Webhook"
        else -> "Custom Webhook"
    }

    val targetIcon = when (rule.targetType) {
        "TELEGRAM" -> Icons.Default.Send
        "DISCORD" -> Icons.Default.AlternateEmail
        else -> Icons.Default.Link
    }

    val targetColor = when (rule.targetType) {
        "TELEGRAM" -> MaterialTheme.colorScheme.primary
        "DISCORD" -> Color(0xFF5865F2)
        else -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("rule_list_item_${rule.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = rule.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (rule.isActive) MaterialTheme.colorScheme.onSurface 
                               else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Filter tipe: " + when(rule.messageTypeFilter) {
                            "ALL" -> "Semua"
                            "TEXT_ONLY" -> "Teks Saja"
                            else -> "Media Saja"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Switch(
                        checked = rule.isActive,
                        onCheckedChange = { onToggle() },
                        modifier = Modifier.testTag("rule_status_switch_${rule.id}"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    IconButton(
                        onClick = { onDelete() },
                        modifier = Modifier.testTag("delete_rule_button_${rule.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Hapus Aturan",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

            Spacer(modifier = Modifier.height(10.dp))

            // Routing indicator visualization
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Source
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.SmartToy, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                    Text("Bot Sumber", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    modifier = Modifier.size(14.dp)
                )

                // Target
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .background(targetColor.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(targetIcon, contentDescription = null, modifier = Modifier.size(12.dp), tint = targetColor)
                    Text(targetLabel, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = targetColor)
                }
            }

            // Keyword inclusion/exclusion feedback
            if (rule.keywordsInclude.isNotEmpty() || rule.keywordsExclude.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (rule.keywordsInclude.isNotEmpty()) {
                        Text(
                            text = "Harus ada kata: ${rule.keywordsInclude}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (rule.keywordsExclude.isNotEmpty()) {
                        Text(
                            text = "Abaikan kata: ${rule.keywordsExclude}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // AI Status indicator
            if (rule.aiTransformationType != "NONE") {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .background(Color(0xFF00E5FF).copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF00B0FF),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "AI Gemini: " + when (rule.aiTransformationType) {
                            "SUMMARIZE" -> "Ringkasan"
                            "TRANSLATE" -> "Terjemahan (${rule.aiCustomPrompt})"
                            else -> "Kustom Promp"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00B0FF)
                    )
                }
            }
        }
    }
}
