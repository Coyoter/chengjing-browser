package tw.techtarian.browser

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.AddToHomeScreen
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable internal fun WebAppIconBadge(
    bitmap: Bitmap?,
    name: String,
    url: String,
    themeColor: Int?,
    size: Dp = 48.dp,
    corner: Dp = 14.dp,
) {
    val colors = MaterialTheme.colorScheme
    val usable = bitmap?.takeUnless { it.isRecycled }
    val accentBg = remember(themeColor, colors.primary) {
        if (themeColor == null) colors.primary
        else Color(themeColor).takeIf { it.luminance() < 0.72f } ?: colors.primary
    }
    Surface(
        modifier = Modifier.size(size).testTag("webapp-icon-badge"),
        shape = RoundedCornerShape(corner),
        color = if (usable != null) Color(0xFFFBFAF6) else accentBg,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f)),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (usable != null) {
                Image(
                    bitmap = usable.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(size * 0.14f),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(
                    text = WebAppPolicy.badgeInitial(name, url),
                    fontSize = (size.value * 0.42f).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFFFFDF7),
                )
            }
        }
    }
}

internal fun webAppModeLabel(mode: WebAppDisplayMode): String = when (mode) {
    WebAppDisplayMode.STANDALONE -> bt(R.string.webapp_mode_standalone)
    WebAppDisplayMode.MINIMAL_UI -> bt(R.string.webapp_mode_minimal)
    WebAppDisplayMode.SHORTCUT -> bt(R.string.webapp_mode_shortcut)
}

internal fun webAppModeDescription(mode: WebAppDisplayMode): String = when (mode) {
    WebAppDisplayMode.STANDALONE -> bt(R.string.webapp_mode_standalone_desc)
    WebAppDisplayMode.MINIMAL_UI -> bt(R.string.webapp_mode_minimal_desc)
    WebAppDisplayMode.SHORTCUT -> bt(R.string.webapp_mode_shortcut_desc)
}

@Composable private fun WebAppModeSelector(
    selectedMode: WebAppDisplayMode,
    onSelect: (WebAppDisplayMode) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = bt(R.string.webapp_mode_heading),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
            color = colors.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple(WebAppDisplayMode.STANDALONE, bt(R.string.webapp_mode_standalone), Icons.Outlined.AppShortcut),
                Triple(WebAppDisplayMode.MINIMAL_UI, bt(R.string.webapp_mode_minimal), Icons.Outlined.WebAsset),
                Triple(WebAppDisplayMode.SHORTCUT, bt(R.string.webapp_mode_shortcut), Icons.Outlined.OpenInBrowser),
            ).forEach { (mode, label, icon) ->
                val active = selectedMode == mode
                WebAppModeCard(
                    label = label,
                    icon = icon,
                    selected = active,
                    modifier = Modifier.weight(1f).testTag("webapp-mode-${mode.id}"),
                    onClick = { onSelect(mode) },
                )
            }
        }
        Text(
            text = webAppModeDescription(selectedMode),
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable private fun WebAppModeCard(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .height(92.dp)
            .semantics { this.selected = selected; role = Role.RadioButton },
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) colors.primaryContainer else colors.surface,
        border = if (selected) BorderStroke(1.dp, colors.primary.copy(alpha = 0.4f))
        else BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.4f)),
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(23.dp),
                tint = if (selected) colors.primary else colors.onSurfaceVariant,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                if (selected) {
                    Icon(Icons.Outlined.Check, null, Modifier.size(12.dp), tint = colors.primary)
                }
                Text(
                    text = label,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable internal fun InstallWebAppDialog(c: BrowserController) {
    val tab = c.active
    val cleanUrl = remember(tab?.url) { WebAppPolicy.installableUrl(tab?.url) }
    if (tab == null || cleanUrl == null) {
        LaunchedEffect(Unit) {
            c.notice = bt(R.string.webapp_notice_invalid_page)
            c.sheet = ""
        }
        return
    }
    val existing = remember(cleanUrl, c.webApps.revision) { c.webApps.findByUrl(cleanUrl) }
    val initialName = remember(cleanUrl, existing?.name, tab.title) {
        existing?.name ?: WebAppPolicy.sanitizeName(tab.title, cleanUrl)
    }
    var name by remember(cleanUrl) { mutableStateOf(initialName) }
    var userEditedName by remember(cleanUrl) { mutableStateOf(false) }
    var mode by remember(cleanUrl) { mutableStateOf(existing?.mode ?: WebAppDisplayMode.STANDALONE) }
    var userEditedMode by remember(cleanUrl) { mutableStateOf(false) }
    var themeColor by remember(cleanUrl) { mutableStateOf(existing?.themeColor) }
    var iconBitmap by remember(cleanUrl) {
        mutableStateOf(existing?.let { c.webApps.loadIcon(it) } ?: c.icons.peek(cleanUrl))
    }
    var loadingMeta by remember(cleanUrl) { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(tab.id, cleanUrl) {
        val resolved = runCatching { WebAppMetadataFetcher.fetch(c, tab) }.getOrNull()
        if (resolved != null) {
            if (!userEditedName && existing == null) {
                name = resolved.metadata.name
            }
            if (!userEditedMode && existing == null) {
                mode = resolved.metadata.suggestedMode
            }
            if (resolved.metadata.themeColor != null) {
                themeColor = resolved.metadata.themeColor
            }
            if (resolved.icon != null) {
                iconBitmap = resolved.icon
            }
        }
        loadingMeta = false
    }

    Dialog(
        onDismissRequest = { if (!saving) c.sheet = "" },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(20.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .heightIn(max = (LocalConfiguration.current.screenHeightDp * 0.88f).dp)
                .testTag("install-webapp-dialog"),
            shape = RoundedCornerShape(28.dp),
            color = colors.surfaceContainerHigh,
            contentColor = colors.onSurface,
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 18.dp, end = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (existing != null) bt(R.string.webapp_dialog_update_title) else bt(R.string.webapp_dialog_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { if (!saving) c.sheet = "" }) {
                        Icon(Icons.Outlined.Close, bt(R.string.msg_de961bb8936f))
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = colors.surface,
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                WebAppIconBadge(
                                    bitmap = iconBitmap,
                                    name = name.ifBlank { initialName },
                                    url = cleanUrl,
                                    themeColor = themeColor,
                                    size = 56.dp,
                                    corner = 16.dp,
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = WebAppPolicy.sanitizeName(name, cleanUrl),
                                        fontSize = 16.sp,
                                        lineHeight = 22.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = WebAppPolicy.displayUrl(cleanUrl),
                                        fontSize = 12.sp,
                                        lineHeight = 16.sp,
                                        color = colors.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = TextStyle(textDirection = TextDirection.Ltr),
                                    )
                                }
                            }
                            if (loadingMeta) {
                                LinearProgressIndicator(
                                    modifier = Modifier.fillMaxWidth().height(2.dp),
                                    trackColor = Color.Transparent,
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it.take(WebAppPolicy.MAX_NAME_LENGTH)
                            userEditedName = true
                        },
                        modifier = Modifier.fillMaxWidth().testTag("webapp-name-input"),
                        label = { Text(bt(R.string.webapp_name_label)) },
                        placeholder = { Text(bt(R.string.webapp_name_placeholder)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = if (name.isNotEmpty()) {
                            {
                                IconButton(onClick = { name = ""; userEditedName = true }) {
                                    Icon(Icons.Outlined.Close, bt(R.string.webapp_clear_name), Modifier.size(18.dp))
                                }
                            }
                        } else null,
                    )

                    WebAppModeSelector(selectedMode = mode) {
                        mode = it
                        userEditedMode = true
                    }

                    if (tab.incognito) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surfaceVariant,
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.PrivacyTip,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp).padding(top = 1.dp),
                                    tint = colors.primary,
                                )
                                Text(
                                    text = bt(R.string.webapp_incognito_note),
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { if (!saving) c.sheet = "" }, enabled = !saving) {
                        Text(bt(R.string.msg_2cd0f3be8738))
                    }
                    Button(
                        onClick = {
                            if (saving) return@Button
                            saving = true
                            scope.launch {
                                val finalName = WebAppPolicy.sanitizeName(name, cleanUrl)
                                val now = System.currentTimeMillis()
                                val appId = existing?.id ?: WebAppPolicy.idForUrl(cleanUrl)
                                val savedIconFile = withContext(Dispatchers.IO) {
                                    iconBitmap?.let { c.webApps.saveIcon(appId, it) } ?: existing?.iconFile
                                }
                                val app = InstalledWebApp(
                                    id = appId,
                                    name = finalName,
                                    url = cleanUrl,
                                    scope = WebAppPolicy.scopeFor(cleanUrl),
                                    mode = mode,
                                    themeColor = themeColor,
                                    iconFile = savedIconFile,
                                    createdAt = existing?.createdAt ?: now,
                                    updatedAt = now,
                                )
                                c.webApps.save(app)
                                c.revision++
                                val outcome = WebAppLauncher.pinToHomeScreen(c.context, app, iconBitmap)
                                c.notice = when (outcome) {
                                    PinOutcome.REQUESTED -> bt(R.string.webapp_notice_pinned, finalName)
                                    PinOutcome.UPDATED -> bt(R.string.webapp_notice_updated, finalName)
                                    PinOutcome.UNSUPPORTED -> bt(R.string.webapp_notice_saved_no_pin)
                                    PinOutcome.FAILED -> bt(R.string.webapp_notice_pin_failed)
                                }
                                saving = false
                                c.sheet = ""
                            }
                        },
                        enabled = !saving,
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("confirm-install-webapp"),
                    ) {
                        Text(if (existing != null) bt(R.string.webapp_action_update) else bt(R.string.webapp_action_install))
                    }
                }
            }
        }
    }
}

@Composable internal fun ColumnScope.WebAppsScreen(c: BrowserController) {
    val colors = MaterialTheme.colorScheme
    val apps = remember(c.webApps.revision, c.revision) { c.webApps.list() }
    var editing by remember { mutableStateOf<InstalledWebApp?>(null) }
    var removing by remember { mutableStateOf<InstalledWebApp?>(null) }

    if (apps.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(64.dp).background(colors.primary.copy(alpha = 0.09f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Apps, null, Modifier.size(30.dp), tint = colors.primary)
            }
            Spacer(Modifier.height(18.dp))
            Text(bt(R.string.webapp_empty_title), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Text(
                text = bt(R.string.webapp_empty_desc),
                fontSize = 13.sp,
                lineHeight = 20.sp,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    } else {
        Text(
            text = bt(R.string.webapp_panel_subtitle),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = colors.onSurfaceVariant,
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("webapp-list"),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(apps, key = { it.id }) { app ->
                WebAppListCard(
                    c = c,
                    app = app,
                    onOpen = {
                        if (WebAppLauncher.open(c.context, app, c)) {
                            c.sheet = ""
                        }
                    },
                    onPin = {
                        val bmp = c.webApps.loadIcon(app) ?: c.icons.peek(app.url)
                        val outcome = WebAppLauncher.pinToHomeScreen(c.context, app, bmp, forcePin = true)
                        c.notice = when (outcome) {
                            PinOutcome.REQUESTED -> bt(R.string.webapp_notice_pinned, app.name)
                            PinOutcome.UPDATED -> bt(R.string.webapp_notice_updated, app.name)
                            PinOutcome.UNSUPPORTED -> bt(R.string.webapp_notice_saved_no_pin)
                            PinOutcome.FAILED -> bt(R.string.webapp_notice_pin_failed)
                        }
                    },
                    onEdit = { editing = app },
                    onOpenInBrowser = {
                        c.newTab(app.url, incognito = false)
                        c.sheet = ""
                    },
                    onRemove = { removing = app },
                )
            }
        }
    }

    editing?.let { target ->
        EditWebAppDialog(
            c = c,
            app = target,
            onDismiss = { editing = null },
        )
    }

    removing?.let { target ->
        AlertDialog(
            onDismissRequest = { removing = null },
            containerColor = colors.surfaceContainerHigh,
            tonalElevation = 0.dp,
            title = { Text(bt(R.string.webapp_remove_title)) },
            text = { Text(bt(R.string.webapp_remove_message, target.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        c.webApps.remove(target.id)
                        WebAppLauncher.disablePinnedShortcut(c.context, target.id, bt(R.string.webapp_disabled_shortcut))
                        c.revision++
                        c.notice = bt(R.string.webapp_notice_removed, target.name)
                        removing = null
                    },
                    modifier = Modifier.testTag("confirm-remove-webapp"),
                ) {
                    Text(bt(R.string.webapp_action_remove), color = colors.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { removing = null }) {
                    Text(bt(R.string.msg_2cd0f3be8738))
                }
            },
        )
    }
}

@Composable private fun WebAppListCard(
    c: BrowserController,
    app: InstalledWebApp,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onOpenInBrowser: () -> Unit,
    onRemove: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    val icon by produceState<Bitmap?>(null, app.id, app.iconFile, c.webApps.revision) {
        value = withContext(Dispatchers.IO) { c.webApps.loadIcon(app) } ?: c.icons.peek(app.url)
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("webapp-card:${app.id}")
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        color = colors.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WebAppIconBadge(
                bitmap = icon,
                name = app.name,
                url = app.url,
                themeColor = app.themeColor,
                size = 48.dp,
                corner = 14.dp,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = app.name,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = WebAppPolicy.displayUrl(app.url),
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(textDirection = TextDirection.Ltr),
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (app.mode == WebAppDisplayMode.SHORTCUT) colors.surfaceVariant else colors.primaryContainer,
                ) {
                    Text(
                        text = webAppModeLabel(app.mode),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = if (app.mode == WebAppDisplayMode.SHORTCUT) colors.onSurfaceVariant else colors.onPrimaryContainer,
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier.testTag("webapp-more:${app.id}"),
                ) {
                    Icon(Icons.Outlined.MoreVert, bt(R.string.webapp_action_more))
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false },
                    containerColor = colors.surfaceContainerHighest,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f)),
                ) {
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.AutoMirrored.Outlined.AddToHomeScreen, null, Modifier.size(20.dp)) },
                        text = { Text(bt(R.string.webapp_action_pin)) },
                        onClick = { menuOpen = false; onPin() },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Outlined.Edit, null, Modifier.size(20.dp)) },
                        text = { Text(bt(R.string.webapp_action_edit)) },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, null, Modifier.size(20.dp)) },
                        text = { Text(bt(R.string.webapp_action_open_browser)) },
                        onClick = { menuOpen = false; onOpenInBrowser() },
                    )
                    HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.45f))
                    DropdownMenuItem(
                        leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(20.dp), tint = colors.error) },
                        text = { Text(bt(R.string.webapp_action_remove), color = colors.error) },
                        onClick = { menuOpen = false; onRemove() },
                    )
                }
            }
        }
    }
}

@Composable private fun EditWebAppDialog(
    c: BrowserController,
    app: InstalledWebApp,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var name by remember(app.id) { mutableStateOf(app.name) }
    var mode by remember(app.id) { mutableStateOf(app.mode) }
    val icon by produceState<Bitmap?>(null, app.id, app.iconFile) {
        value = withContext(Dispatchers.IO) { c.webApps.loadIcon(app) } ?: c.icons.peek(app.url)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .padding(20.dp)
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .testTag("edit-webapp-dialog"),
            shape = RoundedCornerShape(28.dp),
            color = colors.surfaceContainerHigh,
            contentColor = colors.onSurface,
            tonalElevation = 0.dp,
        ) {
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 24.dp, top = 18.dp, end = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(bt(R.string.webapp_edit_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, bt(R.string.msg_de961bb8936f)) }
                }
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(WebAppPolicy.MAX_NAME_LENGTH) },
                        modifier = Modifier.fillMaxWidth().testTag("edit-webapp-name"),
                        label = { Text(bt(R.string.webapp_name_label)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )
                    WebAppModeSelector(selectedMode = mode) { mode = it }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                ) {
                    TextButton(onClick = onDismiss) { Text(bt(R.string.msg_2cd0f3be8738)) }
                    Button(
                        onClick = {
                            val finalName = WebAppPolicy.sanitizeName(name, app.url)
                            val updated = app.copy(
                                name = finalName,
                                mode = mode,
                                updatedAt = System.currentTimeMillis(),
                            )
                            c.webApps.save(updated)
                            WebAppLauncher.updatePinnedShortcut(c.context, updated, icon)
                            c.revision++
                            c.notice = bt(R.string.webapp_notice_updated, finalName)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(22.dp),
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
                        modifier = Modifier.testTag("confirm-edit-webapp"),
                    ) {
                        Text(bt(R.string.webapp_action_update))
                    }
                }
            }
        }
    }
}
