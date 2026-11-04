package com.marcorable.ccrecorder.core.data.repository

import com.marcorable.ccrecorder.core.data.PartyRepository
import com.marcorable.ccrecorder.core.database.dao.PartyDao
import com.marcorable.ccrecorder.core.model.Party
import com.marcorable.ccrecorder.core.model.PersistenceError
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DefaultPartyRepository @Inject constructor(
    private val parties: PartyDao,
) : PartyRepository {
    override fun observeParties(): Flow<List<Party>> = parties.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getParty(id: String): Party =
        parties.get(id)?.toDomain() ?: throw PersistenceError.NotFound("Party", id)

    override suspend fun createParty(party: Party) {
        validate(party)
        parties.insert(party.toEntity())
    }

    override suspend fun updateParty(party: Party) {
        validate(party)
        if (parties.update(party.toEntity()) == 0) throw PersistenceError.NotFound("Party", party.id)
    }

    override suspend fun deleteParty(id: String) {
        if (parties.delete(id) == 0) throw PersistenceError.NotFound("Party", id)
    }

    private fun validate(party: Party) {
        if (runCatching { UUID.fromString(party.id).toString() }.getOrNull() != party.id) {
            throw PersistenceError.InvalidData("Invalid UUID: ${party.id}")
        }
    }
}
