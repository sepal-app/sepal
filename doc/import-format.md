# Import format

What `load-import` reads. Any producer emitting this shape can load into a
garden; nothing in Sepal knows where the records came from.

```
clojure -M:dev:cli load-import --dir out --actor you@example.com [--dry-run]
```

## A record

One JSON file per table, each a list of records with four possible keys:

```json
{"id": "271",
 "created_at": "2006-10-11 09:33:25",
 "refs": {"accession_id": {"table": "accession", "id": "272"},
          "location_id":  {"table": "location",  "id": "4"}},
 "data": {"code": "1", "type": "plant", "quantity": 1}}
```

- **`id`** — the record's key in the source system. **Opaque text, never
  parsed**: `accession_note:1` and `species_synonym-3` are both real. Optional;
  two files carry none.
- **`created_at`** — optional. Restored after the load, because no create spec
  accepts a timestamp. `updated_at` is not supported: the `trigger_*_updated_at`
  triggers have no `WHEN` clause, so any write sets it to now.
- **`refs`** — references to resolve, **keyed by the field each one lands
  on**. Omitted when there are none; an absent reference is left out rather
  than written as null.
- **`data`** — passed to the component's `create!` **verbatim**. Keys are
  snake_case and become kebab-case keywords.

Nothing else is permitted at the top level, and `data` must match the create
spec exactly. A key with no column is a reported failure, not a dropped field.

## References

**The key is the field.** `refs.taxon_id` sets `taxon_id`, `refs.created_by`
sets `created_by`. There is no suffixing, so a foreign key whose column does
not end in `_id` needs no special case.

Three shapes for the value:

| Shape | Resolves against |
|---|---|
| `{"table": "accession", "id": "975"}` | a record earlier in this import |
| `{"wfo": "wfo-0000283538-2025-12"}` | `taxon.wfo_taxon_id` in the target garden |
| `{"sepal_id": 1234}` | a row already in the target garden, by id |

A `wfo` reference matching zero or two taxa is a failure, not a guess —
`wfo_taxon_id` is indexed but not unique. A `sepal_id` is accepted with a
warning: it is only valid against the database the input was built against.

## The files

Loaded in this order, which is the reference graph — a file may only point at
one before it.

| File | `id` | `created_at` | `refs` |
|---|---|---|---|
| `taxon` | yes | — | `parent_id` |
| `location` | yes | yes | — |
| `contact` | yes | yes | — |
| `tag` | yes | yes | — |
| `settings` | yes | — | — |
| `accession` | yes | yes | `taxon_id`, `supplier_contact_id`, `intended_location_id` |
| `material` | yes | yes | `accession_id`, `location_id` |
| `collection` | yes | yes | `accession_id` |
| `material_change` | yes | — | `material_id`, `from_location_id`, `to_location_id` |
| `note` | yes | yes | `resource_id` |
| `tag_link` | yes | yes | `tag_id`, `resource_id` |
| `taxon_vernacular` | — | — | `taxon_id` |
| `taxon_synonym` | yes | — | `taxon_id` |
| `taxon_distribution` | — | — | `taxon_id` |

A file with no records may be omitted. Malformed JSON stops the run.

Four are not a plain insert. `settings` is written as one key/value map.
`tag_link` goes through `tag!`, which takes positional arguments. The two
`taxon_*` files with no `id` are updates onto taxa that already exist.

`note` and `tag_link` are polymorphic: `refs.resource_id` is the record they
hang on, and `data.resource_type` says which kind it is.

## Running

The whole load is one transaction. Failures are collected rather than thrown —
the operator gets the list, not the first one — and it rolls back if any record
failed or if `--dry-run` was passed. A dry run is a real load that is thrown
away, which is the only kind that proves an insert would have succeeded.

It refuses when the directory is missing, when `--actor` names no `user` row
(it will not create one), or when the garden already holds accessions and
`--allow-nonempty` was not passed.

## After a load

Each record's source id is written to `import_record`, mapping it to the Sepal
row it became. That is what a later import resolves against. The table is
unique on `(source_table, source_id)`, so loading the same input twice is a
reported refusal rather than a silent duplicate.

The same mapping is written to `loaded.json` in the input directory, for an
operator reviewing a run. A dry run writes neither.

**Re-loading is not supported.** A source id is only unique within its own
system, so nothing here can be idempotent. Rebuild the garden and load once.
