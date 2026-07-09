package com.example.core.backup

import com.example.core.db.AppDatabase

internal suspend fun AppDatabase.captureBackupRecordSnapshot(
    preferences: BackupPreferencesDto,
): BackupRecordSnapshotDto {
    return BackupRecordSnapshotDto(
        contacts = contactDao().getAllSync().map { it.toBackupDto() },
        events = eventDao().getAllSync().map { it.toBackupDto() },
        pendingMessages = pendingMessageDao().getAllSync().map { it.toBackupDto() },
        sentMessages = sentMessageDao().getAllSync().map { it.toBackupDto() },
        styleProfile = styleProfileDao().get()?.toBackupDto(),
        memoryNotes = memoryNoteDao().getAllSync().map { it.toBackupDto() },
        giftHistory = giftHistoryDao().getAllSync().map { it.toBackupDto() },
        activityLogs = activityLogDao().getAllSync().map { it.toBackupDto() },
        messageFeedback = messageFeedbackDao().getAllSync().map { it.toBackupDto() },
        dispatchAttempts = dispatchAttemptDao().getAllSync().map { it.toBackupDto() },
        preferences = preferences,
    )
}

internal suspend fun AppDatabase.replaceWithBackupPayload(backup: BackupPayloadDto): Int {
    var restored = 0
    deleteExistingRestorableData()
    backup.contacts.forEach {
        contactDao().upsert(it.toEntity())
        restored++
    }
    backup.events.forEach {
        eventDao().upsert(it.toEntity())
        restored++
    }
    backup.pendingMessages.forEach {
        pendingMessageDao().insert(it.toEntity())
        restored++
    }
    backup.sentMessages.forEach {
        sentMessageDao().insert(it.toEntity())
        restored++
    }
    backup.styleProfile?.let {
        styleProfileDao().upsert(it.toEntity())
        restored++
    }
    backup.memoryNotes.forEach {
        memoryNoteDao().upsert(it.toEntity())
        restored++
    }
    backup.giftHistory.forEach {
        giftHistoryDao().upsert(it.toEntity())
        restored++
    }
    backup.activityLogs.forEach {
        activityLogDao().insert(it.toEntity())
        restored++
    }
    backup.messageFeedback.forEach {
        messageFeedbackDao().insert(it.toEntity())
        restored++
    }
    backup.dispatchAttempts.forEach {
        dispatchAttemptDao().upsert(it.toEntity())
        restored++
    }
    return restored
}

private suspend fun AppDatabase.deleteExistingRestorableData() {
    diagnosticSnapshotDao().deleteAll()
    dispatchAttemptDao().deleteAll()
    messageFeedbackDao().deleteAll()
    activityLogDao().deleteAll()
    pendingMessageDao().deleteAll()
    sentMessageDao().deleteAll()
    eventDao().deleteAll()
    memoryNoteDao().deleteAll()
    giftHistoryDao().deleteAll()
    styleProfileDao().deleteAllHistory()
    styleProfileDao().deleteAll()
    contactDao().deleteAll()
}
