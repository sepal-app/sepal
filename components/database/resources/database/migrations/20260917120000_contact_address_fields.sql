-- A postal address in three fields instead of one box.
--
-- province, postal_code and country each have a column already, which left the
-- single `address` holding street, unit and city together — and city is
-- precisely the part a garden wants to sort a list on, filter by, and read off
-- a table column.
--
-- Additive. `address` stays, because a one-line address cannot be split into
-- street and city by any rule that is right on real data: "12 High Street,
-- Newport" and "Apt 4, 12 High St" need different ones, and a parser that
-- guesses wrong does it silently. Existing values stay where they are and the
-- form shows them read-only until a contact is given the new fields.

ALTER TABLE contact ADD COLUMN address1 text;
ALTER TABLE contact ADD COLUMN address2 text;
ALTER TABLE contact ADD COLUMN city text;

-- City is the one that gets filtered on.
CREATE INDEX contact_city_idx on contact (city);
