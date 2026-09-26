-- Every Media tab filters media_link on the record it is showing, and the
-- rollup filters on sets of them, so the pair needs an index.
--
-- Deliberately has no `begin transaction` / `commit`: migrate.clj/apply-one!
-- wraps every migration in one, and adding a second here would nest it.

CREATE INDEX media_link_resource_idx ON media_link (resource_type, resource_id);
