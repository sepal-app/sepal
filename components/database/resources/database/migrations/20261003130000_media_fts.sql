-- Search over media: its title, its description, and a label for the
-- record it is linked to, so typing an accession code finds the photos of
-- that accession and of its material.
--
-- media_fts keeps its own copy of the text rather than reading media's
-- columns, because the label comes from other tables. media_fts_source is
-- the one definition of what the index holds; every trigger refreshes the
-- rows it affects by deleting them and reinserting from the view.
--
-- A material's label is its accession's code and its own, as two words.
-- The tokenizer splits on punctuation, so the full code typed with any
-- punctuation separator matches it, and the material separator setting can
-- change without leaving labels stale.
--
-- Deleting a linked record needs no trigger here: the delete path removes
-- its media_link rows, and the media_link delete trigger refreshes them.
--
-- Deliberately has no `begin transaction` / `commit`: migrate.clj/apply-one!
-- wraps every migration in one, and adding a second here would nest it.

CREATE VIEW media_fts_source AS
SELECT md.id,
       md.title,
       md.description,
       CASE ml.resource_type
         WHEN 'accession' THEN a.code
         WHEN 'material' THEN ma.code || ' ' || m.code
         WHEN 'taxon' THEN t.name
         WHEN 'location' THEN l.code || ' ' || l.name
       END AS linked
FROM media md
LEFT JOIN media_link ml ON ml.media_id = md.id
LEFT JOIN accession a ON ml.resource_type = 'accession' AND a.id = ml.resource_id
LEFT JOIN material m ON ml.resource_type = 'material' AND m.id = ml.resource_id
LEFT JOIN accession ma ON ma.id = m.accession_id
LEFT JOIN taxon t ON ml.resource_type = 'taxon' AND t.id = ml.resource_id
LEFT JOIN location l ON ml.resource_type = 'location' AND l.id = ml.resource_id;

CREATE VIRTUAL TABLE media_fts USING fts5(title, description, linked);

INSERT INTO media_fts(rowid, title, description, linked)
  SELECT id, title, description, linked FROM media_fts_source;

CREATE TRIGGER trigger_media_fts_insert AFTER INSERT ON media BEGIN
  INSERT INTO media_fts(rowid, title, description, linked)
    SELECT id, title, description, linked FROM media_fts_source WHERE id = new.id;
END;

CREATE TRIGGER trigger_media_fts_update AFTER UPDATE OF title, description ON media BEGIN
  DELETE FROM media_fts WHERE rowid = new.id;
  INSERT INTO media_fts(rowid, title, description, linked)
    SELECT id, title, description, linked FROM media_fts_source WHERE id = new.id;
END;

CREATE TRIGGER trigger_media_fts_delete AFTER DELETE ON media BEGIN
  DELETE FROM media_fts WHERE rowid = old.id;
END;

CREATE TRIGGER trigger_media_link_media_fts_insert AFTER INSERT ON media_link BEGIN
  DELETE FROM media_fts WHERE rowid = new.media_id;
  INSERT INTO media_fts(rowid, title, description, linked)
    SELECT id, title, description, linked FROM media_fts_source WHERE id = new.media_id;
END;

CREATE TRIGGER trigger_media_link_media_fts_update AFTER UPDATE ON media_link BEGIN
  DELETE FROM media_fts WHERE rowid IN (old.media_id, new.media_id);
  INSERT INTO media_fts(rowid, title, description, linked)
    SELECT id, title, description, linked FROM media_fts_source
    WHERE id IN (old.media_id, new.media_id);
END;

CREATE TRIGGER trigger_media_link_media_fts_delete AFTER DELETE ON media_link BEGIN
  DELETE FROM media_fts WHERE rowid = old.media_id;
  INSERT INTO media_fts(rowid, title, description, linked)
    SELECT id, title, description, linked FROM media_fts_source WHERE id = old.media_id;
END;

CREATE TRIGGER trigger_accession_media_fts_update AFTER UPDATE OF code ON accession BEGIN
  DELETE FROM media_fts WHERE rowid IN (
    SELECT media_id FROM media_link
    WHERE (resource_type = 'accession' AND resource_id = new.id)
       OR (resource_type = 'material'
           AND resource_id IN (SELECT id FROM material WHERE accession_id = new.id)));
  INSERT INTO media_fts(rowid, title, description, linked)
    SELECT id, title, description, linked FROM media_fts_source
    WHERE id IN (
      SELECT media_id FROM media_link
      WHERE (resource_type = 'accession' AND resource_id = new.id)
         OR (resource_type = 'material'
             AND resource_id IN (SELECT id FROM material WHERE accession_id = new.id)));
END;

CREATE TRIGGER trigger_material_media_fts_update AFTER UPDATE OF code, accession_id ON material BEGIN
  DELETE FROM media_fts WHERE rowid IN (
    SELECT media_id FROM media_link WHERE resource_type = 'material' AND resource_id = new.id);
  INSERT INTO media_fts(rowid, title, description, linked)
    SELECT id, title, description, linked FROM media_fts_source
    WHERE id IN (
      SELECT media_id FROM media_link WHERE resource_type = 'material' AND resource_id = new.id);
END;

CREATE TRIGGER trigger_taxon_media_fts_update AFTER UPDATE OF name ON taxon BEGIN
  DELETE FROM media_fts WHERE rowid IN (
    SELECT media_id FROM media_link WHERE resource_type = 'taxon' AND resource_id = new.id);
  INSERT INTO media_fts(rowid, title, description, linked)
    SELECT id, title, description, linked FROM media_fts_source
    WHERE id IN (
      SELECT media_id FROM media_link WHERE resource_type = 'taxon' AND resource_id = new.id);
END;

CREATE TRIGGER trigger_location_media_fts_update AFTER UPDATE OF code, name ON location BEGIN
  DELETE FROM media_fts WHERE rowid IN (
    SELECT media_id FROM media_link WHERE resource_type = 'location' AND resource_id = new.id);
  INSERT INTO media_fts(rowid, title, description, linked)
    SELECT id, title, description, linked FROM media_fts_source
    WHERE id IN (
      SELECT media_id FROM media_link WHERE resource_type = 'location' AND resource_id = new.id);
END;
