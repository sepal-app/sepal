-- contact.type becomes a lookup table, and gains three values.
--
-- The column was bare text while every other enum in this schema is
-- constrained, so a value the spec does not know landed silently on write and
-- only surfaced as a coercion failure on read -- a 500 on the contact page,
-- with nothing to say which row was at fault.
--
-- A lookup table rather than a CHECK, following taxon_rank: SQLite cannot
-- alter a CHECK constraint, so every future addition to the vocabulary would
-- rebuild this table again. Through a foreign key each one is an INSERT.
--
-- nursery, seed_bank and arboretum are the three added. The vocabulary came
-- from Bauble's source_type list, which a garden that buys from nurseries and
-- swaps with seed banks has already outgrown -- municipal_department was
-- added the same way.
--
-- Deliberately has no `begin transaction` / `commit`: migrate.clj/apply-one!
-- wraps every migration in one, and adding a second here would nest it.

CREATE TABLE contact_type (name text primary key) strict;

INSERT INTO contact_type (name) VALUES
  ('arboretum'), ('botanic_garden'), ('club'), ('commercial'), ('expedition'),
  ('gene_bank'), ('individual'), ('municipal_department'), ('nursery'),
  ('other'), ('research_station'), ('seed_bank'), ('staff'),
  ('university_department'), ('unknown');

CREATE TABLE contact_new (
  id integer primary key autoincrement,
  name text not null,
  email text,
  address text,
  address1 text,
  address2 text,
  city text,
  province text,
  postal_code text,
  country text,
  phone text,
  business text,
  type text references contact_type(name),
  notes text,
  created_at text not null default (datetime('now')),
  updated_at text not null default (datetime('now'))
) strict;

INSERT INTO contact_new (id, name, email, address, address1, address2, city,
                         province, postal_code, country, phone, business, type,
                         notes, created_at, updated_at)
SELECT id, name, email, address, address1, address2, city,
       province, postal_code, country, phone, business, type,
       notes, created_at, updated_at
FROM contact;

DROP TABLE contact;

ALTER TABLE contact_new RENAME TO contact;

CREATE INDEX contact_city_idx on contact (city);

CREATE TRIGGER trigger_contact_updated_at after update on contact
begin
  update contact set updated_at = datetime('now') where id = NEW.id;
end;

-- contact_fts is external-content, so the rebuild dropped its triggers with
-- the table and left its index pointing at rowids that no longer exist.
CREATE TRIGGER trigger_contact_fts_insert AFTER INSERT ON contact BEGIN
  INSERT INTO contact_fts(rowid, name, business, email)
  VALUES (new.id, new.name, COALESCE(new.business, ''), COALESCE(new.email, ''));
END;
CREATE TRIGGER trigger_contact_fts_delete AFTER DELETE ON contact BEGIN
  INSERT INTO contact_fts(contact_fts, rowid, name, business, email)
  VALUES('delete', old.id, old.name, COALESCE(old.business, ''), COALESCE(old.email, ''));
END;
CREATE TRIGGER trigger_contact_fts_update AFTER UPDATE OF name, business, email ON contact BEGIN
  INSERT INTO contact_fts(contact_fts, rowid, name, business, email)
  VALUES('delete', old.id, old.name, COALESCE(old.business, ''), COALESCE(old.email, ''));
  INSERT INTO contact_fts(rowid, name, business, email)
  VALUES (new.id, new.name, COALESCE(new.business, ''), COALESCE(new.email, ''));
END;

INSERT INTO contact_fts(contact_fts) VALUES('rebuild');

-- The guard. A bare `pragma foreign_key_check` prints violations and exits 0,
-- so it fails nothing. Feeding its row count through a CHECK does: on a
-- violation this raises, and -bail rolls the wrapping transaction back.
--
-- This is what catches a garden holding a `type` no release has ever defined:
-- the migration stops rather than nulling the column and losing what it said.
CREATE TEMP TABLE fk_guard (n integer check (n = 0));
INSERT INTO fk_guard SELECT count(*) FROM pragma_foreign_key_check;
DROP TABLE fk_guard;
