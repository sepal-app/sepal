from typing import Literal

import flask
import sqlalchemy as sa
from flask_login import current_user

from sepal.organization.lib import current_organization
from sepal.organization.models import Organization, OrganizationUser

## TODO: Would these be better on the model class?


def get(
    cls,
    resource_id: int | str | float,
    organization_id: int | str | float | None | Literal["current"] = "current",
    user_id: int | str | float | None | Literal["current"] = "current",
    options=None,
    session: sa.orm.Session | None = None,
):
    """Load a resource in an organization.

    Returns None if the resource isn't owned by an organization where the user
    is a member.  Otherwise returns an instance of cls.

    """
    organization_id = (
        organization_id if organization_id != "current" else current_organization.id
    )
    user_id = user_id if user_id != "current" else current_user.id

    if session is None:
        session = flask.g.db

    stmt = (
        sa.select(cls)
        .join(getattr(cls, "organization"))
        .where(getattr(cls, "id") == int(resource_id))
    )

    if organization_id is not None and user_id is not None:
        stmt = stmt.join(Organization._organization_users).where(
            sa.and_(
                OrganizationUser.organization_id == organization_id,
                OrganizationUser.user_id == user_id,
            )
        )

    if options:
        stmt = stmt.options(options)

    return session.scalars(stmt).first()


def get_or_404(
    cls,
    resource_id: int | str | float,
    organization_id: int | str | float | None | Literal["current"] = "current",
    user_id: int | str | float | None | Literal["current"] = "current",
    options=None,
    session: sa.orm.Session | None = None,
):
    resource = get(
        cls,
        resource_id=resource_id,
        organization_id=organization_id,
        user_id=user_id,
        options=options,
        session=session,
    )
    if not resource:
        flask.abort(404)

    return resource
