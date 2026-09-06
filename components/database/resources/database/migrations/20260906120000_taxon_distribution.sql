-- taxon.distribution: a free-text answer to "where is this species from",
-- for a curator to write and a label to show. Plan 034 -- see the plan for
-- why this is one column and not a TDWG reference table.
ALTER TABLE taxon ADD COLUMN distribution text;
