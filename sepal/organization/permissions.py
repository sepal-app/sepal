from sepal.auth.models import User
from sepal.organization.lib import has_role
from sepal.organization.models import Organization, OrganizationRole
from sepal.permissions import Permission, has_permission


class _ManageRolesPermission(Permission):
    name = "MANAGE_ROLES"


MANAGE_ROLES = _ManageRolesPermission()


@has_permission.register(_ManageRolesPermission)
def _(permission: Permission, user: User, org: Organization) -> bool:
    return has_role(org.id, user.id, [OrganizationRole.Owner, OrganizationRole.Admin])
