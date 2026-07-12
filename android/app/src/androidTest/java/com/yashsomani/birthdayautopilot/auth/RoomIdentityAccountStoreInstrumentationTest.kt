package com.yashsomani.birthdayautopilot.auth

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.yashsomani.birthdayautopilot.storage.database.BirthdayDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomIdentityAccountStoreInstrumentationTest {
  private lateinit var database: BirthdayDatabase
  private lateinit var store: RoomIdentityAccountStore

  @Before
  fun setUp() = runBlocking {
    val context: Context = ApplicationProvider.getApplicationContext()
    database = Room.inMemoryDatabaseBuilder(context, BirthdayDatabase::class.java).build()
    database.birthdayDao().initializeIfAbsent("callback-generation")
    store = RoomIdentityAccountStore(
      dao = database.peopleSyncDao(),
      nowMillis = { 1_000 },
      localeTag = { "en-IN" },
    )
  }

  @After
  fun tearDown() {
    database.close()
  }

  @Test
  fun durableRereadDistinguishesCommittedBindingFromProvenAbsence() = runBlocking {
    val binding = NativeAccountBinding(
      googleSubject = "google-subject-1",
      email = "person@example.com",
      firebaseUid = "firebase-uid-1",
    )
    assertEquals(
      IdentityPersistenceResult.ATTACHED,
      store.attach(binding, IdentityProfile(binding.email, "Person")),
    )
    assertEquals(
      IdentityBindingPersistenceStatus.PRESENT,
      store.bindingPersistenceStatus(binding),
    )
    assertEquals(
      IdentityBindingPersistenceStatus.ABSENT,
      store.bindingPersistenceStatus(
        binding.copy(firebaseUid = "firebase-uid-2"),
      ),
    )
  }
}
