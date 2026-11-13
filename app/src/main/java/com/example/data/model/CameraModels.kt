package com.example.data.model

import org.json.JSONArray
import org.json.JSONObject

enum class CameraStatus(val label: String) {
    WORKING("Funcionando normalmente"),
    OFFLINE("Fora do ar")
}

data class CameraChannelState(
    val monitorNumber: Int,
    val cameraNumber: Int,
    val status: CameraStatus = CameraStatus.WORKING
) {
    val cameraName: String
        get() = "Câmera ${cameraNumber.toString().padStart(2, '0')}"

    val isWorking: Boolean
        get() = status == CameraStatus.WORKING
}

data class MonitorSummary(
    val monitorNumber: Int,
    val total: Int,
    val working: Int,
    val offline: Int
)

data class CameraMonitoringState(
    val monitor1: List<CameraChannelState> = defaultMonitorChannels(1),
    val monitor2: List<CameraChannelState> = defaultMonitorChannels(2)
) {
    val monitor1Summary: MonitorSummary
        get() {
            val workingCount = monitor1.count { it.isWorking }
            return MonitorSummary(
                monitorNumber = 1,
                total = monitor1.size,
                working = workingCount,
                offline = monitor1.size - workingCount
            )
        }

    val monitor2Summary: MonitorSummary
        get() {
            val workingCount = monitor2.count { it.isWorking }
            return MonitorSummary(
                monitorNumber = 2,
                total = monitor2.size,
                working = workingCount,
                offline = monitor2.size - workingCount
            )
        }

    val totalCameras: Int
        get() = monitor1.size + monitor2.size

    val totalWorking: Int
        get() = monitor1Summary.working + monitor2Summary.working

    val totalOffline: Int
        get() = monitor1Summary.offline + monitor2Summary.offline

    fun withCameraStatus(monitorNumber: Int, cameraNumber: Int, status: CameraStatus): CameraMonitoringState {
        return when (monitorNumber) {
            1 -> copy(
                monitor1 = monitor1.map {
                    if (it.cameraNumber == cameraNumber) it.copy(status = status) else it
                }
            )
            2 -> copy(
                monitor2 = monitor2.map {
                    if (it.cameraNumber == cameraNumber) it.copy(status = status) else it
                }
            )
            else -> this
        }
    }

    fun toggleCamera(monitorNumber: Int, cameraNumber: Int): CameraMonitoringState {
        val current = when (monitorNumber) {
            1 -> monitor1.firstOrNull { it.cameraNumber == cameraNumber }?.status
            2 -> monitor2.firstOrNull { it.cameraNumber == cameraNumber }?.status
            else -> null
        } ?: return this
        val next = if (current == CameraStatus.WORKING) CameraStatus.OFFLINE else CameraStatus.WORKING
        return withCameraStatus(monitorNumber, cameraNumber, next)
    }

    fun withAllMonitorStatus(monitorNumber: Int, status: CameraStatus): CameraMonitoringState {
        return when (monitorNumber) {
            1 -> copy(monitor1 = monitor1.map { it.copy(status = status) })
            2 -> copy(monitor2 = monitor2.map { it.copy(status = status) })
            else -> this
        }
    }

    fun toWorkingCountText(): String {
        return if (totalOffline == 0) {
            "Todas $totalCameras funcionando"
        } else {
            "$totalWorking funcionando ($totalOffline fora do ar)"
        }
    }

    fun toMonitorDetailsText(monitorNumber: Int): String {
        val list = if (monitorNumber == 1) monitor1 else monitor2
        val offlineCameras = list.filter { !it.isWorking }.map { it.cameraNumber }
        return when {
            offlineCameras.isEmpty() -> "$monitorNumber Todas operantes (16/16)"
            offlineCameras.size == 1 -> "$monitorNumber Câmera ${offlineCameras.first()} fora do ar"
            else -> {
                val last = offlineCameras.last()
                val prefix = offlineCameras.dropLast(1).joinToString(", ")
                "$monitorNumber Câmeras $prefix e $last fora do ar"
            }
        }
    }

    fun toSnapshotJson(): String {
        val root = JSONObject()
        root.put("m1", JSONArray().apply {
            monitor1.sortedBy { it.cameraNumber }.forEach { ch ->
                put(if (ch.isWorking) 1 else 0)
            }
        })
        root.put("m2", JSONArray().apply {
            monitor2.sortedBy { it.cameraNumber }.forEach { ch ->
                put(if (ch.isWorking) 1 else 0)
            }
        })
        return root.toString()
    }

    companion object {
        const val CAMERAS_PER_MONITOR = 16

        fun defaultMonitorChannels(monitorNumber: Int): List<CameraChannelState> {
            return (1..CAMERAS_PER_MONITOR).map { num ->
                CameraChannelState(
                    monitorNumber = monitorNumber,
                    cameraNumber = num,
                    status = CameraStatus.WORKING
                )
            }
        }

        fun allWorking(): CameraMonitoringState {
            return CameraMonitoringState(
                monitor1 = defaultMonitorChannels(1),
                monitor2 = defaultMonitorChannels(2)
            )
        }

        fun defaultBpsPreset(): CameraMonitoringState {
            return allWorking()
                .withCameraStatus(1, 15, CameraStatus.OFFLINE)
                .withCameraStatus(2, 11, CameraStatus.OFFLINE)
                .withCameraStatus(2, 15, CameraStatus.OFFLINE)
                .withCameraStatus(2, 16, CameraStatus.OFFLINE)
        }

        fun fromSnapshotJson(json: String?): CameraMonitoringState? {
            if (json.isNullOrBlank()) return null
            return try {
                val root = JSONObject(json)
                val m1Array = root.optJSONArray("m1")
                val m2Array = root.optJSONArray("m2")
                if (m1Array == null || m2Array == null) return null

                val m1 = (1..CAMERAS_PER_MONITOR).map { num ->
                    val flag = if (num - 1 < m1Array.length()) m1Array.optInt(num - 1, 1) else 1
                    CameraChannelState(1, num, if (flag == 1) CameraStatus.WORKING else CameraStatus.OFFLINE)
                }
                val m2 = (1..CAMERAS_PER_MONITOR).map { num ->
                    val flag = if (num - 1 < m2Array.length()) m2Array.optInt(num - 1, 1) else 1
                    CameraChannelState(2, num, if (flag == 1) CameraStatus.WORKING else CameraStatus.OFFLINE)
                }
                CameraMonitoringState(monitor1 = m1, monitor2 = m2)
            } catch (_: Exception) {
                null
            }
        }

        fun fromRecords(records: List<ShiftCameraRecord>): CameraMonitoringState? {
            if (records.isEmpty()) return null
            val m1Map = records.filter { it.monitorNumber == 1 }.associateBy { it.cameraNumber }
            val m2Map = records.filter { it.monitorNumber == 2 }.associateBy { it.cameraNumber }
            val m1 = (1..CAMERAS_PER_MONITOR).map { num ->
                val isWorking = m1Map[num]?.isWorking ?: true
                CameraChannelState(1, num, if (isWorking) CameraStatus.WORKING else CameraStatus.OFFLINE)
            }
            val m2 = (1..CAMERAS_PER_MONITOR).map { num ->
                val isWorking = m2Map[num]?.isWorking ?: true
                CameraChannelState(2, num, if (isWorking) CameraStatus.WORKING else CameraStatus.OFFLINE)
            }
            return CameraMonitoringState(monitor1 = m1, monitor2 = m2)
        }

        fun fromLegacyStrings(monitor1Details: String, monitor2Details: String): CameraMonitoringState {
            val m1Offline = extractCameraNumbers(monitor1Details)
            val m2Offline = extractCameraNumbers(monitor2Details)
            val m1 = (1..CAMERAS_PER_MONITOR).map { num ->
                CameraChannelState(1, num, if (num in m1Offline) CameraStatus.OFFLINE else CameraStatus.WORKING)
            }
            val m2 = (1..CAMERAS_PER_MONITOR).map { num ->
                CameraChannelState(2, num, if (num in m2Offline) CameraStatus.OFFLINE else CameraStatus.WORKING)
            }
            return CameraMonitoringState(monitor1 = m1, monitor2 = m2)
        }

        private fun extractCameraNumbers(details: String): Set<Int> {
            if (!details.contains("fora do ar", ignoreCase = true)) return emptySet()
            val cleaned = details.trim()
                .removePrefix("1 ")
                .removePrefix("2 ")
            return Regex("\\b([1-9]|1[0-6])\\b")
                .findAll(cleaned)
                .mapNotNull { it.groupValues[1].toIntOrNull() }
                .filter { it in 1..CAMERAS_PER_MONITOR }
                .toSet()
        }
    }
}
