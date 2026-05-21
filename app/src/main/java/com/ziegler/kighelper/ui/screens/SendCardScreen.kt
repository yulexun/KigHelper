package com.ziegler.kighelper.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ziegler.kighelper.ui.InfoCardViewModel
import com.ziegler.kighelper.utils.WifiDirectInfoCardManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendCardScreen(
    viewModel: InfoCardViewModel,
    contentPadding: PaddingValues,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    var wifiStatus by remember { mutableStateOf("就绪") }
    val peers = remember { mutableStateListOf<WifiDirectInfoCardManager.PeerDevice>() }
    var transferState by remember { mutableStateOf("请选择设备") }

    val wifiManager = remember {
        WifiDirectInfoCardManager(
            context = context,
            onStatusChanged = { wifiStatus = it },
            onPeersChanged = { newPeers ->
                peers.clear()
                peers.addAll(newPeers)
            },
            onTransferReceived = { /* Not used in send screen */ },
            onTransferResult = { success, message ->
                transferState = if (success) "完成: $message" else "状态: $message"
                if (success) {
                    Toast.makeText(context, "发送成功", Toast.LENGTH_SHORT).show()
                    onBack()
                }
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
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
            runCatching { ContextCompat.checkSelfPermission(context, it) }.getOrDefault(PackageManager.PERMISSION_DENIED) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) onGranted() else permissionLauncher.launch(missing.toTypedArray())
    }

    DisposableEffect(Unit) {
        wifiManager.start()
        ensurePermissions { 
            wifiManager.discoverPeers() 
        }
        onDispose { 
            wifiManager.release() 
        }
    }

    // Automatic connection logic removed with QR code bootstrap

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("发送名片") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { 
                        ensurePermissions { 
                            wifiManager.discoverPeers() 
                        } 
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(
                    start = contentPadding.calculateStartPadding(layoutDirection),
                    end = contentPadding.calculateEndPadding(layoutDirection)
                )
                .padding(horizontal = 16.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("当前状态: $wifiStatus", style = MaterialTheme.typography.bodyMedium)
                    Text("传输进度: $transferState", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }

            Text("附近可见设备", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))

            if (peers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("正在搜索设备...", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 32.dp)
                ) {
                    items(peers) { peer ->
                        Surface(
                            onClick = {
                                transferState = "生成包中..."
                                viewModel.exportSharePackageBytes(context) { bytes ->
                                    if (bytes != null) {
                                        transferState = "正在发送到 ${peer.name}"
                                        wifiManager.prepareSend(bytes)
                                        wifiManager.connect(peer.address)
                                    } else {
                                        transferState = "生成失败"
                                    }
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(peer.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                                    Text(peer.address, fontSize = 10.sp, color = Color.Gray)
                                }
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.primary
                                ) {
                                    Icon(
                                        Icons.Default.FileUpload,
                                        contentDescription = null, 
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(8.dp).size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
