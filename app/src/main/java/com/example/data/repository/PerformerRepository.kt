package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.local.InitialPerformers
import com.example.domain.model.Performer
import com.example.domain.model.VideoLink
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

@IgnoreExtraProperties
data class FirestorePerformer(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val imageUri: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

@IgnoreExtraProperties
data class FirestoreVideoLink(
    @DocumentId val id: String = "",
    val userId: String = "",
    val performerId: String = "",
    val title: String = "",
    val url: String = "",
    val thumbnailUrl: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null,
)

class PerformerRepository(
    private val db: FirebaseFirestore,
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private val auth: FirebaseAuth
        get() = FirebaseAuth.getInstance(db.app)

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("Nutzer muss mit Google angemeldet sein.")
    }

    private fun performersCollection(userId: String) =
        db.collection("users").document(userId).collection("performers")

    private fun videoLinksCollection(userId: String) =
        db.collection("users").document(userId).collection("video_links")

    private fun catalogMetaDoc(userId: String) =
        db.collection("users").document(userId).collection("meta").document("catalog")

    fun observeAllPerformers(userId: String): Flow<List<Performer>> {
        val performersPath = "users/$userId/performers"
        val linksPath = "users/$userId/video_links"

        val performersFlow = performersCollection(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestorePerformer::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                )
            }
            .catch { error ->
                logStreamErrorIfAuthenticated(error, OperationType.LIST, performersPath, userId)
                throw error
            }

        val linksFlow = videoLinksCollection(userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreVideoLink::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                )
            }
            .catch { error ->
                logStreamErrorIfAuthenticated(error, OperationType.LIST, linksPath, userId)
                throw error
            }

        return combine(performersFlow, linksFlow) { performers, links ->
            val countByPerformer = links.groupingBy { it.performerId }.eachCount()
            performers.map { doc ->
                Performer(
                    id = doc.id,
                    name = doc.name,
                    imageUri = doc.imageUri,
                    videoCount = countByPerformer[doc.id] ?: 0,
                    createdAt = doc.createdAt?.toDate()?.time ?: 0L,
                )
            }
        }
    }

    fun observeVideoLinks(userId: String, performerId: String): Flow<List<VideoLink>> {
        val path = "users/$userId/video_links"
        return videoLinksCollection(userId)
            .whereEqualTo("performerId", performerId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    FirestoreVideoLink::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
                )
                    .map { doc ->
                        VideoLink(
                            id = doc.id,
                            performerId = doc.performerId,
                            title = doc.title,
                            url = doc.url,
                            thumbnailUrl = doc.thumbnailUrl,
                            addedAt = doc.createdAt?.toDate()?.time ?: 0L,
                        )
                    }
                    .sortedByDescending { it.addedAt }
            }
            .catch { error ->
                logStreamErrorIfAuthenticated(error, OperationType.LIST, path, userId)
                throw error
            }
    }

    suspend fun getPerformerById(
        performerId: String,
        userId: String = requireUserId(),
    ): Result<Performer?> {
        val docRef = performersCollection(userId).document(performerId)
        return try {
            val snap = docRef.get().await()
            val dto = snap.toObject(
                FirestorePerformer::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE,
            )
            Result.success(
                dto?.let {
                    Performer(
                        id = it.id,
                        name = it.name,
                        imageUri = it.imageUri,
                        videoCount = 0,
                        createdAt = it.createdAt?.toDate()?.time ?: 0L,
                    )
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun ensureSeeded() {
        val uid = requireUserId()
        val metaRef = catalogMetaDoc(uid)
        val performersCol = performersCollection(uid)

        val metaSnap = try {
            metaRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, metaRef.path)
            throw e
        }
        if (metaSnap.exists()) return

        val existingSnap = try {
            performersCol.limit(1).get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, performersCol.path)
            throw e
        }

        val batch = db.batch()
        if (existingSnap.isEmpty) {
            InitialPerformers.names.forEachIndexed { index, name ->
                val seedId = "seed_${(index + 1).toString().padStart(2, '0')}"
                val docRef = performersCol.document(seedId)
                val payload = mapOf(
                    "userId" to uid,
                    "name" to name.trim(),
                    "imageUri" to null,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
                batch.set(docRef, payload)
            }
        }
        batch.set(
            metaRef,
            mapOf(
                "userId" to uid,
                "seeded" to true,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )

        try {
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, metaRef.path)
            throw e
        }
    }

    suspend fun addPerformer(name: String): String {
        val uid = requireUserId()
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Name darf nicht leer sein" }
        require(trimmed.length <= 120) { "Name darf maximal 120 Zeichen lang sein" }

        val docRef = performersCollection(uid).document()
        val payload = mapOf(
            "userId" to uid,
            "name" to trimmed,
            "imageUri" to null,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateImageUri(id: String, imageUri: String?) {
        val uid = requireUserId()
        require(id.isNotBlank()) { "Darstellerin-ID darf nicht leer sein" }
        val normalized = imageUri?.trim()?.takeIf { it.isNotEmpty() }
        require(normalized == null || normalized.length <= 250_000) {
            "Bild-URL bzw. Bilddaten überschreiten das Sync-Limit."
        }

        val docRef = performersCollection(uid).document(id)
        try {
            docRef.update(
                mapOf(
                    "imageUri" to normalized,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun renamePerformer(id: String, newName: String) {
        val uid = requireUserId()
        require(id.isNotBlank()) { "Darstellerin-ID darf nicht leer sein" }
        val trimmed = newName.trim()
        require(trimmed.isNotEmpty()) { "Name darf nicht leer sein" }
        require(trimmed.length <= 120) { "Name darf maximal 120 Zeichen lang sein" }

        val docRef = performersCollection(uid).document(id)
        try {
            docRef.update(
                mapOf(
                    "name" to trimmed,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deletePerformer(id: String) {
        val uid = requireUserId()
        require(id.isNotBlank()) { "Darstellerin-ID darf nicht leer sein" }
        val performerRef = performersCollection(uid).document(id)
        try {
            val childLinks = videoLinksCollection(uid)
                .whereEqualTo("performerId", id)
                .get()
                .await()
            val batch = db.batch()
            childLinks.documents.forEach { linkDoc ->
                batch.delete(linkDoc.reference)
            }
            batch.delete(performerRef)
            batch.commit().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, performerRef.path)
            throw e
        }
    }

    suspend fun addVideoLink(
        performerId: String,
        title: String,
        url: String,
        thumbnailUrl: String?,
    ): String {
        val uid = requireUserId()
        require(performerId.isNotBlank()) { "Darstellerin-ID darf nicht leer sein" }
        val normalizedUrl = url.trim()
        require(normalizedUrl.isNotEmpty()) { "Video-Link darf nicht leer sein" }
        require(normalizedUrl.length <= 2_000) { "Video-URL zu lang" }

        val resolvedTitle = title.trim().ifEmpty { deriveTitleFromUrl(normalizedUrl) }.take(200)
        val normalizedThumb = thumbnailUrl?.trim()?.takeIf { it.isNotEmpty() }
        require(normalizedThumb == null || normalizedThumb.length <= 250_000) {
            "Thumbnail überschreitet das Sync-Limit."
        }

        val docRef = videoLinksCollection(uid).document()
        val payload = mapOf(
            "userId" to uid,
            "performerId" to performerId,
            "title" to resolvedTitle,
            "url" to normalizedUrl,
            "thumbnailUrl" to normalizedThumb,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        try {
            docRef.set(payload).await()
            return docRef.id
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            throw e
        }
    }

    suspend fun updateVideoLink(
        linkId: String,
        title: String,
        url: String,
        thumbnailUrl: String?,
    ) {
        val uid = requireUserId()
        require(linkId.isNotBlank()) { "Video-Link-ID darf nicht leer sein" }
        val normalizedUrl = url.trim()
        require(normalizedUrl.isNotEmpty()) { "Video-Link darf nicht leer sein" }
        require(normalizedUrl.length <= 2_000) { "Video-URL zu lang" }

        val resolvedTitle = title.trim().ifEmpty { deriveTitleFromUrl(normalizedUrl) }.take(200)
        val normalizedThumb = thumbnailUrl?.trim()?.takeIf { it.isNotEmpty() }
        require(normalizedThumb == null || normalizedThumb.length <= 250_000) {
            "Thumbnail überschreitet das Sync-Limit."
        }

        val docRef = videoLinksCollection(uid).document(linkId)
        try {
            docRef.update(
                mapOf(
                    "title" to resolvedTitle,
                    "url" to normalizedUrl,
                    "thumbnailUrl" to normalizedThumb,
                    "updatedAt" to FieldValue.serverTimestamp(),
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            throw e
        }
    }

    suspend fun deleteVideoLink(linkId: String) {
        val uid = requireUserId()
        require(linkId.isNotBlank()) { "Video-Link-ID darf nicht leer sein" }
        val docRef = videoLinksCollection(uid).document(linkId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    private fun logStreamErrorIfAuthenticated(
        error: Throwable,
        operationType: OperationType,
        path: String,
        expectedUid: String,
    ) {
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) return
        val exceptionToLog = firestoreError ?: error
        if (auth.currentUser?.uid == expectedUid) {
            handleFirestoreError(exceptionToLog, operationType, path)
        }
    }

    private fun deriveTitleFromUrl(url: String): String {
        val withoutQuery = url.substringBefore('?').substringBefore('#').trimEnd('/')
        val lastSegment = withoutQuery.substringAfterLast('/', "").trim()
        return lastSegment.ifEmpty {
            url.removePrefix("https://").removePrefix("http://").take(36)
        }
    }
}
