#!/usr/bin/env sh
set -Eeuxo pipefail

SCHEMA=${SCHEMA:-wfo_plantlist_2023_12}
WFO_PATH=${WFO_PATH:-/home/brett/devel/wfo_plantlist}

# TODO: Warn if a WFO_PATH isn't set

psql -c "\copy $SCHEMA.name(id, alternative_id, basionym_id, scientific_name, authorship, rank, uninomial, genus, infrageneric_epithet, specific_epithet, infraspecific_epithet, code, reference_id, published_in_year, link) from '$WFO_PATH/name.tsv' with (format csv, header true, delimiter E'\t');"

psql -c "\copy $SCHEMA.reference (id, citation, link, doi, remarks) from '$WFO_PATH/reference.tsv' with (format csv, header true, delimiter E'\t');"

psql -c "\copy $SCHEMA.synonym (id, taxon_id, name_id, according_to_id, reference_id, link) from '$WFO_PATH/synonym.tsv' with (format csv, header true, delimiter E'\t');"

psql -c "\copy $SCHEMA.taxon(id, name_id, parent_id, according_to_id, scrutinizer, scrutinizer_id, scrutinizer_date, reference_id, link) from '$WFO_PATH/taxon.tsv' with (format csv, header true, delimiter E'\t');"
