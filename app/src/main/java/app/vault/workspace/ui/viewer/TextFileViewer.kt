package app.vault.workspace.ui.viewer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vault.workspace.ui.theme.VaultAccent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Premium text / JSON / XML reader (Phase 2).
 *
 * Font size, theme (dark / sepia / light), wrap, mono/sans, line numbers, in-file search,
 * keep-screen-on, encoding label, soft truncate + Load more. Decrypt via [loadBytes] only;
 * plaintext wiped from byte buffers on dispose — no durable plaintext on disk.
 */
@Composable
fun TextFileViewer(
    loadBytes: suspend () -> ByteArray,
    itemId: String? = null,
    modifier: Modifier = Modifier,
    onSingleTap: () -> Unit = {},
    controlsVisible: Boolean = true,
    onControlsInteraction: () -> Unit = {},
    onSave: (suspend (String) -> Result<Unit>)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember(context) { TextReaderPrefs(context) }
    val scope = rememberCoroutineScope()
    val latestOnSingleTap by rememberUpdatedState(onSingleTap)
    val latestOnControlsInteraction by rememberUpdatedState(onControlsInteraction)

    var fullText by remember { mutableStateOf<String?>(null) }
    var encodingLabel by remember { mutableStateOf("UTF-8") }
    var bytesTruncated by remember { mutableStateOf(false) }
    var originalByteLength by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var loadMoreCount by remember { mutableIntStateOf(0) }

    var fontSp by remember { mutableFloatStateOf(prefs.getFontSp()) }
    var theme by remember { mutableStateOf(prefs.getTheme()) }
    var wrap by remember { mutableStateOf(prefs.getWrap()) }
    var monospace by remember { mutableStateOf(prefs.getMonospace()) }
    var lineNumbers by remember { mutableStateOf(prefs.getLineNumbers()) }

    var searchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var matchIndex by remember { mutableIntStateOf(0) }
    var matchLineIndices by remember { mutableStateOf<List<Int>>(emptyList()) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var saveBusy by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val hScroll = rememberScrollState()
    val palette = textReaderPalette(theme)

    // Keep screen on while reader is open.
    DisposableEffect(Unit) {
        val window = view.context.findTextReaderActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(itemId) {
        error = null
        fullText = null
        loadMoreCount = 0
        try {
            val decoded = withContext(Dispatchers.IO) {
                val bytes = loadBytes()
                try {
                    TextEncoding.decode(bytes)
                } finally {
                    bytes.fill(0)
                }
            }
            fullText = decoded.text
            editing = false
            draft = decoded.text
            saveError = null
            encodingLabel = decoded.encodingLabel
            bytesTruncated = decoded.bytesTruncated ||
                decoded.text.length >= TextEncoding.HARD_MAX_CHARS
            originalByteLength = decoded.originalByteLength
        } catch (e: Exception) {
            error = e.message ?: "Cannot read text"
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            fullText = null
            matchLineIndices = emptyList()
            searchQuery = ""
        }
    }

    val text = fullText
    val visibleLimit = if (text == null) {
        0
    } else {
        TextEncoding.visibleLimit(text.length, loadMoreCount)
    }
    val visibleText = text?.take(visibleLimit).orEmpty()
    val lines = remember(visibleText) { visibleText.split('\n') }
    val canLoadMore = text != null && TextEncoding.canLoadMore(text.length, visibleLimit)

    // Recompute search hits when query / visible window changes.
    LaunchedEffect(searchQuery, visibleText) {
        if (searchQuery.isEmpty()) {
            matchLineIndices = emptyList()
            matchIndex = 0
            return@LaunchedEffect
        }
        val q = searchQuery
        val hits = ArrayList<Int>()
        for (i in lines.indices) {
            if (lines[i].contains(q, ignoreCase = true)) hits.add(i)
        }
        matchLineIndices = hits
        matchIndex = 0
        if (hits.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(hits[0]) }
        }
    }

    fun bumpChrome() = latestOnControlsInteraction()

    fun persistFont(sp: Float) {
        fontSp = sp
        prefs.setFontSp(sp)
        bumpChrome()
    }

    fun cycleTheme() {
        theme = when (theme) {
            TextReaderTheme.DARK -> TextReaderTheme.SEPIA
            TextReaderTheme.SEPIA -> TextReaderTheme.LIGHT
            TextReaderTheme.LIGHT -> TextReaderTheme.DARK
        }
        prefs.setTheme(theme)
        bumpChrome()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        when {
            error != null -> Text(
                error!!,
                color = palette.text,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
            text == null -> CircularProgressIndicator(
                color = VaultAccent,
                modifier = Modifier.align(Alignment.Center),
            )
            else -> {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val maxReadable = 720.dp
                    if (editing) {
                        BasicTextField(
                            value = draft,
                            onValueChange = { draft = it; saveError = null },
                            textStyle = TextStyle(
                                color = palette.text,
                                fontSize = fontSp.sp,
                                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.SansSerif,
                            ),
                            cursorBrush = SolidColor(VaultAccent),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = 12.dp,
                                    bottom = if (controlsVisible) 140.dp else 24.dp,
                                ),
                        )
                        if (saveError != null) {
                            Text(
                                saveError!!,
                                color = Color(0xFFFF6B6B),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = if (controlsVisible) 150.dp else 32.dp),
                            )
                        }
                    } else {
                    val contentMod = if (wrap) {
                        Modifier
                            .fillMaxWidth()
                            .widthIn(max = maxReadable)
                            .align(Alignment.TopCenter)
                    } else {
                        Modifier
                            .fillMaxSize()
                            .horizontalScroll(hScroll)
                    }

                    LazyColumn(
                        state = listState,
                        modifier = contentMod
                            .fillMaxHeight()
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { latestOnSingleTap() })
                            }
                            .padding(
                                start = 12.dp,
                                end = 12.dp,
                                top = 12.dp,
                                bottom = if (controlsVisible) 140.dp else 24.dp,
                            ),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        itemsIndexed(lines, key = { index, _ -> index }) { index, line ->
                            val isHit = matchLineIndices.getOrNull(matchIndex) == index &&
                                searchQuery.isNotEmpty()
                            val annotated = remember(line, searchQuery, isHit, palette, matchIndex) {
                                highlightLine(line, searchQuery, isHit, palette)
                            }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                if (lineNumbers) {
                                    Text(
                                        text = (index + 1).toString(),
                                        color = palette.muted,
                                        fontSize = (fontSp * 0.85f).sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier
                                            .width(48.dp)
                                            .padding(end = 8.dp),
                                        maxLines = 1,
                                    )
                                }
                                SelectionContainer(modifier = if (wrap) Modifier.weight(1f) else Modifier) {
                                    Text(
                                        text = annotated,
                                        color = palette.text,
                                        fontSize = fontSp.sp,
                                        fontFamily = if (monospace) {
                                            FontFamily.Monospace
                                        } else {
                                            FontFamily.SansSerif
                                        },
                                        softWrap = wrap,
                                        overflow = if (wrap) TextOverflow.Clip else TextOverflow.Visible,
                                        maxLines = if (wrap) Int.MAX_VALUE else 1,
                                    )
                                }
                            }
                        }
                        if (canLoadMore || bytesTruncated) {
                            item(key = "load_more_footer") {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    if (bytesTruncated || (text.length >= TextEncoding.HARD_MAX_CHARS)) {
                                        Text(
                                            "Large file — showing up to ${TextEncoding.HARD_MAX_CHARS / 1_000}k chars" +
                                                if (originalByteLength > 0) {
                                                    " (${formatBytes(originalByteLength)} source)"
                                                } else {
                                                    ""
                                                },
                                            color = palette.muted,
                                            fontSize = 12.sp,
                                        )
                                        Spacer(Modifier.height(8.dp))
                                    }
                                    if (canLoadMore) {
                                        TextButton(
                                            onClick = {
                                                bumpChrome()
                                                loadMoreCount++
                                            },
                                        ) {
                                            Text("Load more", color = VaultAccent)
                                        }
                                    }
                                }
                            }
                        }
                    }
                    } // end !editing

                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    TextReaderBottomChrome(
                        palette = palette,
                        encodingLabel = encodingLabel,
                        fontSp = fontSp,
                        theme = theme,
                        wrap = wrap,
                        monospace = monospace,
                        lineNumbers = lineNumbers,
                        editing = editing,
                        canEdit = onSave != null,
                        saveBusy = saveBusy,
                        searchOpen = searchOpen,
                        searchQuery = searchQuery,
                        matchIndex = matchIndex,
                        matchCount = matchLineIndices.size,
                        onToggleEdit = {
                            bumpChrome()
                            if (editing) {
                                editing = false
                                draft = fullText.orEmpty()
                                saveError = null
                            } else {
                                draft = fullText.orEmpty()
                                editing = true
                                searchOpen = false
                            }
                        },
                        onSaveEdit = {
                            val saver = onSave ?: return@TextReaderBottomChrome
                            bumpChrome()
                            scope.launch {
                                saveBusy = true
                                saveError = null
                                val result = saver(draft)
                                saveBusy = false
                                result.onSuccess {
                                    fullText = draft
                                    editing = false
                                }.onFailure {
                                    saveError = it.message ?: "Save failed"
                                }
                            }
                        },
                        onFontMinus = {
                            persistFont(
                                (fontSp - TextReaderPrefs.FONT_STEP_SP)
                                    .coerceAtLeast(TextReaderPrefs.MIN_FONT_SP),
                            )
                        },
                        onFontPlus = {
                            persistFont(
                                (fontSp + TextReaderPrefs.FONT_STEP_SP)
                                    .coerceAtMost(TextReaderPrefs.MAX_FONT_SP),
                            )
                        },
                        onCycleTheme = { cycleTheme() },
                        onToggleWrap = {
                            wrap = !wrap
                            prefs.setWrap(wrap)
                            bumpChrome()
                        },
                        onToggleMono = {
                            monospace = !monospace
                            prefs.setMonospace(monospace)
                            bumpChrome()
                        },
                        onToggleLineNumbers = {
                            lineNumbers = !lineNumbers
                            prefs.setLineNumbers(lineNumbers)
                            bumpChrome()
                        },
                        onToggleSearch = {
                            bumpChrome()
                            searchOpen = !searchOpen
                            if (!searchOpen) {
                                searchQuery = ""
                            }
                        },
                        onSearchQueryChange = {
                            bumpChrome()
                            searchQuery = it
                        },
                        onFindPrev = {
                            bumpChrome()
                            if (matchLineIndices.isEmpty()) return@TextReaderBottomChrome
                            val next = (matchIndex - 1 + matchLineIndices.size) % matchLineIndices.size
                            matchIndex = next
                            scope.launch {
                                listState.animateScrollToItem(matchLineIndices[next])
                            }
                        },
                        onFindNext = {
                            bumpChrome()
                            if (matchLineIndices.isEmpty()) return@TextReaderBottomChrome
                            val next = (matchIndex + 1) % matchLineIndices.size
                            matchIndex = next
                            scope.launch {
                                listState.animateScrollToItem(matchLineIndices[next])
                            }
                        },
                    )
                }
                } // BoxWithConstraints
            }
        }
    }
}

private data class TextReaderPalette(
    val background: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val highlight: Color,
    val highlightCurrent: Color,
)

private fun textReaderPalette(theme: TextReaderTheme): TextReaderPalette = when (theme) {
    TextReaderTheme.DARK -> TextReaderPalette(
        background = Color(0xFF0B0C0E),
        surface = Color(0xFF14161A),
        text = Color(0xFFF2F0EA),
        muted = Color(0xFF9A958C),
        highlight = Color(0x66C6A667),
        highlightCurrent = Color(0xAAC6A667),
    )
    TextReaderTheme.SEPIA -> TextReaderPalette(
        background = Color(0xFFF4ECD8),
        surface = Color(0xFFE8DCC0),
        text = Color(0xFF3B2F1E),
        muted = Color(0xFF7A6A50),
        highlight = Color(0x66C6A667),
        highlightCurrent = Color(0xAAC6A667),
    )
    TextReaderTheme.LIGHT -> TextReaderPalette(
        background = Color(0xFFF7F5F0),
        surface = Color(0xFFECEAE3),
        text = Color(0xFF1A1A1A),
        muted = Color(0xFF6B6860),
        highlight = Color(0x66C6A667),
        highlightCurrent = Color(0xAAC6A667),
    )
}

private fun highlightLine(
    line: String,
    query: String,
    isCurrentHitLine: Boolean,
    palette: TextReaderPalette,
) = buildAnnotatedString {
    if (query.isEmpty()) {
        append(line)
        return@buildAnnotatedString
    }
    var start = 0
    val lowerLine = line.lowercase()
    val lowerQuery = query.lowercase()
    while (start < line.length) {
        val idx = lowerLine.indexOf(lowerQuery, start)
        if (idx < 0) {
            append(line.substring(start))
            break
        }
        if (idx > start) append(line.substring(start, idx))
        withStyle(
            SpanStyle(
                background = if (isCurrentHitLine) palette.highlightCurrent else palette.highlight,
                color = palette.text,
            ),
        ) {
            append(line.substring(idx, idx + query.length))
        }
        start = idx + query.length.coerceAtLeast(1)
    }
}

@Composable
private fun TextReaderBottomChrome(
    palette: TextReaderPalette,
    encodingLabel: String,
    fontSp: Float,
    theme: TextReaderTheme,
    wrap: Boolean,
    monospace: Boolean,
    lineNumbers: Boolean,
    editing: Boolean = false,
    canEdit: Boolean = false,
    saveBusy: Boolean = false,
    searchOpen: Boolean,
    searchQuery: String,
    matchIndex: Int,
    matchCount: Int,
    onFontMinus: () -> Unit,
    onFontPlus: () -> Unit,
    onCycleTheme: () -> Unit,
    onToggleWrap: () -> Unit,
    onToggleMono: () -> Unit,
    onToggleLineNumbers: () -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onFindPrev: () -> Unit,
    onFindNext: () -> Unit,
    onToggleEdit: () -> Unit = {},
    onSaveEdit: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(palette.surface.copy(alpha = 0.94f))
            .navigationBarsPadding()
            .padding(bottom = 4.dp),
    ) {
        if (searchOpen) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = palette.text, fontSize = 15.sp),
                    cursorBrush = SolidColor(VaultAccent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onFindNext() }),
                    modifier = Modifier
                        .weight(1f)
                        .background(palette.background.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    decorationBox = { inner ->
                        Box {
                            if (searchQuery.isEmpty()) {
                                Text("Find in file…", color = palette.muted, fontSize = 15.sp)
                            }
                            inner()
                        }
                    },
                )
                Text(
                    if (matchCount == 0) "0/0" else "${matchIndex + 1}/$matchCount",
                    color = palette.muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                IconButton(onClick = onFindPrev) {
                    Icon(
                        Icons.Default.KeyboardArrowUp,
                        contentDescription = "Find previous",
                        tint = palette.text,
                    )
                }
                IconButton(onClick = onFindNext) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Find next",
                        tint = palette.text,
                    )
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                encodingLabel,
                color = palette.muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            TextButton(onClick = onFontMinus) {
                Text("A−", color = palette.text)
            }
            Text(
                "${fontSp.toInt()}sp",
                color = palette.muted,
                fontSize = 11.sp,
            )
            TextButton(onClick = onFontPlus) {
                Text("A+", color = palette.text)
            }
            IconButton(onClick = onCycleTheme) {
                Icon(
                    imageVector = when (theme) {
                        TextReaderTheme.DARK -> Icons.Default.DarkMode
                        TextReaderTheme.SEPIA -> Icons.Default.LightMode
                        TextReaderTheme.LIGHT -> Icons.Default.LightMode
                    },
                    contentDescription = "Theme ${theme.name.lowercase()}",
                    tint = VaultAccent,
                )
            }
            IconButton(onClick = onToggleWrap) {
                Icon(
                    Icons.AutoMirrored.Filled.WrapText,
                    contentDescription = if (wrap) "Word wrap on" else "Horizontal scroll",
                    tint = if (wrap) VaultAccent else palette.muted,
                )
            }
            IconButton(onClick = onToggleMono) {
                Icon(
                    Icons.Default.TextFields,
                    contentDescription = if (monospace) "Monospace" else "Sans-serif",
                    tint = if (monospace) VaultAccent else palette.muted,
                )
            }
            IconButton(onClick = onToggleLineNumbers) {
                Icon(
                    Icons.Default.FormatListNumbered,
                    contentDescription = if (lineNumbers) "Line numbers on" else "Line numbers off",
                    tint = if (lineNumbers) VaultAccent else palette.muted,
                )
            }
            IconButton(onClick = onToggleSearch, enabled = !editing) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (searchOpen) VaultAccent else palette.text,
                )
            }
            if (canEdit) {
                if (editing) {
                    IconButton(onClick = onSaveEdit, enabled = !saveBusy) {
                        Icon(
                            Icons.Default.Save,
                            contentDescription = "Save",
                            tint = VaultAccent,
                        )
                    }
                    IconButton(onClick = onToggleEdit, enabled = !saveBusy) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Cancel edit",
                            tint = palette.text,
                        )
                    }
                } else {
                    IconButton(onClick = onToggleEdit) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit text",
                            tint = palette.text,
                        )
                    }
                }
            }
        }
    }
}

private fun formatBytes(n: Int): String = when {
    n < 1024 -> "${n}B"
    n < 1024 * 1024 -> "${n / 1024}KB"
    else -> "%.1fMB".format(n / (1024f * 1024f))
}

private tailrec fun Context.findTextReaderActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findTextReaderActivity()
    else -> null
}
