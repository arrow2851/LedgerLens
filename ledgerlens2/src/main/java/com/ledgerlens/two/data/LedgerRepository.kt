package com.ledgerlens.two.data

import androidx.room.withTransaction
import com.ledgerlens.two.domain.FinanceEngine
import com.ledgerlens.two.domain.Movement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.Reader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class EditRequest(
    val merchant: String?,
    val category: String?,
    val kind: String,
    val excluded: Boolean,
    val amountMinor: Long,
    val occurredAtMs: Long
)

class LedgerRepository(private val db: LedgerDatabase) {
    val transactions = db.dao().observeTransactions()
    val sources = db.dao().observeSources()
    val rules = db.dao().observeRules()
    val budgets = db.dao().observeBudgets()
    val ingestor = SmsIngestor(db)

    suspend fun rawSmsBody(smsId: Long): String? = withContext(Dispatchers.IO) {
        db.dao().smsById(smsId)?.body
    }

    suspend fun approveSource(sender: String, approved: Boolean) = withContext(Dispatchers.IO) {
        val source = db.dao().sourceBySender(sender) ?: return@withContext
        db.dao().updateSource(source.copy(approved = approved, updatedAtMs = System.currentTimeMillis()))
    }

    suspend fun edit(id: Long, request: EditRequest) = withContext(Dispatchers.IO) {
        require(request.kind in listOf(
            Movement.EXPENSE, Movement.INCOME, Movement.REFUND, Movement.REIMBURSEMENT,
            Movement.CARD_PAYMENT, Movement.TRANSFER, Movement.P2P_REVIEW,
            Movement.HOLD, Movement.SCHEDULED
        )) { "Unknown financial treatment" }
        require(request.amountMinor > 0L && request.amountMinor < 100_000_000_000L)
        require(request.occurredAtMs >= 946684800000L &&
            request.occurredAtMs <= System.currentTimeMillis() + 172800000L)
        val merchant = request.merchant?.trim()?.ifBlank { null }
        val category = request.category?.trim()?.ifBlank { null }
        val dao = db.dao()
        db.withTransaction {
            val previous = dao.transactionById(id) ?: return@withTransaction
            val newKind = request.kind
            // Inclusion is derived from confirmed treatment. A P2P remains outside spending.
            val excluded = when (newKind) {
                Movement.EXPENSE, Movement.REFUND, Movement.REIMBURSEMENT -> request.excluded
                else -> true
            }
            val changed = previous.copy(
                amountMinor = request.amountMinor, occurredAtMs = request.occurredAtMs,
                merchant = merchant, merchantKey = merchant?.let(FinanceEngine::canonicalMerchant).orEmpty(),
                category = category, kind = newKind, excluded = excluded,
                verified = true, merchantUserEdited = previous.merchantUserEdited ||
                    merchant != previous.merchant,
                categoryUserEdited = previous.categoryUserEdited || category != previous.category,
                kindUserEdited = previous.kindUserEdited || newKind != previous.kind,
                updatedAtMs = System.currentTimeMillis()
            )
            if (changed != previous) {
                dao.updateTransaction(changed)
                dao.insertAudit(TransactionAudit(
                    transactionId = previous.id, operation = "USER_CORRECTION",
                    beforeJson = auditSnapshot(previous), afterJson = auditSnapshot(changed),
                    editedAtMs = System.currentTimeMillis()
                ))
            }
        }
    }

    suspend fun duplicateDecision(id: Long, isDuplicate: Boolean) = withContext(Dispatchers.IO) {
        val current = db.dao().transactionById(id) ?: return@withContext
        db.withTransaction {
            val changed = current.copy(
                duplicateDismissed = !isDuplicate,
                excluded = if (isDuplicate) true else current.excluded,
                verified = true, updatedAtMs = System.currentTimeMillis()
            )
            db.dao().updateTransaction(changed)
            db.dao().insertAudit(TransactionAudit(
                transactionId = id, operation = "DUPLICATE_DECISION",
                beforeJson = auditSnapshot(current), afterJson = auditSnapshot(changed),
                editedAtMs = System.currentTimeMillis()
            ))
        }
    }

    /** One explicit merchant decision updates unedited history and future imports. */
    suspend fun saveMerchantDefault(merchant: String, category: String) = withContext(Dispatchers.IO) {
        require(category in FinanceEngine.categories)
        val key = FinanceEngine.canonicalMerchant(merchant)
        require(key.isNotBlank())
        db.withTransaction {
            val now = System.currentTimeMillis()
            db.dao().saveRule(MerchantRule(
                merchantKey = key, merchantDisplay = merchant.trim(),
                category = category, active = true, updatedAtMs = now
            ))
            db.dao().transactionsForMerchant(key).filter { !it.categoryUserEdited }.forEach {
                val changed = it.copy(category = category, updatedAtMs = now)
                if (changed.category != it.category) {
                    db.dao().updateTransaction(changed)
                    db.dao().insertAudit(TransactionAudit(
                        transactionId = it.id, operation = "MERCHANT_DEFAULT",
                        beforeJson = auditSnapshot(it), afterJson = auditSnapshot(changed),
                        editedAtMs = now
                    ))
                }
            }
        }
    }

    suspend fun setRuleEnabled(key: String, enabled: Boolean) = withContext(Dispatchers.IO) {
        val existing = db.dao().ruleByKey(key) ?: return@withContext
        db.dao().saveRule(existing.copy(active = enabled, updatedAtMs = System.currentTimeMillis()))
    }

    suspend fun saveBudget(month: String, category: String, currency: String, amountMinor: Long) =
        withContext(Dispatchers.IO) {
            require(Regex("""^\d{4}-\d{2}$""").matches(month))
            require(category in FinanceEngine.categories)
            require(currency in listOf("USD", "INR", "SAR", "AED"))
            require(amountMinor in 1L..100_000_000_000L)
            db.dao().saveBudget(MonthlyBudget(
                key = month + "|" + currency + "|" + category,
                month = month, category = category, currency = currency,
                limitMinor = amountMinor, updatedAtMs = System.currentTimeMillis()
            ))
        }

    suspend fun deleteEverything() = withContext(Dispatchers.IO) {
        db.withTransaction {
            db.dao().clearTransactions()
            db.dao().clearSms()
            db.dao().clearSources()
            db.dao().clearRules()
            db.dao().clearBudgets()
            db.dao().clearAudit()
        }
    }

    private fun auditSnapshot(t: LedgerTransaction): String = JSONObject().apply {
        put("amountMinor", t.amountMinor)
        put("currency", t.currency)
        put("occurredAtMs", t.occurredAtMs)
        put("merchant", t.merchant ?: JSONObject.NULL)
        put("category", t.category ?: JSONObject.NULL)
        put("kind", t.kind)
        put("excluded", t.excluded)
        put("verified", t.verified)
        put("duplicateOfId", t.duplicateOfId ?: JSONObject.NULL)
        put("duplicateDismissed", t.duplicateDismissed)
    }.toString()

    /** Spreadsheet-safe CSV export: each field is quoted; formula-like text is neutralized. */
    suspend fun exportCsv(): String = withContext(Dispatchers.IO) {
        val header = listOf("id", "sms_id", "date_utc", "sender", "merchant", "category",
            "treatment", "amount_minor", "currency", "source_approved",
            "excluded", "verified", "duplicate_of", "confidence", "reason")
        val sourceMap = db.dao().allSources().associateBy { it.sender }
        fun escape(value: String): String {
            val safe = if (value.startsWith("=") || value.startsWith("+") ||
                value.startsWith("-") || value.startsWith("@") ||
                value.startsWith("\t")) "'" + value else value
            return "\"" + safe.replace("\"", "\"\"") + "\""
        }
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        buildString {
            appendLine(header.joinToString(","))
            db.dao().allTransactions().forEach { t ->
                val fields = listOf(
                    t.id.toString(), t.smsId.toString(), formatter.format(Date(t.occurredAtMs)),
                    t.sender, t.merchant.orEmpty(), t.category.orEmpty(), t.kind,
                    t.amountMinor.toString(), t.currency,
                    (sourceMap[t.sourceKey]?.approved == true).toString(), t.excluded.toString(),
                    t.verified.toString(), t.duplicateOfId?.toString().orEmpty(),
                    t.confidence.toString(), t.reason
                )
                appendLine(fields.joinToString(",") { escape(it) })
            }
        }
    }

    /** Native v1 full-fidelity backup, including local SMS bodies and all user decisions. */
    suspend fun exportBackup(): String = withContext(Dispatchers.IO) {
        val dao = db.dao()
        fun objectOf(vararg pairs: Pair<String, Any?>): JSONObject {
            val item = JSONObject()
            pairs.forEach { (key, value) -> item.put(key, value ?: JSONObject.NULL) }
            return item
        }
        fun arrayOfObjects(values: List<JSONObject>): JSONArray =
            JSONArray().also { arr -> values.forEach { obj -> arr.put(obj) } }
        JSONObject().apply {
            put("format", "LedgerLens2")
            put("schema_version", 1)
            put("exported_at_ms", System.currentTimeMillis())
            put("sms", arrayOfObjects(dao.allSms().map { s -> objectOf(
                "smsId" to s.smsId, "sender" to s.sender, "body" to s.body,
                "receivedAtMs" to s.receivedAtMs, "sentAtMs" to s.sentAtMs,
                "insertedAtMs" to s.insertedAtMs
            ) }))
            put("sources", arrayOfObjects(dao.allSources().map { s -> objectOf(
                "sender" to s.sender, "approved" to s.approved,
                "institution" to s.institution, "updatedAtMs" to s.updatedAtMs
            ) }))
            put("transactions", arrayOfObjects(dao.allTransactions().map { t -> objectOf(
                "id" to t.id, "smsId" to t.smsId, "sourceKey" to t.sourceKey,
                "sender" to t.sender, "amountMinor" to t.amountMinor,
                "currency" to t.currency, "kind" to t.kind, "merchant" to t.merchant,
                "merchantKey" to t.merchantKey, "category" to t.category,
                "confidence" to t.confidence, "reason" to t.reason,
                "occurredAtMs" to t.occurredAtMs, "receivedAtMs" to t.receivedAtMs,
                "excluded" to t.excluded, "verified" to t.verified,
                "duplicateOfId" to t.duplicateOfId,
                "duplicateDismissed" to t.duplicateDismissed,
                "merchantUserEdited" to t.merchantUserEdited,
                "categoryUserEdited" to t.categoryUserEdited,
                "kindUserEdited" to t.kindUserEdited, "updatedAtMs" to t.updatedAtMs
            ) }))
            put("rules", arrayOfObjects(dao.allRules().map { r -> objectOf(
                "merchantKey" to r.merchantKey, "merchantDisplay" to r.merchantDisplay,
                "category" to r.category, "active" to r.active, "updatedAtMs" to r.updatedAtMs
            ) }))
            put("budgets", arrayOfObjects(dao.allBudgets().map { b -> objectOf(
                "key" to b.key, "month" to b.month, "category" to b.category,
                "currency" to b.currency, "limitMinor" to b.limitMinor,
                "updatedAtMs" to b.updatedAtMs
            ) }))
            put("audit", arrayOfObjects(dao.allAudit().map { e -> objectOf(
                "id" to e.id, "transactionId" to e.transactionId,
                "operation" to e.operation, "beforeJson" to e.beforeJson,
                "afterJson" to e.afterJson, "editedAtMs" to e.editedAtMs
            ) }))
        }.toString(2)
    }

    /**
     * Strict v1 restore: validate all objects before destructive transaction. Room rollback
     * protects the current dataset if a malformed backup fails while inserting.
     */
    suspend fun restoreBackup(reader: Reader): Int = withContext(Dispatchers.IO) {
        val root = JSONObject(reader.readText())
        require(root.optString("format") == "LedgerLens2" && root.optInt("schema_version") == 1) {
            "Not a LedgerLens2 schema-1 backup"
        }
        fun JSONObject.nullable(key: String): String? =
            if (!has(key) || isNull(key)) null else getString(key)
        fun JSONObject.nullableLong(key: String): Long? =
            if (!has(key) || isNull(key)) null else getLong(key)
        fun objects(name: String): List<JSONObject> {
            val arr = root.getJSONArray(name)
            require(arr.length() <= 200_000) { "Backup exceeds supported item limit" }
            return (0 until arr.length()).map { arr.getJSONObject(it) }
        }
        val sources = objects("sources").map { s -> FinancialSource(
            sender = s.getString("sender"), approved = s.getBoolean("approved"),
            institution = s.nullable("institution"), updatedAtMs = s.getLong("updatedAtMs")
        ) }
        val sourceKeys = sources.map { it.sender }.toSet()
        val sms = objects("sms").map { s -> StoredSms(
            smsId = s.getLong("smsId"), sender = s.getString("sender"),
            body = s.getString("body"), receivedAtMs = s.getLong("receivedAtMs"),
            sentAtMs = s.nullableLong("sentAtMs"), insertedAtMs = s.getLong("insertedAtMs")
        ) }
        val smsIds = sms.map { it.smsId }.toSet()
        val kinds = setOf(Movement.EXPENSE, Movement.INCOME, Movement.REFUND,
            Movement.REIMBURSEMENT, Movement.CARD_PAYMENT, Movement.TRANSFER, Movement.P2P_REVIEW,
            Movement.HOLD, Movement.SCHEDULED)
        val tx = objects("transactions").map { t ->
            val amount = t.getLong("amountMinor")
            val treatment = t.getString("kind")
            val key = t.getString("sourceKey")
            val smsId = t.getLong("smsId")
            require(amount > 0L && treatment in kinds && key in sourceKeys && smsId in smsIds) {
                "Invalid financial record in backup"
            }
            LedgerTransaction(
                id = t.getLong("id"), smsId = smsId, sourceKey = key,
                sender = t.getString("sender"), amountMinor = amount,
                currency = t.getString("currency"), kind = treatment,
                merchant = t.nullable("merchant"), merchantKey = t.getString("merchantKey"),
                category = t.nullable("category"), confidence = t.getDouble("confidence"),
                reason = t.getString("reason"), occurredAtMs = t.getLong("occurredAtMs"),
                receivedAtMs = t.getLong("receivedAtMs"), excluded = t.getBoolean("excluded"),
                verified = t.getBoolean("verified"), duplicateOfId = t.nullableLong("duplicateOfId"),
                duplicateDismissed = t.getBoolean("duplicateDismissed"),
                merchantUserEdited = t.getBoolean("merchantUserEdited"),
                categoryUserEdited = t.getBoolean("categoryUserEdited"),
                kindUserEdited = t.getBoolean("kindUserEdited"),
                updatedAtMs = t.getLong("updatedAtMs")
            )
        }
        require(sources.map { it.sender }.distinct().size == sources.size)
        require(sms.map { it.smsId }.distinct().size == sms.size)
        require(tx.map { it.smsId }.distinct().size == tx.size)
        val rules = objects("rules").map { r -> MerchantRule(
            merchantKey = r.getString("merchantKey"), merchantDisplay = r.getString("merchantDisplay"),
            category = r.getString("category"), active = r.getBoolean("active"),
            updatedAtMs = r.getLong("updatedAtMs")
        ) }
        val budgets = objects("budgets").map { b -> MonthlyBudget(
            key = b.getString("key"), month = b.getString("month"),
            category = b.getString("category"), currency = b.getString("currency"),
            limitMinor = b.getLong("limitMinor"), updatedAtMs = b.getLong("updatedAtMs")
        ) }
        val audit = objects("audit").map { e -> TransactionAudit(
            id = e.getLong("id"), transactionId = e.getLong("transactionId"),
            operation = e.getString("operation"), beforeJson = e.getString("beforeJson"),
            afterJson = e.getString("afterJson"), editedAtMs = e.getLong("editedAtMs")
        ) }
        val transactionIds = tx.map { it.id }.toSet()
        require(audit.all { it.transactionId in transactionIds })
        db.withTransaction {
            val dao = db.dao()
            dao.clearAudit(); dao.clearTransactions(); dao.clearSms(); dao.clearSources()
            dao.clearRules(); dao.clearBudgets()
            for (s in sources) dao.insertSource(s)
            for (s in sms) dao.insertSms(s)
            for (t in tx) require(dao.insertTransaction(t) > 0L) { "Duplicate transaction ID" }
            for (r in rules) dao.saveRule(r)
            for (b in budgets) dao.saveBudget(b)
            for (e in audit) dao.insertAudit(e)
        }
        tx.size
    }
}
