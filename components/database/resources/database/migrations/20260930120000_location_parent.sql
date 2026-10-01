-- A location can sit inside another, such as a row inside an orchard. Null is
-- a top-level location, which every existing row is.
--
-- No `on delete`: the foreign key refuses to delete a location that has
-- children, and the delete path turns that into a message.
--
-- material.location_id had no index, and every rollup filters it with
-- `IN (subtree)`.
--
-- Deliberately has no `begin transaction` / `commit`: migrate.clj/apply-one!
-- wraps every migration in one, and adding a second here would nest it.

ALTER TABLE location ADD COLUMN parent_id integer references location(id);

CREATE INDEX location_parent_id_idx ON location (parent_id);
CREATE INDEX material_location_id_idx ON material (location_id);
