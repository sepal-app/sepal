from abc import ABC
from functools import singledispatch
from typing import Any, Optional

import flask
from flask_login import current_user

from sepal.auth.models import User
from sepal.organization.lib import get_current_organization


class Permission(ABC):
    name = ""


@singledispatch
def has_permission(permission: Permission, user: User, resource: Any) -> bool:
    return False


_permission_name_map: dict[str, Permission] = {}


def has_permission_template(name: str, resource: Optional[Any]) -> bool:
    """Make has_permission available in templates.

    This function assumes the permission check is for the current user. If a
    resource isn't provided it assumes you want to check the permission against
    the current organization.

    """
    perm = _permission_name_map.get(name, None)
    if perm is None:
        return False

    if resource is None:
        resource = get_current_organization()

    return has_permission(perm, current_user, get_current_organization())


def init_app(app: flask.Flask) -> None:
    global _permission_name_map
    # Create a map of permission name to permission instances so we can use
    # permission names in templates
    _permission_name_map = {
        p.name: p() for p in has_permission.registry.keys() if hasattr(p, "name")
    }

    app.context_processor(lambda: dict(has_permission=has_permission_template))
