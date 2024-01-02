from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, Text, UniqueConstraint, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

import sepal.db as db
from sepal.organization.models import Organization
from sepal.taxon.models import Taxon


class Accession(db.Model, db.IdMixin):
    code: Mapped[str] = mapped_column(Text)
    taxon_id: Mapped[int] = mapped_column(
        ForeignKey("taxon.id"),
    )
    taxon: Mapped["Taxon"] = relationship(
        remote_side=lambda: Taxon.id,
        back_populates="accessions",
    )
    organization_id: Mapped[int] = mapped_column(ForeignKey("organization.id"))
    organization: Mapped[Organization] = relationship()

    items: Mapped[list["AccessionItem"]] = relationship(
        back_populates="accession", cascade="all, delete-orphan"
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )

    __table_args__ = (UniqueConstraint(organization_id, code),)
