package com.ziegler.kighelper.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.ziegler.kighelper.data.InfoCard
import com.ziegler.kighelper.data.ReceivedInfoCard
import com.ziegler.kighelper.data.SocialMediaEntry
import com.ziegler.kighelper.ui.InfoCardViewModel
import com.ziegler.kighelper.utils.WifiDirectInfoCardManager
import android.graphics.BitmapFactory
import java.io.File
import androidx.core.graphics.toColorInt


// Interface for Compose Preview support
interface InfoCardCollectionData {
    val infoCard: InfoCard
    val receivedCards: List<ReceivedInfoCard>
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InfoCardCollectionScreen(
    viewModel: InfoCardViewModel,
    contentPadding: PaddingValues,
    onNavigateToEdit: () -> Unit,
    onNavigateToSend: () -> Unit
) {
    InfoCardCollectionScreenImpl(
        data = object : InfoCardCollectionData {
            override val infoCard: InfoCard get() = viewModel.infoCard
            override val receivedCards: List<ReceivedInfoCard> get() = viewModel.receivedCards
        },
        viewModel = viewModel,
        contentPadding = contentPadding,
        onNavigateToEdit = onNavigateToEdit,
        onNavigateToSend = onNavigateToSend
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InfoCardCollectionScreenImpl(
    data: InfoCardCollectionData,
    viewModel: InfoCardViewModel?,
    contentPadding: PaddingValues,
    onNavigateToEdit: () -> Unit,
    onNavigateToSend: () -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current
    var zoomedCard by remember { mutableStateOf<InfoCard?>(null) }
    var zoomedReceivedCard by remember { mutableStateOf<ReceivedInfoCard?>(null) }
    var showReceiveDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel?.setBackgroundImage(context, uri)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        containerColor = MaterialTheme.colorScheme.background
    ) { insets ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(
                    start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
                    end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
                    bottom = contentPadding.calculateBottomPadding()
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(16.dp)) }

            // Top Section: Profile and Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    KigInfoCard(
                        card = data.infoCard,
                        modifier = Modifier.weight(1f).clickable { 
                            zoomedCard = data.infoCard 
                            zoomedReceivedCard = null
                        },
                        isLarge = true,
                        onEditClick = onNavigateToEdit
                    )

                    Column(
                        modifier = Modifier.weight(1f).aspectRatio(0.78f),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            onClick = onNavigateToSend,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("发送名片", fontWeight = FontWeight.Bold)
                            }
                        }

                        Surface(
                            onClick = { showReceiveDialog = true },
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f))
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(32.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("接收名片", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Divider and Section Title
            item {
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "已收集的卡片",
                    modifier = Modifier.padding(vertical = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            // Grid of Received Cards
            val chunkedCards = data.receivedCards.chunked(3)
            if (chunkedCards.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("暂无收集的卡片", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                items(chunkedCards) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { received ->
                            KigInfoCard(
                                card = received.card,
                                modifier = Modifier.weight(1f).clickable { 
                                    zoomedCard = received.card 
                                    zoomedReceivedCard = received
                                },
                                isLarge = false
                            )
                        }
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }

    if (showReceiveDialog && viewModel != null) {
        ReceiveCardDialog(
            viewModel = viewModel,
            onDismiss = { showReceiveDialog = false }
        )
    }

    if (zoomedCard != null) {
        Dialog(
            onDismissRequest = { 
                zoomedCard = null 
                zoomedReceivedCard = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { 
                        zoomedCard = null 
                        zoomedReceivedCard = null
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    KigInfoCard(
                        card = zoomedCard!!,
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .clickable(enabled = false) { },
                        isLarge = true,
                        showSocialLinks = true
                    )
                    
                    if (zoomedCard?.id == data.infoCard.id) {
                        Surface(
                            onClick = {
                                zoomedCard = null
                                onNavigateToEdit()
                            },
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "编辑资料",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                            },
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.AddPhotoAlternate,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "更换背景",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else if (zoomedReceivedCard != null) {
                        Surface(
                            onClick = {
                                viewModel?.deleteReceivedCard(zoomedReceivedCard!!)
                                zoomedCard = null
                                zoomedReceivedCard = null
                            },
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    "删除卡片",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
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
private fun KigInfoCard(
    card: InfoCard,
    modifier: Modifier = Modifier,
    isLarge: Boolean = false,
    showSocialLinks: Boolean = false,
    onEditClick: (() -> Unit)? = null
) {
    val backgroundColor = if (isLarge) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val shapeColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
    val contentColor = if (card.backgroundImagePath != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    val imageBitmap = remember(card.backgroundImagePath) {
        card.backgroundImagePath?.let { path -> 
            val file = File(path)
            if (file.exists()) {
                BitmapFactory.decodeFile(path)?.asImageBitmap()
            } else null
        }
    }

    Card(
        modifier = modifier.aspectRatio(0.78f),
        shape = RoundedCornerShape(if (isLarge) 32.dp else 24.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Overlay to ensure text readability
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.25f)))
            }

            Box(modifier = Modifier.fillMaxSize().padding(if (isLarge) 16.dp else 12.dp)) {
                if (onEditClick != null) {
                    Surface(
                        onClick = onEditClick,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(if (isLarge) 32.dp else 26.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "编辑",
                                modifier = Modifier.size(if (isLarge) 16.dp else 12.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                if (imageBitmap == null) {
                    // Decorative Shapes - Only show if no background image
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(bottom = if (isLarge) 24.dp else 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(if (isLarge) 12.dp else 6.dp)
                    ) {
                        // Top shape: Rounded Triangle/Shield
                        Box(
                            modifier = Modifier
                                .size(if (isLarge) 56.dp else 32.dp)
                                .background(shapeColor, RoundedCornerShape(topStart = 50.dp, topEnd = 50.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(if (isLarge) 10.dp else 5.dp)) {
                            // Left shape: Gear-ish
                            Box(
                                modifier = Modifier
                                    .size(if (isLarge) 42.dp else 24.dp)
                                    .background(shapeColor, RoundedCornerShape(10.dp))
                            )
                            // Right shape: Rounded Square
                            Box(
                                modifier = Modifier
                                    .size(if (isLarge) 42.dp else 24.dp)
                                    .background(shapeColor, RoundedCornerShape(10.dp))
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.align(Alignment.BottomStart),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = card.name.ifBlank { "未命名" },
                        style = if (isLarge) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = contentColor,
                        maxLines = 1
                    )
                    
                    if (showSocialLinks) {
                        card.socialEntries.take(3).forEach { entry ->
                            if (entry.platform.isNotBlank() || entry.handle.isNotBlank()) {
                                Text(
                                    text = "${entry.platform}: ${entry.handle}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = contentColor.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("UseKtx")
private fun parseThemeColor(raw: String): Color {
    return runCatching { Color(raw.toColorInt()) }
        .getOrElse { Color(0xFF6750A4) }
}

// --- Compose Preview Support ---

private class PreviewInfoCardCollectionData : InfoCardCollectionData {
    override val infoCard: InfoCard = InfoCard(
        name = "张三",
        themeColorHex = "#6750A4",
        socialEntries = listOf(
            SocialMediaEntry("微信", "zhangsan123"),
            SocialMediaEntry("QQ", "12345678")
        )
    )
    override val receivedCards: List<ReceivedInfoCard> = List(9) { index ->
        ReceivedInfoCard(
            card = InfoCard(
                name = "朋友 $index",
                socialEntries = listOf(SocialMediaEntry("微博", "friend_$index"))
            ),
            source = "Wi-Fi Direct"
        )
    }
}

@Composable
private fun ReceiveCardDialog(
    viewModel: InfoCardViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var wifiStatus by remember { mutableStateOf("就绪") }
    var transferState by remember { mutableStateOf("等待发送者...") }

    val wifiManager = remember {
        WifiDirectInfoCardManager(
            context = context,
            onStatusChanged = { wifiStatus = it },
            onPeersChanged = { /* Not used in receive screen */ },
            onTransferReceived = { bytes ->
                viewModel.importFromSharePackageBytes(context, bytes, "Wi-Fi Direct") { success ->
                    if (success) {
                        Toast.makeText(context, "已接收信息卡", Toast.LENGTH_SHORT).show()
                        onDismiss()
                    }
                }
            },
            onTransferResult = { success, message ->
                transferState = if (success) "完成: $message" else "状态: $message"
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            wifiManager.prepareReceive()
            wifiManager.discoverPeers()
        }
    }

    fun ensurePermissions(onGranted: () -> Unit) {
        val required = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }
        val missing = required.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) onGranted() else permissionLauncher.launch(missing.toTypedArray())
    }

    DisposableEffect(Unit) {
        wifiManager.start()
        ensurePermissions {
            wifiManager.prepareReceive()
            wifiManager.discoverPeers()
        }
        onDispose { wifiManager.release() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                @OptIn(ExperimentalMaterial3Api::class)
                androidx.compose.material3.TopAppBar(
                    title = { Text("接收名片") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "关闭")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(64.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            "正在等待发送者",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "请保持此页面开启",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(48.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = wifiStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Text(
                    text = transferState,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(modifier = Modifier.height(32.dp))
                
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp).padding(horizontal = 32.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "信息卡收藏预览")
@Composable
fun InfoCardCollectionScreenPreview() {
    MaterialTheme {
        InfoCardCollectionScreenImpl(
            data = PreviewInfoCardCollectionData(),
            viewModel = null,
            contentPadding = PaddingValues(0.dp),
            onNavigateToEdit = {},
            onNavigateToSend = {}
        )
    }
}


