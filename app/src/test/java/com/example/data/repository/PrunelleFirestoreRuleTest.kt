package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.local.*
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class PrunelleFirestoreRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun saveProfileAndContacts_authenticatedOwner_succeedsAndReadsBack() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = FirestoreCloudRepository(firestore, auth)

        val saveRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.saveUserProfileAndPrivateAccount(
                displayName = "Alice Prunelle",
                username = "alice",
                bio = "Compte vérifié",
                statusText = "En ligne",
                avatarUri = null,
                birthDay = 15,
                birthMonth = 8,
                birthYear = 1996,
                birthdayPrivacy = PrivacyAudience.CONTACTS,
                timezoneId = "Europe/Paris",
                countryCode = "+33",
                phoneMasked = "+33 •••• 78",
                email = ALICE_EMAIL,
                readReceiptsEnabled = true
            )
        }
        assertTrue("Profile save failed: ${saveRes.exceptionOrNull()}", saveRes.isSuccess)

        val profile = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getUserProfile().getOrThrow() }
        assertNotNull(profile)
        assertEquals("Alice Prunelle", profile?.displayName)
        assertEquals(15L, profile?.birthDay)
        assertEquals(8L, profile?.birthMonth)

        val priv = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getPrivateAccount().getOrThrow() }
        assertEquals(1996L, priv?.birthYear)

        val tokenRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.updateFcmToken("fcm_test_token_${aliceUid.take(6)}")
        }
        assertTrue("updateFcmToken failed: ${tokenRes.exceptionOrNull()}", tokenRes.isSuccess)
        val updatedPriv = withTimeout(DEFAULT_TIMEOUT_MS) { repo.getPrivateAccount().getOrThrow() }
        assertEquals("fcm_test_token_${aliceUid.take(6)}", updatedPriv?.fcmToken)

        val contactId = "c_${UUID.randomUUID().toString().take(8)}"
        val contactRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertContact(
                ContactEntity(
                    id = contactId,
                    displayName = "Claire",
                    username = "claire",
                    birthDay = 20,
                    birthMonth = 11
                )
            )
        }
        assertTrue("Contact save failed: ${contactRes.exceptionOrNull()}", contactRes.isSuccess)

        val observed = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeContacts(aliceUid).first { list -> list.any { it.id == contactId } }
        }
        assertTrue(observed.any { it.id == contactId })
    }

    @Test
    fun upsertAndObserveMessages_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = FirestoreCloudRepository(firestore, auth)
        val msgId = "msg_${UUID.randomUUID().toString().take(8)}"

        val res = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.upsertMessage(
                MessageEntity(
                    id = msgId,
                    conversationId = "dm_alice_test",
                    senderId = aliceUid,
                    senderName = "Alice",
                    content = "Message synchronisé Firestore",
                    type = MessageType.TEXT,
                    readStatus = ReadStatus.SENT,
                    sentAt = 1_700_000_000L
                )
            )
        }
        assertTrue("Message upsert failed: ${res.exceptionOrNull()}", res.isSuccess)

        val msgs = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeMessages(aliceUid).first { list -> list.any { it.id == msgId } }
        }
        assertEquals("Message synchronisé Firestore", msgs.first { it.id == msgId }.content)
    }

    @Test
    fun crossUserReadMessage_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FirestoreCloudRepository(firestore, auth)
        val msgId = "msg_${UUID.randomUUID().toString().take(8)}"

        withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.upsertMessage(
                MessageEntity(
                    id = msgId,
                    conversationId = "dm_private",
                    senderId = aliceUid,
                    senderName = "Alice",
                    content = "Secret",
                    readStatus = ReadStatus.SENT,
                    sentAt = 1_700_000_000L
                )
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val caught = try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    firestore.collection("users").document(aliceUid)
                        .collection("messages").document(msgId).get().await()
                }
            }
            null
        } catch (e: Exception) {
            e.unwrapFirestoreException()
        }
        assertNotNull("Expected PERMISSION_DENIED for cross-user read", caught)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, caught?.code)
    }

    @Test
    fun observeMessages_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = FirestoreCloudRepository(firestore, auth)
        val ex = try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeMessages("alice_unauth_check").first()
            }
            null
        } catch (e: Exception) {
            e
        }
        assertNotNull("Expected PERMISSION_DENIED exception", ex)
        val fsEx = checkNotNull(ex?.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $ex"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_prunelle@test.com"
        const val BOB_EMAIL = "bob_prunelle@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}
