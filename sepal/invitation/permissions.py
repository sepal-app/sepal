from sepal.auth.models import User
from sepal.organization.lib import has_role
from sepal.organization.models import Organization, OrganizationRole
from sepal.permissions import Permission, has_permission


class _InviteUserPermission(Permission):
    name = "INVITE_USER"


INVITE_USER = _InviteUserPermission()


@has_permission.register(_InviteUserPermission)
def _(permission: Permission, user: User, org: Organization) -> bool:
    return has_role(org.id, user.id, [OrganizationRole.Owner, OrganizationRole.Admin])
