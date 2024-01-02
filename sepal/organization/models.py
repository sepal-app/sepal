import enum
from typing import Optional

from sqlalchemy import Column, ForeignKey, UniqueConstraint
from sqlalchemy.dialects import postgresql as pg
from sqlalchemy.ext.associationproxy import AssociationProxy, association_proxy
from sqlalchemy.ext.mutable import MutableDict
from sqlalchemy.orm import Mapped, mapped_column, relationship

import sepal.db as db

# from sepal.auth.models import User
from sepal.db import text


class OrganizationRole(enum.Enum):
    Owner = "owner"
    Admin = "admin"
    Read = "read"
    Write = "write"


class Organization(db.Model, db.IdMixin):
    name: Mapped[text]
    abbreviation: Mapped[Optional[text]] = mapped_column(default="", server_default="")
    short_name: Mapped[Optional[text]] = mapped_column(default="", server_default="")

    address: Mapped[Optional[text]]
    city: Mapped[Optional[text]]
    state: Mapped[Optional[text]]
    country: Mapped[Optional[text]]
    postal_code: Mapped[Optional[text]]

    settings: Mapped[dict] = mapped_column(
        MutableDict.as_mutable(pg.JSONB), default={}, server_default="{}"
    )

    _organization_users: Mapped[list["OrganizationUser"]] = relationship(
        cascade="all, delete-orphan",
        # secondary=lambda: class_mapper(OrganizationUser).local_table,
        back_populates="organization",
    )

    users: AssociationProxy[list["User"]] = association_proxy(
        "_organization_users", "user", creator=lambda user: OrganizationUser(user=user)
    )


class OrganizationUser(db.Model, db.IdMixin):
    organization_id: Mapped[int] = mapped_column(
        ForeignKey("organization.id"),
    )
    organization: Mapped[Organization] = relationship()

    user_id: Mapped[int] = mapped_column(ForeignKey("user.id"))
    user: Mapped["User"] = relationship()

    role = Column(
        pg.ENUM(
            OrganizationRole,
            name="organization_role",
            values_callable=lambda roles: [r.value for r in roles],
        ),
        nullable=False,
    )

    __table_args__ = (
        UniqueConstraint(
            organization_id,
            user_id,
            role,
        ),
    )
