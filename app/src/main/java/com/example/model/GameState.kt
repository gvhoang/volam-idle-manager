package com.example.model

enum class TabLifecycleState {
    IDLE,
    LOGGING_IN,
    IN_GAME,
    OFFLINE
}

data class AccountConfig(
    val tabIndex: Int,
    val username: String = "",
    val password: String = "",
    val autoLoginEnabled: Boolean = true,
    val customUrl: String = ""
)

data class CharacterStats(
    val tabIndex: Int,
    val state: TabLifecycleState = TabLifecycleState.IDLE,
    val isOnline: Boolean = false,
    val characterName: String = "Chưa mở tab",
    val sect: String = "Chưa rõ",
    val level: String = "0",
    val expPercent: String = "0%",
    val daiHongBao: String = "0",
    val hongBao: String = "0",
    val thapRecord: String = "Chưa đi",
    val bossHoangKim: String = "0",
    val daTauQuest: String = "Chưa nhận",
    val tasksCompleted: String = "",
    val currentMode: String = "Mặc định",
    val lastStatus: String = "Chờ mở tab (Idle)",
    val lastUpdateEpoch: Long = System.currentTimeMillis()
)

data class NetworkDataInfo(
    val totalBytesFormatted: String = "0.0 MB",
    val downloadSpeedFormatted: String = "0 KB/s",
    val uploadSpeedFormatted: String = "0 KB/s",
    val totalRxBytes: Long = 0L,
    val totalTxBytes: Long = 0L
)

enum class ViewLayoutMode {
    FOUR_COLUMNS,
    TWO_BY_TWO,
    SINGLE_TAB
}