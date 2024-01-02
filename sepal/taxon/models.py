import enum
from datetime import datetime
from typing import Optional

from sqlalchemy import DateTime, Enum, ForeignKey, UniqueConstraint, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

import sepal.db as db
from sepal.db import text
from sepal.organization.models import Organization


class TaxonRank(enum.Enum):
    Class = "class"
    Cultivar = "cultivar"
    CultivarGroup = "cultivar_group"
    Family = "family"
    Form = "form"
    Genus = "genus"
    Grex = "grex"
    Kingdom = "kingdom"
    Order = "order"
    Phylum = "phylum"
    Section = "section"
    Series = "series"
    Species = "species"
    Subclass = "subclass"
    Subfamily = "subfamily"
    Subform = "subform"
    Subgenus = "subgenus"
    Subsection = "subsection"
    Subseries = "subseries"
    Subspecies = "subspecies"
    Subtribe = "subtribe"
    Subvariety = "subvariety"
    Superorder = "superorder"
    Tribe = "tribe"
    Variety = "variety"

    @classmethod
    def choices(cls):
        return [(rank.value, rank.name) for rank in cls]


class Taxon(db.Model, db.IdMixin):
    name: Mapped[text]
    author: Mapped[text] = mapped_column(default="", server_default="")
    rank: Mapped[Enum] = mapped_column(
        Enum(
            TaxonRank,
            name="taxon_rank",
            values_callable=lambda ranks: [r.value for r in ranks],
        ),
    )
    parent_id: Mapped[Optional[int]] = mapped_column(ForeignKey("taxon.id"))
    parent: Mapped["Taxon"] = relationship(
        remote_side=lambda: Taxon.id,
        back_populates="children",
    )
    children: Mapped[list["Taxon"]] = relationship(
        remote_side=lambda: Taxon.parent_id,
        cascade="all, delete-orphan",
        back_populates="parent",
    )

    accessions: Mapped[list["Accession"]] = relationship(
        back_populates="taxon", cascade="all, delete-orphan"
    )

    organization_id: Mapped[int] = mapped_column(ForeignKey("organization.id"))
    organization: Mapped[Organization] = relationship()

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )

    __table_args__ = (
        UniqueConstraint(
            organization_id,
            "name",
            author,
            rank,
        ),
    )
