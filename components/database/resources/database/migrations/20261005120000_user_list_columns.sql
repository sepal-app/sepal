-- Which columns a user has chosen on each list page, as
-- {"<list>": {"<column>": true|false}}. Null means no choice on any list.
--
-- No `begin transaction` / `commit`: migrate.clj/apply-one! wraps every
-- migration in one.

ALTER TABLE "user" ADD COLUMN list_columns text
  CHECK (list_columns IS NULL OR json_valid(list_columns));

-- Author sort on the taxa list.
CREATE INDEX taxon_author_lower_idx ON taxon (lower(author));

-- Rank sort on the taxa list.
CREATE INDEX taxon_rank_idx ON taxon (rank);
