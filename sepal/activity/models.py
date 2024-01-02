import enum
from datetime import datetime

from sqlalchemy import DateTime, Enum, ForeignKey, func
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship

import sepal.db as db
from sepal.auth.models import User
from sepal.organization.models import Organization


class ActivityType(enum.StrEnum):
    AccessionEvent = "accession_event"
    AccessionItemEvent = "accession_item_event"
    TaxonEvent = "taxon_event"
    MediaEvent = "media_event"
    LocationEvent = "location_event"
    OrganizationEvent = "organization_event"
    UserEvent = "user_event"


class Activity(db.Model, db.IdMixin):
    payload: Mapped[dict] = mapped_column(JSONB)
    type: Mapped[Enum] = mapped_column(
        Enum(
            ActivityType,
            name="activity_type",
            values_callable=lambda types: [t.value for t in types],
        ),
    )

    # TODO: Need to account for the user id not existing. Or maybe we only soft
    # delete users.
    user_id: Mapped[int] = mapped_column(ForeignKey("user.id"))
    user: Mapped["User"] = relationship(
        remote_side=lambda: User.id,
        # back_populates="activity",
    )

    organization_id: Mapped[int] = mapped_column(ForeignKey("organization.id"))
    organization: Mapped["Organization"] = relationship(
        remote_side=lambda: Organization.id,
        # back_populates="activity",
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )
