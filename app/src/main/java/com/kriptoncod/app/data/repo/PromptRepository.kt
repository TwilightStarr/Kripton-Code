package com.kriptoncod.app.data.repo

import androidx.room.withTransaction
import com.kriptoncod.app.data.db.Assignment
import com.kriptoncod.app.data.db.AssignmentDao
import com.kriptoncod.app.data.db.KriptonDatabase
import com.kriptoncod.app.data.db.PromptItem
import com.kriptoncod.app.data.db.PromptItemDao
import com.kriptoncod.app.data.db.PromptSet
import com.kriptoncod.app.data.db.PromptSetDao
import com.kriptoncod.app.domain.AssignmentMode
import kotlinx.coroutines.flow.Flow

class PromptRepository(
    private val db: KriptonDatabase,
    private val sets: PromptSetDao = db.promptSetDao(),
    private val items: PromptItemDao = db.promptItemDao(),
    private val assignments: AssignmentDao = db.assignmentDao(),
) {
    fun observeSets(): Flow<List<PromptSet>> = sets.observeAll()
    fun observeSet(id: Long): Flow<PromptSet?> = sets.observe(id)
    fun observeItems(setId: Long): Flow<List<PromptItem>> = items.observeBySet(setId)
    fun observeAssignments(setId: Long): Flow<List<Assignment>> = assignments.observeForSet(setId)

    suspend fun getItems(setId: Long): List<PromptItem> = items.getBySet(setId)
    suspend fun getItem(id: Long): PromptItem? = items.get(id)
    suspend fun getAssignment(promptId: Long): Assignment? = assignments.get(promptId)

    suspend fun createSet(name: String): Long = sets.insert(PromptSet(name = name))

    suspend fun renameSet(id: Long, name: String) {
        sets.get(id)?.let { sets.update(it.copy(name = name)) }
    }

    /** Set silinince promptları ve atamaları da silinir (CASCADE). */
    suspend fun deleteSet(id: Long) {
        sets.deleteById(id)
    }

    /** Sona ekler. Sıra numarası ve ekleme aynı işlemde yapılır (eşzamanlı eklemede çakışma olmaz). */
    suspend fun addItem(setId: Long, title: String, body: String): Long = db.withTransaction {
        items.insert(PromptItem(setId = setId, title = title, body = body, order = items.maxPosition(setId) + 1))
    }

    suspend fun updateItem(id: Long, title: String, body: String) {
        items.get(id)?.let { items.update(it.copy(title = title, body = body)) }
    }

    suspend fun deleteItem(id: Long) {
        items.deleteById(id)
    }

    /** [orderedIds] sırasına göre 0..n-1 yeniden numaralandırır; sette olmayan id'ler yok sayılır. */
    suspend fun reorder(setId: Long, orderedIds: List<Long>) = db.withTransaction {
        val valid = items.getBySet(setId).map { it.id }.toSet()
        val ordered = orderedIds.filter { it in valid }.distinct()
        val rest = items.getBySet(setId).map { it.id }.filter { it !in ordered }
        (ordered + rest).forEachIndexed { index, id -> items.setPosition(id, index) }
    }

    suspend fun assign(promptId: Long, accountId: Long, mode: AssignmentMode) {
        assignments.upsert(Assignment(promptId, accountId, mode))
    }

    suspend fun unassign(promptId: Long) {
        assignments.deleteForPrompt(promptId)
    }
}
