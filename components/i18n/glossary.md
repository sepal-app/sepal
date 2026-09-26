# Translation glossary

Sepal's interface uses the vocabulary of living plant collections. Several of
these words have an everyday meaning that is wrong here. Translate each term
the way botanical gardens that work in the target language use it, and use the
same translation everywhere.

`bin/i18n-translate` sends this file with every request. A domain reviewer
fills in each language's column.

| Term | Meaning in Sepal | Not | Español |
|---|---|---|---|
| accession | A batch of plants received at one time from one source, recorded under one accession number | an entry, access or admission | |
| accession number, code | The identifier a garden gives an accession or material, e.g. `2026.0149` | a postal code or source code | |
| material | One plant or group of plants from an accession, at one location | fabric, supplies or raw material | |
| taxon (plural taxa) | A named group in the plant classification: family, genus, species, subspecies, variety, cultivar | a tax | |
| location | A named place in the garden where material grows, e.g. a bed or greenhouse | a map address | |
| provenance | Where an accession came from: wild, cultivated from wild, garden, or unknown | an art-history provenance | |
| wild provenance status | Whether wild material is from its native range, introduced, or unknown | | |
| received as | The form the accession arrived in: seed, cutting, bare-root plant | | |
| propagation | An attempt to produce new plants from existing material: seed sowing, cutting, grafting, division | reproduction in general | |
| rootstock | The plant a graft is joined onto | | |
| parentage, cross | The two parent taxa of a hybrid | family relationship | |
| vernacular name | A taxon's common name in some language | | |
| observation | A dated record of a plant's condition, phenology or pests | | |
| phenology | The plant's stage in its seasonal cycle: budding, flowering, fruiting | | |
| contact | A person or organisation a garden exchanges material with | | |
| archive | Hide a record from lists while keeping it and its history | backup storage | |

Taxon names and their rank connectors (`subsp.`, `var.`, `f.`) are botanical
Latin. They are never translated.

## Register

How the interface addresses the user, per language.

| Language | Register |
|---|---|
| Español | Formal *usted* wherever the text addresses the user ("Su perfil", "Introduzca su correo electrónico"). Buttons and menu commands take the infinitive ("Guardar cambios") |
