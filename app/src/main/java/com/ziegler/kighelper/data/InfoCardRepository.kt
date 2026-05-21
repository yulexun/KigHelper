package com.ziegler.kighelper.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface InfoCardRepository {
    suspend fun getCard(): InfoCard
    suspend fun saveCard(card: InfoCard)
    suspend fun getReceivedCards(): List<ReceivedInfoCard>
    suspend fun saveReceivedCards(cards: List<ReceivedInfoCard>)
}

class SharedPreferencesInfoCardRepository(
    context: Context,
    private val gson: Gson = Gson()
) : InfoCardRepository {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override suspend fun getCard(): InfoCard = withContext(Dispatchers.IO) {
        val json = prefs.getString(INFO_CARD_KEY, null) ?: return@withContext InfoCard.empty()

        runCatching {
            gson.fromJson(json, InfoCard::class.java)?.normalized() ?: InfoCard.empty()
        }.getOrElse { error ->
            Log.w(TAG, "信息卡解析失败，已回退到默认信息卡", error)
            InfoCard.empty()
        }
    }

    override suspend fun saveCard(card: InfoCard) = withContext(Dispatchers.IO) {
        prefs.edit(commit = true) {
            putString(INFO_CARD_KEY, gson.toJson(card.normalized()))
        }
    }

    override suspend fun getReceivedCards(): List<ReceivedInfoCard> = withContext(Dispatchers.IO) {
        val json = prefs.getString(RECEIVED_CARDS_KEY, null) ?: return@withContext emptyList()

        runCatching {
            gson.fromJson(json, Array<ReceivedInfoCard>::class.java)
                ?.map { item ->
                    item.copy(card = item.card.normalized())
                }
                ?: emptyList()
        }.getOrElse { error ->
            Log.w(TAG, "收到的信息卡解析失败，已忽略损坏数据", error)
            emptyList()
        }
    }

    override suspend fun saveReceivedCards(cards: List<ReceivedInfoCard>) = withContext(Dispatchers.IO) {
        prefs.edit(commit = true) {
            putString(RECEIVED_CARDS_KEY, gson.toJson(cards))
        }
    }

    private companion object {
        private const val TAG = "InfoCardRepository"
        private const val PREFS_NAME = "info_card_prefs"
        private const val INFO_CARD_KEY = "info_card"
        private const val RECEIVED_CARDS_KEY = "received_cards"
    }
}

