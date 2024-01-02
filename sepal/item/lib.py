import enum
from dataclasses import asdict, dataclass

from sepal.activity.models import Activity, ActivityType
from sepal.auth.models import User
from sepal.item.models import AccessionItem


class AccessionItemEventAction(enum.StrEnum):
    Created = "created"
    Deleted = "deleted"
    Updated = "updated"


@dataclass
class AccessionItemEventPayload:
    action: AccessionItemEventAction
    accession_code: str
    accession_id: int
    accession_item_code: str
    accession_item_id: int
    taxon_name: str
    taxon_id: int
    taxon_name: str
    location_code: str
    location_id: int


def create_accession_item_activity(
    action: AccessionItemEventAction, item: AccessionItem, user: User, session=None
):
    payload = AccessionItemEventPayload(
        action=action,
        accession_item_id=item.id,
        accession_item_code=item.code,
        accession_code=item.accession.code,
        accession_id=item.accession.id,
        taxon_id=item.accession.taxon.id,
        taxon_name=item.accession.taxon.name,
        location_code=item.location.code,
        location_id=item.location.code,
    )
    return Activity.create(
        payload=asdict(payload),
        user=user,
        organization=item.organization,
        type=ActivityType.AccessionItemEvent,
        session=session,
    )
