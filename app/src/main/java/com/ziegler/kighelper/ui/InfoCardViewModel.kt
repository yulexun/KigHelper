package com.ziegler.kighelper.ui

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ziegler.kighelper.data.InfoCard
import com.ziegler.kighelper.data.InfoCardRepository
import com.ziegler.kighelper.data.ReceivedInfoCard
import com.ziegler.kighelper.data.SocialMediaEntry
import com.ziegler.kighelper.utils.InfoCardTransferUtils
import kotlinx.coroutines.launch

class InfoCardViewModel(
    private val repository: InfoCardRepository
) : ViewModel() {
    var infoCard by mutableStateOf(InfoCard.empty())
        private set
    var receivedCards by mutableStateOf<List<ReceivedInfoCard>>(emptyList())
        private set

    init {
        loadCard()
        loadReceivedCards()
    }

    fun updateName(name: String) {
        infoCard = infoCard.copy(name = name)
        persist()
    }

    fun updateThemeColor(colorHex: String) {
        infoCard = infoCard.copy(themeColorHex = InfoCard.normalizeColor(colorHex))
        persist()
    }

    fun updateSocialEntry(index: Int, platform: String? = null, handle: String? = null) {
        if (index !in infoCard.socialEntries.indices) return

        val updated = infoCard.socialEntries.toMutableList()
        val current = updated[index]
        updated[index] = current.copy(
            platform = platform ?: current.platform,
            handle = handle ?: current.handle
        )
        infoCard = infoCard.copy(socialEntries = updated)
        persist()
    }

    fun addSocialEntry() {
        val updated = infoCard.socialEntries.toMutableList()
        updated.add(SocialMediaEntry())
        infoCard = infoCard.copy(socialEntries = updated)
        persist()
    }

    fun removeSocialEntry(index: Int) {
        if (index !in infoCard.socialEntries.indices) return

        val updated = infoCard.socialEntries.toMutableList()
        updated.removeAt(index)
        infoCard = infoCard.copy(
            socialEntries = if (updated.isEmpty()) listOf(SocialMediaEntry()) else updated
        )
        persist()
    }

    fun setBackgroundImage(context: Context, uri: Uri) {
        viewModelScope.launch {
            val processedPath = InfoCardTransferUtils.processBackgroundImageToWebp(context, uri)
            if (processedPath != null) {
                infoCard = infoCard.copy(backgroundImagePath = processedPath)
                persist()
            }
        }
    }

    fun clearBackgroundImage() {
        infoCard = infoCard.copy(backgroundImagePath = null)
        persist()
    }

    fun importFromSharePackage(context: Context, uri: Uri, onImported: (Boolean) -> Unit) {
        viewModelScope.launch {
            val imported = InfoCardTransferUtils.importSharePackage(context, uri)
            if (imported != null) {
                appendReceivedCard(imported, source = "系统分享")
                onImported(true)
            } else {
                onImported(false)
            }
        }
    }

    fun exportSharePackage(context: Context, onExported: (Uri?) -> Unit) {
        viewModelScope.launch {
            onExported(InfoCardTransferUtils.exportSharePackage(context, infoCard))
        }
    }

    fun exportSharePackageBytes(context: Context, onExported: (ByteArray?) -> Unit) {
        viewModelScope.launch {
            onExported(InfoCardTransferUtils.exportSharePackageBytes(context, infoCard))
        }
    }

    fun importFromSharePackageBytes(context: Context, bytes: ByteArray, source: String, onImported: (Boolean) -> Unit) {
        viewModelScope.launch {
            val imported = InfoCardTransferUtils.importSharePackageBytes(context, bytes)
            if (imported != null) {
                appendReceivedCard(imported, source)
                onImported(true)
            } else {
                onImported(false)
            }
        }
    }

    fun deleteReceivedCard(card: ReceivedInfoCard) {
        val updated = receivedCards.filter { it.id != card.id }
        receivedCards = updated
        viewModelScope.launch {
            repository.saveReceivedCards(updated)
        }
    }

    private fun loadCard() {
        viewModelScope.launch {
            infoCard = repository.getCard()
        }
    }

    private fun loadReceivedCards() {
        viewModelScope.launch {
            receivedCards = repository.getReceivedCards()
        }
    }

    private fun appendReceivedCard(card: InfoCard, source: String) {
        val updated = listOf(
            ReceivedInfoCard(card = card.normalized(), source = source)
        ) + receivedCards
        receivedCards = updated
        viewModelScope.launch {
            repository.saveReceivedCards(updated)
        }
    }

    private fun persist() {
        val snapshot = infoCard.normalized()
        infoCard = snapshot
        viewModelScope.launch {
            repository.saveCard(snapshot)
        }
    }
}

class InfoCardViewModelFactory(
    private val repository: InfoCardRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(InfoCardViewModel::class.java)) {
            return InfoCardViewModel(repository) as T
        }

        throw IllegalArgumentException("未知的 ViewModel 类型: ${modelClass.name}")
    }
}

