package com.localcharacter.app.device

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.localcharacter.app.domain.model.ModelDescriptor

data class DeviceCapabilities(
    val totalRamBytes: Long,
    val availableRamBytes: Long,
    val cpuCores: Int,
    val abis: List<String>,
    val androidVersion: String,
    val hardware: String,
)

enum class CompatibilityLevel { RECOMMENDED, CAUTION, HIGH_RISK }

data class ModelCompatibility(val level: CompatibilityLevel, val message: String)

class DeviceCapabilityManager(private val context: Context) {
    fun inspect(): DeviceCapabilities {
        val manager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        return DeviceCapabilities(
            totalRamBytes = memory.totalMem,
            availableRamBytes = memory.availMem,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            abis = Build.SUPPORTED_ABIS.toList(),
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            hardware = listOf(Build.MANUFACTURER, Build.MODEL, Build.HARDWARE).filter { it.isNotBlank() }.joinToString(" · "),
        )
    }

    fun compatibility(model: ModelDescriptor): ModelCompatibility {
        val device = inspect()
        val ratio = model.sizeBytes.toDouble() / device.availableRamBytes.coerceAtLeast(1)
        return when {
            ratio < 0.55 -> ModelCompatibility(CompatibilityLevel.RECOMMENDED, "Recomendado para la RAM disponible")
            ratio < 0.78 -> ModelCompatibility(CompatibilityLevel.CAUTION, "Puede consumir mucha memoria")
            else -> ModelCompatibility(CompatibilityLevel.HIGH_RISK, "Riesgo alto de memoria insuficiente")
        }
    }
}
