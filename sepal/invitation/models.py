import secrets
from datetime import datetime, timedelta, timezone
from typing import Optional, Self

import flask
from sqlalchemy import DateTime, ForeignKey, func, null, select
from sqlalchemy.dialects import postgresql as pg
from sqlalchemy.orm import Mapped, joinedload, mapped_column, relationship

import sepal.db as db
from sepal.db import text
from sepal.organization.models import Organization, OrganizationRole
from sepal.settings import settings


class Invitation(db.Model, db.IdMixin):
    organization_id: Mapped[int] = mapped_column(
        ForeignKey("organization.id"),
    )
    organization: Mapped[Organization] = relationship()

    email: Mapped[text] = mapped_column()
    token: Mapped[text] = mapped_column(unique=True, default=secrets.token_hex(16))
    role: Mapped[OrganizationRole] = mapped_column(
        pg.ENUM(
            OrganizationRole,
            name="organization_role",
            values_callable=lambda roles: [r.value for r in roles],
        ),
    )
    rejected_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True))
    _rejected_by: Mapped[Optional[int]] = mapped_column(
        "rejected_by", ForeignKey("user.id")
    )
    rejected_by: Mapped[Optional["User"]] = relationship(foreign_keys=[_rejected_by])

    accepted_at: Mapped[Optional[datetime]] = mapped_column(DateTime(timezone=True))
    _accepted_by: Mapped[Optional[int]] = mapped_column(
        "accepted_by", ForeignKey("user.id")
    )
    accepted_by: Mapped[Optional["User"]] = relationship(foreign_keys=[_accepted_by])

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )

    _created_by: Mapped[int] = mapped_column("created_by", ForeignKey("user.id"))
    created_by: Mapped["User"] = relationship(foreign_keys=[_created_by])

    @classmethod
    def get_active_token(cls, token, session=None) -> Optional[Self]:
        """Get invitation by its token."""
        if session is None:
            session = flask.g.db

        now = datetime.now(timezone.utc)

        return session.scalar(
            select(Invitation)
            .where(
                Invitation.token == token,
                Invitation.created_at
                < now + timedelta(minutes=settings.INVITATION_TTL_MINUTES),
                Invitation.rejected_at == null(),
                Invitation.accepted_at == null(),
            )
            .options(joinedload(Invitation.organization))
        )
