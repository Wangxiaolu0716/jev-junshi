package io.junshi.core.memory

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Consent-gated, local-only relationship memory.
 *
 * Ported from the upstream `scripts/memory_store.py` contract: the same scopes,
 * source types, confidence levels, per-scope caps and an append-only operation
 * ledger, so that "what was remembered" and "what was removed" are both
 * answerable. Storage is one JSON file with atomic replace — no database, no
 * network, and the whole directory can be deleted by the user.
 */

@Serializable
enum class MemoryScope(val cap: Int) {
    @SerialName("user") USER(30),
    @SerialName("object") OBJECT(15),
    @SerialName("relationship") RELATIONSHIP(10),
    @SerialName("event") EVENT(20),
    @SerialName("hypothesis") HYPOTHESIS(5),
}

@Serializable
enum class SourceType {
    @SerialName("user_explicit") USER_EXPLICIT,
    @SerialName("user_report") USER_REPORT,
    @SerialName("chatlab") CHATLAB,
    @SerialName("tool") TOOL,
    @SerialName("assistant_inference") ASSISTANT_INFERENCE,
}

@Serializable
enum class Confidence { @SerialName("high") HIGH, @SerialName("medium") MEDIUM, @SerialName("low") LOW }

@Serializable
data class MemoryRecord(
    val id: String,
    val scope: MemoryScope,
    val subjectId: String,
    val field: String,
    val value: String,
    val sourceType: SourceType,
    val sourceRef: String = "",
    val occurredAt: String? = null,
    val observedAt: String,
    val confidence: Confidence = Confidence.MEDIUM,
    val status: String = "active",
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class MemoryOp(
    val opId: String,
    val createdAt: String,
    val action: String,
    val beforeJson: String,
    val afterJson: String,
)

@Serializable
data class MemoryState(
    val policyVersion: Int = 1,
    val consent: Boolean = false,
    val consentAt: String? = null,
    val consentReason: String? = null,
    val records: List<MemoryRecord> = emptyList(),
    val operations: List<MemoryOp> = emptyList(),
)

/** What a caller gets back: either the new state or a typed refusal. */
data class MemoryResult(val ok: Boolean, val code: String, val message: String, val record: MemoryRecord? = null)

class JunshiMemory(private val dir: File) {

    private val file = File(dir, "memory.json")
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }
    private val lock = Any()

    companion object {
        const val POLICY_VERSION = 1
        const val MAX_VALUE_CHARS = 200
        const val MAX_RECORDS = 200
        const val MAX_OPERATIONS = 20
    }

    /** Read the current state without creating anything. */
    fun status(): MemoryState = synchronized(lock) { load() }

    /**
     * First explicit consent. Refuses without a reason: the upstream contract is
     * that memory is never switched on by implication.
     */
    fun enable(consentReason: String): MemoryResult = synchronized(lock) {
        if (consentReason.isBlank()) {
            return MemoryResult(false, "CONSENT_REQUIRED", "需要用户明确同意，并说明记住什么、用于什么")
        }
        val state = load()
        if (state.consent) {
            return MemoryResult(false, "ALREADY_ENABLED", "长期记忆已经启用；如需修改请先撤销")
        }
        val now = nowIso()
        save(state.copy(consent = true, consentAt = now, consentReason = consentReason.trim()))
        MemoryResult(true, "ENABLED", "已启用长期记忆；你可以随时查看、暂停或删除")
    }

    /** Pause writing but keep what is already stored. */
    fun pause(): MemoryResult = synchronized(lock) {
        val state = load()
        if (!state.consent) return MemoryResult(false, "NOT_ENABLED", "尚未启用长期记忆")
        save(state.copy(consent = false))
        MemoryResult(true, "PAUSED", "已暂停写入；已有记录仍然保留，可随时删除")
    }

    fun put(
        scope: MemoryScope,
        subjectId: String,
        field: String,
        value: String,
        sourceType: SourceType,
        confidence: Confidence = Confidence.MEDIUM,
        sourceRef: String = "",
        occurredAt: String? = null,
    ): MemoryResult = synchronized(lock) {
        val state = load()
        if (!state.consent) {
            return MemoryResult(false, "NOT_ENABLED", "尚未启用长期记忆，不写入任何内容")
        }
        if (subjectId.isBlank() || field.isBlank() || value.isBlank()) {
            return MemoryResult(false, "INVALID", "scope/subject/field/value 都不能为空")
        }
        if (value.length > MAX_VALUE_CHARS) {
            return MemoryResult(false, "TOO_LONG", "单条记忆最多 $MAX_VALUE_CHARS 字，请压缩后再存")
        }
        val now = nowIso()
        val existing = state.records.firstOrNull {
            it.scope == scope && it.subjectId == subjectId && it.field == field
        }
        val record = if (existing != null) {
            existing.copy(
                value = value.trim(),
                sourceType = sourceType,
                sourceRef = sourceRef,
                confidence = confidence,
                occurredAt = occurredAt,
                observedAt = now,
                updatedAt = now,
            )
        } else {
            MemoryRecord(
                id = UUID.randomUUID().toString().take(12),
                scope = scope,
                subjectId = subjectId,
                field = field,
                value = value.trim(),
                sourceType = sourceType,
                sourceRef = sourceRef,
                occurredAt = occurredAt,
                observedAt = now,
                confidence = confidence,
                createdAt = now,
                updatedAt = now,
            )
        }
        var records = state.records.filterNot { it.id == record.id } + record

        // Per-scope cap: drop the oldest record of that scope, and say so in the ledger.
        val ops = ArrayDeque(state.operations)
        val sameScope = records.filter { it.scope == scope }.sortedBy { it.updatedAt }
        if (sameScope.size > scope.cap) {
            val evicted = sameScope.take(sameScope.size - scope.cap)
            records = records.filterNot { r -> evicted.any { it.id == r.id } }
            evicted.forEach { ev ->
                ops.addLast(op("evict", json.encodeToString(MemoryRecord.serializer(), ev), "null"))
            }
        }
        if (records.size > MAX_RECORDS) {
            val overflow = records.sortedBy { it.updatedAt }.take(records.size - MAX_RECORDS)
            records = records.filterNot { r -> overflow.any { it.id == r.id } }
            overflow.forEach { ev ->
                ops.addLast(op("evict", json.encodeToString(MemoryRecord.serializer(), ev), "null"))
            }
        }
        ops.addLast(
            op(
                if (existing != null) "update" else "add",
                existing?.let { json.encodeToString(MemoryRecord.serializer(), it) } ?: "null",
                json.encodeToString(MemoryRecord.serializer(), record),
            )
        )
        save(state.copy(records = records, operations = ops.takeLast(MAX_OPERATIONS)))
        MemoryResult(true, if (existing != null) "UPDATED" else "ADDED", "已记录：${field}", record)
    }

    /** One subject's records, newest first, optionally limited by scope. */
    fun recall(subjectId: String, scopes: Set<MemoryScope>? = null, limit: Int = 20): List<MemoryRecord> =
        synchronized(lock) {
            val state = load()
            state.records
                .filter { it.subjectId == subjectId && it.status == "active" }
                .filter { scopes == null || it.scope in scopes }
                .sortedByDescending { it.updatedAt }
                .take(limit)
        }

    /** Delete one record. The ledger keeps before/after so the removal is auditable. */
    fun forget(recordId: String): MemoryResult = synchronized(lock) {
        val state = load()
        val record = state.records.firstOrNull { it.id == recordId }
            ?: return MemoryResult(false, "NOT_FOUND", "没有这条记忆：$recordId")
        val ops = state.operations + op("forget", json.encodeToString(MemoryRecord.serializer(), record), "null")
        save(state.copy(records = state.records.filterNot { it.id == recordId }, operations = ops.takeLast(MAX_OPERATIONS)))
        MemoryResult(true, "FORGOTTEN", "已删除：${record.field}")
    }

    /** Delete everything and switch memory off. */
    fun purge(): MemoryResult = synchronized(lock) {
        val state = load()
        val ops = state.operations + op("purge", "${state.records.size} records", "null")
        save(
            MemoryState(
                policyVersion = POLICY_VERSION,
                consent = false,
                records = emptyList(),
                operations = ops.takeLast(MAX_OPERATIONS),
            )
        )
        MemoryResult(true, "PURGED", "已删除全部记忆并关闭长期记忆")
    }

    fun operations(): List<MemoryOp> = synchronized(lock) { load().operations }

    /** A compact digest for a prompt: one line per field, capped. */
    fun digest(subjectId: String, limit: Int = 8): String =
        recall(subjectId, limit = limit).joinToString("\n") { "- ${it.scope.name.lowercase()}/${it.field}：${it.value}" }

    fun exportJson(): String = synchronized(lock) { json.encodeToString(MemoryState.serializer(), load()) }

    private fun op(action: String, before: String, after: String) = MemoryOp(
        opId = UUID.randomUUID().toString().take(12),
        createdAt = nowIso(),
        action = action,
        beforeJson = before,
        afterJson = after,
    )

    private fun load(): MemoryState {
        if (!file.isFile) return MemoryState(policyVersion = POLICY_VERSION)
        val text = file.readText(StandardCharsets.UTF_8)
        if (text.isBlank()) return MemoryState(policyVersion = POLICY_VERSION)
        return runCatching { json.decodeFromString(MemoryState.serializer(), text) }
            .getOrElse { MemoryState(policyVersion = POLICY_VERSION) }
    }

    private fun save(state: MemoryState) {
        if (!dir.isDirectory) dir.mkdirs()
        val tmp = File(dir, "memory.json.tmp")
        tmp.writeText(json.encodeToString(MemoryState.serializer(), state), StandardCharsets.UTF_8)
        runCatching {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }.onFailure {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun nowIso(): String = java.time.Instant.now().toString()
}
