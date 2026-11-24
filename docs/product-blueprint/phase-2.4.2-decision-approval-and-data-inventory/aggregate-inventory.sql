-- AI-Birthday Phase 2.4.2 aggregate-only local data inventory
-- READ ONLY. Run only against an authorized, restorable COPY of ai_birthday.
-- Expected schema version: 5. Stop if PRAGMA user_version is not 5.
-- Output contains aggregate counts/schema metadata only. Do not add SELECTs
-- that emit IDs, names, birthdays, phone/email, notes, facts or draft bodies.

PRAGMA query_only = ON;

-- 0. Confirm expected schema. Stop manually unless this returns 5.
PRAGMA user_version;

-- 1. Table row totals.
SELECT 'persons' AS table_name, COUNT(*) AS row_count FROM persons
UNION ALL SELECT 'birthdays', COUNT(*) FROM birthdays
UNION ALL SELECT 'message_drafts', COUNT(*) FROM message_drafts
UNION ALL SELECT 'reminder_settings_entries', COUNT(*) FROM reminder_settings_entries
UNION ALL SELECT 'delivery_events', COUNT(*) FROM delivery_events
UNION ALL SELECT 'jobs', COUNT(*) FROM jobs;

-- 2. Person birthday completeness, validity, and active/deleted totals.
SELECT
  COUNT(*) AS persons_total,
  COALESCE(SUM(CASE WHEN deleted_at IS NULL THEN 1 ELSE 0 END), 0) AS persons_active,
  COALESCE(SUM(CASE WHEN deleted_at IS NOT NULL THEN 1 ELSE 0 END), 0) AS persons_tombstoned,
  COALESCE(SUM(CASE WHEN birthday_month IS NULL AND birthday_day IS NULL THEN 1 ELSE 0 END), 0) AS birthday_both_null,
  COALESCE(SUM(CASE WHEN (birthday_month IS NULL) <> (birthday_day IS NULL) THEN 1 ELSE 0 END), 0) AS birthday_partial_pair,
  COALESCE(SUM(CASE
    WHEN birthday_month IS NOT NULL AND birthday_day IS NOT NULL
     AND NOT (
       birthday_month BETWEEN 1 AND 12
       AND birthday_day BETWEEN 1 AND CASE
         WHEN birthday_month IN (1,3,5,7,8,10,12) THEN 31
         WHEN birthday_month IN (4,6,9,11) THEN 30
         WHEN birthday_month = 2 THEN 29
         ELSE 0
       END
     ) THEN 1 ELSE 0 END
  ), 0) AS birthday_invalid_pair,
  COALESCE(SUM(CASE WHEN birthday_month IS NOT NULL AND birthday_day IS NOT NULL
            AND birthday_month BETWEEN 1 AND 12
            AND birthday_day BETWEEN 1 AND CASE
              WHEN birthday_month IN (1,3,5,7,8,10,12) THEN 31
              WHEN birthday_month IN (4,6,9,11) THEN 30
              WHEN birthday_month = 2 THEN 29
              ELSE 0
            END THEN 1 ELSE 0 END
  ), 0) AS birthday_valid_pair,
  COALESCE(SUM(CASE WHEN birthday_month = 2 AND birthday_day = 29 THEN 1 ELSE 0 END), 0) AS feb29_persons
FROM persons;

-- 3. autoPrepare is a stored boolean. This does not reveal user intent/provenance.
SELECT
  COALESCE(SUM(CASE WHEN auto_prepare = 1 THEN 1 ELSE 0 END), 0) AS auto_prepare_true,
  COALESCE(SUM(CASE WHEN auto_prepare = 0 THEN 1 ELSE 0 END), 0) AS auto_prepare_false,
  COALESCE(SUM(CASE WHEN auto_prepare IS NULL THEN 1 ELSE 0 END), 0) AS auto_prepare_null
FROM persons;

-- 4. Recipient timezone shape only. IANA validity requires an external timezone
-- database/library check; this query intentionally emits no timezone strings.
SELECT
  COALESCE(SUM(CASE WHEN timezone IS NULL THEN 1 ELSE 0 END), 0) AS timezone_null,
  COALESCE(SUM(CASE WHEN timezone IS NOT NULL AND TRIM(timezone) = '' THEN 1 ELSE 0 END), 0) AS timezone_empty,
  COALESCE(SUM(CASE WHEN timezone IS NOT NULL AND TRIM(timezone) <> '' THEN 1 ELSE 0 END), 0) AS timezone_nonempty_to_validate
FROM persons;

-- 5. Phone format buckets only. These are shape buckets, not validity assertions.
SELECT
  COALESCE(SUM(CASE WHEN phone_number IS NULL OR TRIM(phone_number) = '' THEN 1 ELSE 0 END), 0) AS phone_missing,
  COALESCE(SUM(CASE WHEN phone_number LIKE '+%' THEN 1 ELSE 0 END), 0) AS phone_plus_prefix,
  COALESCE(SUM(CASE WHEN phone_number IS NOT NULL AND TRIM(phone_number) <> ''
            AND phone_number NOT LIKE '+%' THEN 1 ELSE 0 END), 0) AS phone_nonempty_without_plus
FROM persons;

-- 6. Birthday duplicate groups and excess rows.
SELECT COUNT(*) AS duplicate_person_cycle_groups
FROM (
  SELECT person_id, cycle_year FROM birthdays
  GROUP BY person_id, cycle_year HAVING COUNT(*) > 1
);
SELECT COALESCE(SUM(n - 1), 0) AS duplicate_person_cycle_excess_rows
FROM (
  SELECT COUNT(*) AS n FROM birthdays
  GROUP BY person_id, cycle_year HAVING COUNT(*) > 1
);
SELECT COUNT(*) AS persons_with_multiple_birthday_rows
FROM (
  SELECT person_id FROM birthdays
  GROUP BY person_id HAVING COUNT(*) > 1
);

-- 7. Birthday references to missing or tombstoned Persons.
SELECT
  COALESCE(SUM(CASE WHEN p.id IS NULL THEN 1 ELSE 0 END), 0) AS birthday_rows_with_missing_person,
  COALESCE(SUM(CASE WHEN p.id IS NOT NULL AND p.deleted_at IS NOT NULL THEN 1 ELSE 0 END), 0) AS birthday_rows_for_tombstoned_person
FROM birthdays b LEFT JOIN persons p ON p.id = b.person_id;

-- 8. Message draft linkage integrity.
SELECT
  COUNT(*) AS message_drafts_total,
  COALESCE(SUM(CASE WHEN b.id IS NULL THEN 1 ELSE 0 END), 0) AS drafts_with_missing_birthday,
  COALESCE(SUM(CASE WHEN p.id IS NULL THEN 1 ELSE 0 END), 0) AS drafts_with_missing_person,
  COALESCE(SUM(CASE WHEN b.id IS NOT NULL AND b.person_id <> d.person_id THEN 1 ELSE 0 END), 0) AS drafts_person_birthday_mismatch
FROM message_drafts d
LEFT JOIN birthdays b ON b.id = d.birthday_id
LEFT JOIN persons p ON p.id = d.person_id;

-- 9. Active Birthday.draftId reference integrity.
SELECT
  COUNT(*) AS birthdays_total,
  COALESCE(SUM(CASE WHEN b.draft_id IS NOT NULL AND d.id IS NULL THEN 1 ELSE 0 END), 0) AS birthday_rows_with_missing_active_draft
FROM birthdays b LEFT JOIN message_drafts d ON d.id = b.draft_id;

-- 10. Delivery evidence with missing Birthday references.
SELECT
  COUNT(*) AS delivery_events_total,
  COALESCE(SUM(CASE WHEN b.id IS NULL THEN 1 ELSE 0 END), 0) AS delivery_events_with_missing_birthday
FROM delivery_events e LEFT JOIN birthdays b ON b.id = e.birthday_id;

-- 11. AI-generation jobs whose subject no longer resolves to a Birthday.
-- Other job types use different subjectId semantics and are not joined here.
SELECT
  COUNT(*) AS ai_generate_jobs_total,
  COALESCE(SUM(CASE WHEN b.id IS NULL THEN 1 ELSE 0 END), 0) AS ai_generate_jobs_with_missing_birthday
FROM jobs j LEFT JOIN birthdays b ON b.id = j.subject_id
WHERE j.type = 'ai_generate';

-- 12. Reminder settings row counts; actual scheduled alarms are OS state and
-- are not stored as one row per reminder in this database.
SELECT
  COUNT(*) AS reminder_settings_rows,
  COALESCE(SUM(CASE WHEN enabled = 1 THEN 1 ELSE 0 END), 0) AS reminder_settings_enabled
FROM reminder_settings_entries;

-- 13. Schema constraints/index metadata; no user row values are emitted.
PRAGMA foreign_key_list('birthdays');
PRAGMA index_list('birthdays');
PRAGMA foreign_key_list('message_drafts');
PRAGMA foreign_key_list('delivery_events');
