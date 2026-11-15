package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class PerformerRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureSeeded_authenticatedOwner_creates50InitialPerformers() = runBlocking {
        val uid = signInTestUser("seed_${UUID.randomUUID()}@test.com")
        val repository = PerformerRepository(firestore)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.ensureSeeded()
        }

        val performers = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeAllPerformers(uid).first { it.size == 50 }
        }
        assertEquals(50, performers.size)
        assertTrue(performers.any { it.name == "Emily Willis" })
    }

    @Test
    fun addPerformerAndVideoLinks_authenticatedOwner_updatesCountsAndDeletesCleanly() = runBlocking {
        val uid = signInTestUser("crud_${UUID.randomUUID()}@test.com")
        val repository = PerformerRepository(firestore)

        val performerId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addPerformer("Test Star ${UUID.randomUUID().toString().take(6)}")
        }
        assertTrue(performerId.isNotBlank())

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateImageUri(performerId, "https://example.com/poster.jpg")
            repository.renamePerformer(performerId, "Renamed Star")
        }

        val linkId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.addVideoLink(
                performerId = performerId,
                title = "Sample Scene",
                url = "https://example.com/watch/1",
                thumbnailUrl = "https://example.com/thumb.jpg",
            )
        }
        assertTrue(linkId.isNotBlank())

        val performers = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeAllPerformers(uid).first { list ->
                list.any { it.id == performerId && it.name == "Renamed Star" && it.videoCount == 1 }
            }
        }
        val created = performers.first { it.id == performerId }
        assertEquals("https://example.com/poster.jpg", created.imageUri)

        val links = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeVideoLinks(uid, performerId).first { it.any { l -> l.id == linkId } }
        }
        assertEquals("Sample Scene", links.first { it.id == linkId }.title)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.updateVideoLinkMetadata(
                linkId = linkId,
                title = "Updated Metadata Title",
                thumbnailUrl = "https://example.com/updated-thumb.jpg",
            )
        }

        val allLinks = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeAllVideoLinks(uid).first { list ->
                list.any {
                    it.id == linkId &&
                        it.title == "Updated Metadata Title" &&
                        it.thumbnailUrl == "https://example.com/updated-thumb.jpg"
                }
            }
        }
        assertTrue(allLinks.any { it.id == linkId })

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.deletePerformer(performerId)
        }

        val afterDelete = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeAllPerformers(uid).first { list -> list.none { it.id == performerId } }
        }
        assertFalse(afterDelete.any { it.id == performerId })
    }

    @Test
    fun getPerformerById_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("alice_${UUID.randomUUID()}@test.com")
        val aliceRepo = PerformerRepository(firestore)
        val performerId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.addPerformer("Alice Only")
        }

        signInTestUser("bob_${UUID.randomUUID()}@test.com")
        val bobRepo = PerformerRepository(firestore)
        val result = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.getPerformerById(performerId = performerId, userId = aliceUid)
        }
        assertTrue(result.isFailure)
        val firestoreException = result.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(firestoreException)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
    }

    @Test
    fun observeAllPerformers_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser("unauth_${UUID.randomUUID()}@test.com")
        auth.signOut()
        val repository = PerformerRepository(firestore)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeAllPerformers(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED when signed out")
        } catch (e: Exception) {
            val firestoreException = e.unwrapFirestoreException()
            assertNotNull("Expected nested FirebaseFirestoreException, got $e", firestoreException)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException?.code)
        }
    }

    private companion object {
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
