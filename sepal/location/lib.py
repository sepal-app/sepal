import enum
from dataclasses import asdict, dataclass

from sepal.activity.models import Activity, ActivityType
from sepal.auth.models import User
from sepal.location.models import Location


class LocationEventAction(enum.StrEnum):
    Created = "created"
    Deleted = "deleted"
    Updated = "updated"


@dataclass
class LocationEventPayload:
    action: LocationEventAction
    location_id: int
    location_name: str
    location_code: str


def create_location_activity(
    action: LocationEventAction, location: Location, user: User, session=None
):
    payload = LocationEventPayload(
        action=action,
        location_id=location.id,
        location_name=location.name,
        location_code=location.code,
    )
    activity = Activity.create(
        payload=asdict(payload),
        user=user,
        organization=location.organization,
        type=ActivityType.LocationEvent,
        session=session,
    )
    return activity
