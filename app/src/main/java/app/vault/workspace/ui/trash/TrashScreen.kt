package app.vault.workspace.ui.trash

import app.vault.workspace.ui.nav.VaultMotion
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vault.workspace.data.VaultCategory
import app.vault.workspace.ui.library.ThumbCache
import app.vault.workspace.data.VaultItem
import app.vault.workspace.data.formatHumanSize
import app.vault.workspace.data.formatReadableDate
import app.vault.workspace.ui.theme.VaultAccent
import app.vault.workspace.ui.theme.VaultAmoled
import app.vault.workspace.ui.theme.VaultDanger
import app.vault.workspace.ui.theme.VaultSurface
import app.vault.workspace.ui.theme.VaultText
import app.vault.workspace.ui.theme.VaultTextMuted

private val TrashThumbShape = RoundedCornerShape(14.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    items: List<VaultItem>,
    onBack: () -> Unit,
    onRestore: (VaultItem) -> Unit,
    onDeleteForever: (VaultItem) -> Unit,
    onEmptyTrash: () -> Unit,
    onLoadThumb: suspend (id: String) -> Bitmap?,
) {
    var confirmEmpty by remember { mutableStateOf(false) }
    var pendingHardDelete by remember { mutableStateOf<VaultItem?>(null) }

    Scaffold(
        containerColor = VaultAmoled,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Trash",
                            fontWeight = FontWeight.SemiBold,
                            color = VaultText,
                        )
                        Text(
                            if (items.isEmpty()) "Empty" else "${items.size} items",
                            color = VaultTextMuted,
                            style = MaterialTheme.typography.labelMedium,
                            fontSize = 12.sp,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = VaultText,
                        )
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        TextButton(onClick = { confirmEmpty = true }) {
                            Text("Empty", color = VaultDanger, fontWeight = FontWeight.SemiBold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = VaultAmoled,
                    titleContentColor = VaultText,
                    navigationIconContentColor = VaultText,
                    actionIconContentColor = VaultDanger,
                ),
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (items.isEmpty()) {
                Column(
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 36.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(VaultSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = VaultAccent,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    Text(
                        "Trash is empty",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = VaultText,
                    )
                    Text(
                        "Items you move to trash appear here.",
                        color = VaultTextMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.widthIn(max = 280.dp),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 104.dp),
                    contentPadding = PaddingValues(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items, key = { it.id }) { item ->
                        TrashCard(
                            item = item,
                            onLoadThumb = onLoadThumb,
                            onRestore = { onRestore(item) },
                            onDeleteForever = { pendingHardDelete = item },
                        )
                    }
                }
            }
        }
    }

    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            properties = VaultMotion.dialogProperties,
            title = {
                Text("Empty trash?", fontWeight = FontWeight.SemiBold, color = VaultText)
            },
            text = {
                Text(
                    "Permanently delete ${items.size} item(s)? This cannot be undone.",
                    color = VaultTextMuted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmEmpty = false
                        onEmptyTrash()
                    },
                ) {
                    Text("Delete forever", color = VaultDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) {
                    Text("Cancel", color = VaultTextMuted)
                }
            },
            containerColor = VaultSurface,
        )
    }

    pendingHardDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingHardDelete = null },
            properties = VaultMotion.dialogProperties,
            containerColor = VaultSurface,
            title = {
                Text("Delete forever?", fontWeight = FontWeight.SemiBold, color = VaultText)
            },
            text = {
                Text(
                    "“${item.displayName}” will be permanently deleted.",
                    color = VaultTextMuted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingHardDelete = null
                        onDeleteForever(item)
                    },
                ) {
                    Text("Delete forever", color = VaultDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingHardDelete = null }) {
                    Text("Cancel", color = VaultTextMuted)
                }
            },
        )
    }
}

@Composable
private fun TrashCard(
    item: VaultItem,
    onLoadThumb: suspend (String) -> Bitmap?,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    val thumb by produceState<Bitmap?>(
        initialValue = if (item.hasThumb) ThumbCache.peek(item.id) else null,
        item.id,
        item.hasThumb,
    ) {
        value = if (item.hasThumb) {
            runCatching { ThumbCache.get(item.id) { onLoadThumb(item.id) } }.getOrNull()
        } else {
            null
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f),
        colors = CardDefaults.cardColors(containerColor = VaultSurface),
        shape = TrashThumbShape,
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (thumb != null) {
                    val imageBitmap = remember(thumb) { thumb!!.asImageBitmap() }
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = item.displayName,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(TrashThumbShape),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(48.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xCC000000)),
                                ),
                            ),
                    )
                } else {
                    Icon(
                        imageVector = categoryIcon(item.category),
                        contentDescription = null,
                        tint = VaultAccent,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(36.dp),
                    )
                }
                Text(
                    item.displayName,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge,
                    color = VaultText,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                )
            }
            Text(
                buildString {
                    append(formatHumanSize(item.sizeBytes))
                    item.deletedAt?.let {
                        append(" · ")
                        append(formatReadableDate(it))
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = VaultTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                IconButton(onClick = onRestore) {
                    Icon(
                        Icons.Default.RestoreFromTrash,
                        contentDescription = "Restore",
                        tint = VaultAccent,
                    )
                }
                IconButton(onClick = onDeleteForever) {
                    Icon(
                        Icons.Default.DeleteForever,
                        contentDescription = "Delete forever",
                        tint = VaultDanger,
                    )
                }
            }
        }
    }
}

private fun categoryIcon(category: VaultCategory): ImageVector =
    when (category) {
        VaultCategory.IMAGE -> Icons.Default.Image
        VaultCategory.VIDEO -> Icons.Default.VideoFile
        VaultCategory.AUDIO -> Icons.Default.AudioFile
        VaultCategory.DOCUMENT -> Icons.Default.Description
        VaultCategory.OTHER -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
