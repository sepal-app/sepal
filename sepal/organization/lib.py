import enum
import json
from dataclasses import asdict, dataclass
from typing import Optional

from flask import g, session
from sqlalchemy import select
from werkzeug.local import LocalProxy

from sepal.activity.models import Activity, ActivityType
from sepal.auth.models import User
from sepal.organization.models import Organization, OrganizationRole, OrganizationUser

current_organization = LocalProxy(lambda: get_current_organization())
current_organization_key = "current_organization"


class OrganizationEventAction(enum.StrEnum):
    Created = "created"
    Deleted = "deleted"
    Updated = "updated"


@dataclass
class OrganizationEventPayload:
    action: OrganizationEventAction
    organization_id: int
    organization_name: str


def create_organization_activity(
    action: OrganizationEventAction,
    organization: Organization,
    user: User,
    session=None,
):
    payload = OrganizationEventPayload(
        action=action,
        organization_id=organization.id,
        organization_name=organization.name,
    )
    activity = Activity.create(
        payload=asdict(payload),
        user=user,
        organization=organization,
        type=ActivityType.OrganizationEvent,
        session=session,
    )
    return activity


def is_member(organization_id, user_id, session=None) -> bool:
    if session is None:
        session = g.db

    stmt = select(OrganizationUser).where(
        OrganizationUser.organization_id == organization_id,
        OrganizationUser.user_id == user_id,
    )
    result = session.scalar(stmt)
    return result


def has_role(
    organization_id: int,
    user_id: int,
    role: OrganizationRole | list[OrganizationRole],
    session=None,
) -> bool:
    if session is None:
        session = g.db

    stmt = select(OrganizationUser).where(
        OrganizationUser.organization_id == organization_id,
        OrganizationUser.user_id == user_id,
    )

    if isinstance(role, list):
        stmt = stmt.where(OrganizationUser.role.in_(role))
    else:
        stmt = stmt.where(OrganizationUser.role == role)

    return session.scalar(stmt)


def get_current_organization() -> Optional[Organization]:
    """Return the Organization as stored as `current_organization` in the session.

    This function could potentially get called multiple times in a request via
    the current_organization proxy but we should be safe from excessive queries
    queries since it will be cached in the session for the request context.

    """
    if current_organization_key not in session:
        return None

    id = json.loads(session[current_organization_key]).get("id")
    return g.db.get(Organization, id)


def set_current_organization(org: Organization) -> None:
    data = {}
    for key in org.__mapper__.c.keys():
        data[key] = getattr(org, key)

    session["current_organization"] = json.dumps(data)


def clear_current_organization() -> None:
    if current_organization_key in session:
        del session[current_organization_key]
