-- An accession code is unique in the garden, and a material code is unique
-- within its accession. Nothing enforced either before, so two people creating
-- an accession in the same minute could both read the same next number and
-- both save it.
--
-- Material's index is per accession, matching where its sequence is counted:
-- two accessions may each have a material 1.
--
-- Run these before deploying, on every live database -- a duplicate makes the
-- migration fail, and a failed migration means the instance does not start:
--
--   select code, count(*) from accession group by code having count(*) > 1;
--   select accession_id, code, count(*) from material
--     group by accession_id, code having count(*) > 1;

CREATE UNIQUE INDEX accession_code_idx ON accession (code);
CREATE UNIQUE INDEX material_accession_id_code_idx ON material (accession_id, code);
