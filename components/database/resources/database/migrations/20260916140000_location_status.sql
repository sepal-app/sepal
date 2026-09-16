-- Retiring a location that has a past.
--
-- material_change names a location as the source or the destination of every
-- move, so a location that has ever held material can never be deleted:
-- removing it would leave the move log saying a plant came from nowhere. That
-- is permanent, and no action a curator can take clears it, so without this a
-- bed that is gone from the garden stays in every picker for good.
--
-- Archiving keeps the row, and with it the name the history reads back, while
-- taking the location out of the pickers so nothing new is filed there. The
-- shape is "user".status, which says the same thing about a person.

ALTER TABLE location ADD COLUMN status text not null default 'active'
  check(status in ('active', 'archived'));

-- The lists ask for active locations by default, which is every row today and
-- most rows always.
CREATE INDEX location_status_idx on location (status);
