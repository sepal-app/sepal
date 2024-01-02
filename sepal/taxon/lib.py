import enum
from dataclasses import asdict, dataclass
from typing import Optional

from sqlalchemy.orm import Session

from sepal.activity.models import Activity, ActivityType
from sepal.auth.models import User
from sepal.taxon.models import Taxon


class TaxonEventAction(enum.StrEnum):
    Created = "created"
    Deleted = "deleted"
    Updated = "updated"


@dataclass
class TaxonEventPayload:
    action: TaxonEventAction
    taxon_id: int
    taxon_name: str


def create_taxon_activity(
    action: TaxonEventAction,
    taxon: Taxon,
    user: User,
    session: Optional[Session] = None,
):
    payload = TaxonEventPayload(action=action, taxon_id=taxon.id, taxon_name=taxon.name)
    activity = Activity.create(
        payload=asdict(payload),
        user=user,
        organization=taxon.organization,
        type=ActivityType.TaxonEvent,
        session=session,
    )
    return activity
