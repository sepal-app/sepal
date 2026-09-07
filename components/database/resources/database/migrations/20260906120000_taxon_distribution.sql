-- taxon.distribution: a free-text answer to "where is this species from",
-- for a curator to write and a label to show. One free-text column rather
-- than a TDWG geography table and a join, because nothing here queries the
-- structure -- the only thing it ever fed was a string on a label.
ALTER TABLE taxon ADD COLUMN distribution text;
