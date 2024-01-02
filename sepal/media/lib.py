import enum
from dataclasses import asdict, dataclass
from typing import Optional

from sqlalchemy.orm import Session

from sepal.activity.models import Activity, ActivityType
from sepal.auth.models import User
from sepal.media.models import Media


class MediaEventAction(enum.StrEnum):
    Created = "created"
    Deleted = "deleted"
    Updated = "updated"


@dataclass
class MediaEventPayload:
    action: MediaEventAction
    media_id: int
    media_title: str


def create_media_activity(
    action: MediaEventAction,
    media: Media,
    user: User,
    session: Optional[Session] = None,
):
    payload = MediaEventPayload(
        action=action,
        media_id=media.id,
        media_title=media.title,
    )
    activity = Activity.create(
        payload=asdict(payload),
        user=user,
        organization=media.organization,
        type=ActivityType.MediaEvent,
        session=session,
    )
    return activity
