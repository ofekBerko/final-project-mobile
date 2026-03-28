package cs.colman.talento.data.repository

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import cs.colman.talento.TAG
import cs.colman.talento.data.local.dao.ProfessionDao
import cs.colman.talento.data.model.Profession
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class ProfessionRepository(
    private val professionDao: ProfessionDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private val _filteredProfessions = MutableLiveData<List<Profession>>()
    val filteredProfessions: LiveData<List<Profession>> = _filteredProfessions

    suspend fun searchProfessions(query: String, limit: Int = 15) {
        withContext(Dispatchers.IO) {
            try {
                val searchQuery = if (query.isBlank()) {
                    "%"
                } else {
                    "%${query.trim()}%"
                }

                val localProfessions = if (query.isBlank()) {
                    professionDao.getProfessionsWithLimit(limit)
                } else {
                    professionDao.searchProfessionsWithLimit(searchQuery, limit)
                }

                _filteredProfessions.postValue(localProfessions)
            } catch (e: Exception) {
                Log.e(TAG, "Error searching professions: ${e.message}", e)
                _filteredProfessions.postValue(emptyList())
            }
        }
    }

    suspend fun refreshProfessions() {
        withContext(Dispatchers.IO) {
            try {
                val snapshot = firestore.collection("professions").get().await()

                val professions = snapshot.documents.mapNotNull { doc ->
                    val name = doc.getString("name") ?: return@mapNotNull null

                    Profession(
                        id = doc.id,
                        name = name
                    )
                }

                professionDao.insertProfessions(professions)
            } catch (e: Exception) {
                Log.e(TAG, "Error refreshing professions: ${e.message}", e)
            }
        }
    }
}
