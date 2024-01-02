from datetime import datetime

from sqlalchemy import DateTime, ForeignKey, UniqueConstraint, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

import sepal.db as db
from sepal.accession.models import Accession
from sepal.db import text
from sepal.location.models import Location
from sepal.organization.models import Organization


class AccessionItem(db.Model, db.IdMixin):
    code: Mapped[text]
    accession_id: Mapped[int] = mapped_column(
        ForeignKey("accession.id"),
    )
    accession: Mapped[Accession] = relationship(back_populates="items")

    location_id: Mapped[int] = mapped_column(ForeignKey("location.id"))
    location: Mapped["Location"] = relationship(back_populates="items")

    organization_id: Mapped[int] = mapped_column(ForeignKey("organization.id"))
    organization: Mapped[Organization] = relationship()

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )

    __table_args__ = (UniqueConstraint(organization_id, accession_id, "code"),)
