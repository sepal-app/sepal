from datetime import datetime
from typing import Optional

from sqlalchemy import DateTime, ForeignKey, UniqueConstraint, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

import sepal.db as db
from sepal.db import text
from sepal.organization.models import Organization


class Location(db.Model, db.IdMixin):
    name: Mapped[text]
    code: Mapped[Optional[text]]
    description: Mapped[text]
    organization_id: Mapped[int] = mapped_column(ForeignKey("organization.id"))
    organization: Mapped[Organization] = relationship()

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )

    items: Mapped[list["AccessionItem"]] = relationship(
        back_populates="location", cascade="all, delete-orphan"
    )

    __table_args__ = (
        UniqueConstraint(organization_id, "name"),
        UniqueConstraint(organization_id, "code"),
    )
