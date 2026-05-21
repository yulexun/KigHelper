package com.ziegler.kighelper.utils

import android.annotation.SuppressLint
import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

class WifiDirectInfoCardManager(
	context: Context,
	private val onStatusChanged: (String) -> Unit,
	private val onPeersChanged: (List<PeerDevice>) -> Unit,
	private val onTransferReceived: (ByteArray) -> Unit,
	private val onTransferResult: (Boolean, String) -> Unit
) {
	private val mainHandler = android.os.Handler(context.mainLooper)

	private fun runOnMain(block: () -> Unit) {
		mainHandler.post(block)
	}

	data class PeerDevice(
		val name: String,
		val address: String
	)

	private enum class TransferMode {
		IDLE,
		SEND,
		RECEIVE
	}

	private val appContext = context.applicationContext
	private val wifiManager = appContext.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
	private val channel = wifiManager?.initialize(appContext, appContext.mainLooper, null)
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	private var receiverRegistered = false
	private var pendingPayload: ByteArray? = null
	private var transferMode: TransferMode = TransferMode.IDLE
	private var transferJob: Job? = null

	private val peerListListener = WifiP2pManager.PeerListListener { peers ->
		val mappedPeers = peers.deviceList.map {
			PeerDevice(
				name = it.deviceName.takeIf { value -> value.isNotBlank() } ?: "未知设备",
				address = it.deviceAddress
			)
		}
		runOnMain { onPeersChanged(mappedPeers) }
	}

	private val connectionInfoListener: WifiP2pManager.ConnectionInfoListener = WifiP2pManager.ConnectionInfoListener { info ->
		if (!info.groupFormed) {
			runOnMain { onStatusChanged("连接已断开或未建立") }
			return@ConnectionInfoListener
		}

		val owner = info.groupOwnerAddress?.hostAddress.orEmpty()
		if (owner.isEmpty() && !info.isGroupOwner) {
			runOnMain { onStatusChanged("等待组主地址...") }
			// Sometimes IP is not immediately available, request again after a short delay
			mainHandler.postDelayed({
				if (hasWifiDirectPermission()) {
					wifiManager?.requestConnectionInfo(channel, connectionInfoListener)
				}
			}, 1000)
			return@ConnectionInfoListener
		}

		runOnMain { onStatusChanged("已连接，组主: ${if (info.isGroupOwner) "本机" else owner}") }

		when (transferMode) {
			TransferMode.SEND -> {
				val payload = pendingPayload
				if (payload == null) {
					runOnMain { onTransferResult(false, "未找到可发送数据") }
					return@ConnectionInfoListener
				}
				if (info.isGroupOwner) {
					startServerTransfer(mode = TransferMode.SEND, payload = payload)
				} else {
					startClientTransfer(
						mode = TransferMode.SEND,
						payload = payload,
						groupOwnerAddress = info.groupOwnerAddress
					)
				}
			}

			TransferMode.RECEIVE -> {
				if (info.isGroupOwner) {
					startServerTransfer(mode = TransferMode.RECEIVE, payload = null)
				} else {
					startClientTransfer(
						mode = TransferMode.RECEIVE,
						payload = null,
						groupOwnerAddress = info.groupOwnerAddress
					)
				}
			}

			TransferMode.IDLE -> Unit
		}
	}

	private val receiver = object : BroadcastReceiver() {
		@SuppressLint("MissingPermission")
		override fun onReceive(context: Context, intent: Intent) {
			when (intent.action) {
				WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
					val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
					val enabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
					runOnMain { onStatusChanged(if (enabled) "Wi-Fi Direct 已开启" else "请先开启 Wi-Fi Direct") }
				}

				WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
					if (hasWifiDirectPermission()) {
						wifiManager?.requestPeers(channel, peerListListener)
					}
				}

				WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
					if (hasWifiDirectPermission()) {
						wifiManager?.requestConnectionInfo(channel, connectionInfoListener)
					} else {
						runOnMain { onStatusChanged("未连接") }
						// Auto-restart discovery if we are in RECEIVE mode to be ready for next sender
						if (transferMode == TransferMode.RECEIVE) {
							startDiscovery()
						}
					}
				}
			}
		}
	}

	fun start() {
		registerReceiverIfNeeded()
		onStatusChanged("Wi-Fi Direct 已就绪")
	}

	fun stop() {
		pendingPayload = null
		transferMode = TransferMode.IDLE
		transferJob?.cancel()
		transferJob = null
		if (receiverRegistered) {
			runCatching { appContext.unregisterReceiver(receiver) }
			receiverRegistered = false
		}
		onPeersChanged(emptyList())
		onStatusChanged("Wi-Fi Direct 已停止")
	}

	@SuppressLint("MissingPermission")
	fun discoverPeers() {
		if (!hasWifiDirectPermission()) {
			onTransferResult(false, "缺少 Wi-Fi Direct 所需权限")
			return
		}
		// Reset state before discovery to improve reliability
		wifiManager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
			override fun onSuccess() {
				startDiscovery()
			}
			override fun onFailure(reason: Int) {
				startDiscovery()
			}
		})
	}

	@SuppressLint("MissingPermission")
	private fun startDiscovery() {
		wifiManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
			override fun onSuccess() {
				runOnMain { onStatusChanged("正在搜索附近设备...") }
			}

			override fun onFailure(reason: Int) {
				runOnMain { onTransferResult(false, "搜索设备失败: $reason") }
			}
		})
	}

	fun prepareSend(payload: ByteArray) {
		pendingPayload = payload
		transferMode = TransferMode.SEND
		runOnMain { onStatusChanged("已准备发送，请选择设备") }
	}

	fun prepareReceive() {
		pendingPayload = null
		transferMode = TransferMode.RECEIVE
		runOnMain { onStatusChanged("已准备接收，等待连接") }
	}

	@SuppressLint("MissingPermission")
	fun connect(peerAddress: String) {
		if (!hasWifiDirectPermission()) {
			onTransferResult(false, "缺少 Wi-Fi Direct 所需权限")
			return
		}

		// Reset existing group state before connecting to avoid "no response" issues
		wifiManager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
			override fun onSuccess() {
				performConnect(peerAddress)
			}
			override fun onFailure(reason: Int) {
				// Fails if no group exists, which is common, proceed to connect
				performConnect(peerAddress)
			}
		})
	}

	@SuppressLint("MissingPermission")
	private fun performConnect(peerAddress: String) {
		val config = WifiP2pConfig().apply {
			deviceAddress = peerAddress
			wps.setup = WpsInfo.PBC
			// Set high priority for group ownership if sending to ensure we can start server if needed
			groupOwnerIntent = 15
		}
		wifiManager?.connect(channel, config, object : WifiP2pManager.ActionListener {
			override fun onSuccess() {
				runOnMain { onStatusChanged("连接请求已发送") }
			}

			override fun onFailure(reason: Int) {
				runOnMain { onTransferResult(false, "连接失败: $reason") }
			}
		})
	}

	@SuppressLint("MissingPermission")
	fun disconnect() {
		if (!hasWifiDirectPermission()) return
		wifiManager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
			override fun onSuccess() {
				runOnMain { onStatusChanged("已断开连接") }
			}

			override fun onFailure(reason: Int) {
				runOnMain { onTransferResult(false, "断开失败: $reason") }
			}
		})
	}

	fun release() {
		disconnect()
		stop()
		scope.cancel()
	}

	private fun startServerTransfer(mode: TransferMode, payload: ByteArray?) {
		transferJob?.cancel()
		transferJob = scope.launch {
			runCatching {
				ServerSocket(PORT).use { server ->
					server.soTimeout = SOCKET_TIMEOUT_MS
					server.accept().use { socket ->
						when (mode) {
							TransferMode.SEND -> writePayload(socket, payload ?: ByteArray(0))
							TransferMode.RECEIVE -> readPayload(socket)
							TransferMode.IDLE -> Unit
						}
					}
				}
			}.onFailure {
				if (it !is java.net.SocketTimeoutException) {
					runOnMain { onTransferResult(false, "传输失败: ${it.message.orEmpty()}") }
				}
			}
		}
	}

	private fun startClientTransfer(mode: TransferMode, payload: ByteArray?, groupOwnerAddress: InetAddress?) {
		transferJob?.cancel()
		transferJob = scope.launch {
			runCatching {
				val host = groupOwnerAddress ?: error("组主地址不可用")
				Socket(host, PORT).use { socket ->
					when (mode) {
						TransferMode.SEND -> writePayload(socket, payload ?: ByteArray(0))
						TransferMode.RECEIVE -> readPayload(socket)
						TransferMode.IDLE -> Unit
					}
				}
			}.onFailure {
				runOnMain { onTransferResult(false, "传输失败: ${it.message.orEmpty()}") }
			}
		}
	}

	private fun writePayload(socket: Socket, payload: ByteArray) {
		runCatching {
			DataOutputStream(socket.getOutputStream()).use { output ->
				output.writeInt(payload.size)
				output.write(payload)
				output.flush()
			}
			runOnMain { onTransferResult(true, "发送完成") }
		}.onFailure {
			runOnMain { onTransferResult(false, "发送失败: ${it.message}") }
		}
	}

	private fun readPayload(socket: Socket) {
		runCatching {
			DataInputStream(socket.getInputStream()).use { input ->
				val length = input.readInt()
				if (length <= 0 || length > MAX_PAYLOAD_BYTES) {
					error("无效数据大小")
				}
				val buffer = ByteArray(length)
				input.readFully(buffer)
				runOnMain { onTransferReceived(buffer) }
			}
			runOnMain { onTransferResult(true, "接收完成") }
		}.onFailure {
			runOnMain { onTransferResult(false, "接收失败: ${it.message}") }
		}
	}

	private fun registerReceiverIfNeeded() {
		if (receiverRegistered) return
		val filter = IntentFilter().apply {
			addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
			addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
			addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
		}
		appContext.registerReceiver(receiver, filter)
		receiverRegistered = true
	}

	private fun hasWifiDirectPermission(): Boolean {
		val hasNearby = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			ContextCompat.checkSelfPermission(
				appContext,
				Manifest.permission.NEARBY_WIFI_DEVICES
			) == PackageManager.PERMISSION_GRANTED
		} else {
			true
		}
		val hasLocation = ContextCompat.checkSelfPermission(
			appContext,
			Manifest.permission.ACCESS_FINE_LOCATION
		) == PackageManager.PERMISSION_GRANTED
		return hasNearby && hasLocation
	}

	private companion object {
		private const val PORT = 8988
		private const val SOCKET_TIMEOUT_MS = 60_000
		private const val MAX_PAYLOAD_BYTES = 10 * 1024 * 1024
	}
}

