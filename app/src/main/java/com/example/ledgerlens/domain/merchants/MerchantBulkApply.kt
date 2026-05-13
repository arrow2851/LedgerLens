package com.example.ledgerlens.domain.merchants

import com.example.ledgerlens.data.AppDatabase

suspend fun applyMerchantCategoryBulk(
    database: AppDatabase,
    merchantNames: Set<String>,
    option: CategoryOption
): MerchantBulkApplyResult {
    if (merchantNames.isEmpty()) {
        return MerchantBulkApplyResult(
            merchantCount = 0,
            transactionCount = 0,
            ruleCount = 0
        )
    }

    val now = System.currentTimeMillis()
    var updatedTransactions = 0
    var savedRules = 0

    database.transactionDao()
        .getAllOnce()
        .forEach { transaction ->
            val updated = applyMerchantCategoryToTransaction(
                transaction = transaction,
                merchantNames = merchantNames,
                option = option,
                now = now
            )

            if (updated != transaction) {
                database.transactionDao().update(updated)
                updatedTransactions++
            }
        }

    merchantNames.forEach { merchantName ->
        database.transactionRuleDao().upsert(
            buildMerchantDefaultRule(
                merchantName = merchantName,
                option = option,
                now = now
            )
        )
        savedRules++
    }

    return MerchantBulkApplyResult(
        merchantCount = merchantNames.size,
        transactionCount = updatedTransactions,
        ruleCount = savedRules
    )
}
