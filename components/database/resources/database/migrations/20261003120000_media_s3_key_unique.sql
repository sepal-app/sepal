-- One media item per stored object. Deleting a media item deletes its object,
-- so a second item on the same object would be left pointing at nothing.
--
-- Creating the index fails on a database that already has two items on one
-- object. Find them with:
--
--   select s3_bucket, s3_key from media
--   group by s3_bucket, s3_key having count(*) > 1;
--
-- Deliberately has no `begin transaction` / `commit`: migrate.clj/apply-one!
-- wraps every migration in one, and adding a second here would nest it.

CREATE UNIQUE INDEX media_s3_bucket_s3_key_idx ON media (s3_bucket, s3_key);
