package com.localcharacter.app.llm.provider

import com.localcharacter.app.data.repository.AiUsageRepository
import com.localcharacter.app.data.settings.SettingsRepository
import java.util.Calendar
import java.util.UUID
import kotlinx.coroutines.flow.first

class ApiUsageTracker(
    private val repository: AiUsageRepository,
    private val settings: SettingsRepository,
) {
    suspend fun record(
        selection: ProviderModelSelection,
        model: LlmModelInfo?,
        usage: TokenUsage?,
        conversationId: String,
        characterId: String,
        timeToFirstTokenMillis: Long?,
        generationDurationMillis: Long,
    ) {
        val cost = if (usage != null && model != null) CostCalculator.estimateUsd(usage, model) else if (selection.providerId == LOCAL_PROVIDER_ID) 0.0 else null
        repository.save(
            AiUsageRecord(
                id = UUID.randomUUID().toString(),
                providerId = selection.providerId,
                modelId = selection.modelId,
                inputTokens = usage?.inputTokens,
                outputTokens = usage?.outputTokens,
                estimatedCostUsd = cost,
                timestamp = System.currentTimeMillis(),
                conversationId = conversationId,
                characterId = characterId,
                timeToFirstTokenMillis = timeToFirstTokenMillis,
                generationDurationMillis = generationDurationMillis,
            ),
        )
    }

    suspend fun monthlySpendUsd(): Double = repository.estimatedCostSince(monthStart())

    suspend fun budgetBlock(model: LlmModelInfo?): ProviderError? {
        if (model?.pricingType != PricingType.PAID) return null
        val budget = settings.aiProviderSettings.first().budget
        val limit = budget.monthlyBudgetUsd ?: return null
        if (!budget.blockAtLimit || monthlySpendUsd() < limit) return null
        return ProviderError(
            ProviderErrorKind.BILLING,
            "Se alcanzó el presupuesto mensual configurado. Las APIs de pago están bloqueadas.",
        )
    }

    private fun monthStart(): Long = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

