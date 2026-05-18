package com.ziegler.kighelper.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ziegler.kighelper.data.InfoCard
import com.ziegler.kighelper.data.SocialMediaEntry
import com.ziegler.kighelper.ui.InfoCardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditInfoCardScreen(
    viewModel: InfoCardViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.setBackgroundImage(context, uri)
        }
    }

    EditInfoCardScreenImpl(
        infoCard = viewModel.infoCard,
        onUpdateName = { viewModel.updateName(it) },
        onAddSocialEntry = { viewModel.addSocialEntry() },
        onRemoveSocialEntry = { viewModel.removeSocialEntry(it) },
        onUpdateSocialEntry = { index, platform, handle -> 
            viewModel.updateSocialEntry(index, platform, handle) 
        },
        onPickImage = {
            imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onClearImage = { viewModel.clearBackgroundImage() },
        contentPadding = contentPadding,
        onBack = onBack
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditInfoCardScreenImpl(
    infoCard: InfoCard,
    onUpdateName: (String) -> Unit,
    onAddSocialEntry: () -> Unit,
    onRemoveSocialEntry: (Int) -> Unit,
    onUpdateSocialEntry: (Int, String?, String?) -> Unit,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("编辑个人名片") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection)
                )
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            item {
                Text("基本信息", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = infoCard.name,
                    onValueChange = onUpdateName,
                    label = { Text("姓名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }

            item {
                Text("个性化", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Surface(
                            modifier = Modifier.size(60.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            if (infoCard.backgroundImagePath != null) {
                                val bitmap = remember(infoCard.backgroundImagePath) {
                                    try {
                                        android.graphics.BitmapFactory.decodeFile(infoCard.backgroundImagePath)?.asImageBitmap()
                                    } catch (e: Exception) {
                                        null
                                    }
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            } else {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        Icons.Default.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                        
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("背景图片", style = MaterialTheme.typography.bodyLarge)
                            Row {
                                TextButton(onClick = onPickImage) {
                                    Text(if (infoCard.backgroundImagePath == null) "选择图片" else "更换图片")
                                }
                                if (infoCard.backgroundImagePath != null) {
                                    TextButton(onClick = onClearImage, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                        Text("移除")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("社交链接", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onAddSocialEntry) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Text("添加")
                    }
                }
            }

            itemsIndexed(infoCard.socialEntries) { index, entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = entry.platform,
                                onValueChange = { onUpdateSocialEntry(index, it, null) },
                                label = { Text("平台 (如: 微信, QQ)") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            IconButton(onClick = { onRemoveSocialEntry(index) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        OutlinedTextField(
                            value = entry.handle,
                            onValueChange = { onUpdateSocialEntry(index, null, it) },
                            label = { Text("账号/ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(32.dp)) }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun EditInfoCardScreenPreview() {
    MaterialTheme {
        EditInfoCardScreenImpl(
            infoCard = InfoCard(
                name = "预览用户",
                socialEntries = listOf(
                    SocialMediaEntry("微信", "preview_wechat"),
                    SocialMediaEntry("QQ", "123456")
                )
            ),
            onUpdateName = {},
            onAddSocialEntry = {},
            onRemoveSocialEntry = {},
            onUpdateSocialEntry = { _, _, _ -> },
            onPickImage = {},
            onClearImage = {},
            contentPadding = PaddingValues(0.dp),
            onBack = {}
        )
    }
}

