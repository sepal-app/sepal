-- activity.created_at as fixed-width UTC, '2026-09-01T15:30:00.123Z', so its
-- text order is its time order. Instant.toString wrote a varying number of
-- fraction digits, and '...:00Z' sorted after '...:00.500Z'; a row written by
-- the column default had no T and no Z at all.
--
-- strftime reads every one of those shapes and truncates to the millisecond,
-- as the code now writes. The result is still ISO-8601, so an older build's
-- Instant/parse reads every rewritten row. A value strftime cannot read is
-- left as it is.
--
-- Deliberately has no `begin transaction` / `commit`: migrate.clj/apply-one!
-- wraps every migration in one, and adding a second here would nest it.

UPDATE activity
   SET created_at = strftime('%Y-%m-%dT%H:%M:%fZ', created_at)
 WHERE strftime('%Y-%m-%dT%H:%M:%fZ', created_at) IS NOT NULL;
