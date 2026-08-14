-- Additional FTS tables for accession, location, and contact
-- These enable fast full-text search with prefix matching

--------------------------------------------------------------------------------
-- Accession FTS
-- Searchable: code
--------------------------------------------------------------------------------

CREATE VIRTUAL TABLE accession_fts USING fts5(
  code,
  content='accession',
  content_rowid='id'
);

-- Populate from existing data
INSERT INTO accession_fts(rowid, code)
SELECT id, code FROM accession;

-- Keep in sync with triggers
CREATE TRIGGER trigger_accession_fts_insert AFTER INSERT ON accession BEGIN
  INSERT INTO accession_fts(rowid, code) VALUES (new.id, new.code);
END;

CREATE TRIGGER trigger_accession_fts_delete AFTER DELETE ON accession BEGIN
  INSERT INTO accession_fts(accession_fts, rowid, code) VALUES('delete', old.id, old.code);
END;

CREATE TRIGGER trigger_accession_fts_update AFTER UPDATE OF code ON accession BEGIN
  INSERT INTO accession_fts(accession_fts, rowid, code) VALUES('delete', old.id, old.code);
  INSERT INTO accession_fts(rowid, code) VALUES (new.id, new.code);
END;

--------------------------------------------------------------------------------
-- Location FTS
-- Searchable: code, name, description
--------------------------------------------------------------------------------

CREATE VIRTUAL TABLE location_fts USING fts5(
  code,
  name,
  description,
  content='location',
  content_rowid='id'
);

-- Populate from existing data
INSERT INTO location_fts(rowid, code, name, description)
SELECT id, code, name, COALESCE(description, '') FROM location;

-- Keep in sync with triggers
CREATE TRIGGER trigger_location_fts_insert AFTER INSERT ON location BEGIN
  INSERT INTO location_fts(rowid, code, name, description)
  VALUES (new.id, new.code, new.name, COALESCE(new.description, ''));
END;

CREATE TRIGGER trigger_location_fts_delete AFTER DELETE ON location BEGIN
  INSERT INTO location_fts(location_fts, rowid, code, name, description)
  VALUES('delete', old.id, old.code, old.name, COALESCE(old.description, ''));
END;

CREATE TRIGGER trigger_location_fts_update AFTER UPDATE OF code, name, description ON location BEGIN
  INSERT INTO location_fts(location_fts, rowid, code, name, description)
  VALUES('delete', old.id, old.code, old.name, COALESCE(old.description, ''));
  INSERT INTO location_fts(rowid, code, name, description)
  VALUES (new.id, new.code, new.name, COALESCE(new.description, ''));
END;

--------------------------------------------------------------------------------
-- Contact FTS
-- Searchable: name, business, email
--------------------------------------------------------------------------------

CREATE VIRTUAL TABLE contact_fts USING fts5(
  name,
  business,
  email,
  content='contact',
  content_rowid='id'
);

-- Populate from existing data
INSERT INTO contact_fts(rowid, name, business, email)
SELECT id, name, COALESCE(business, ''), COALESCE(email, '') FROM contact;

-- Keep in sync with triggers
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
