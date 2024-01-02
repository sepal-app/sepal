from sepal.permissions import Permission, has_permission

from flask import render_template_string

from test.fixtures import *  # noqa


class _TestPermission(Permission):
    name: str = "TEST"


TEST_PERM = _TestPermission()


@has_permission.register(_TestPermission)
def _(permission: Permission, user_id: int, organization_id: int) -> bool:
    return True


def test_perm(app, user):
    assert has_permission(TEST_PERM, user, "something")


def test_has_permission_in_templates(app, user):
    s = render_template_string("{% if has_permission('TEST', 123) %}TEST{% endif %}")
    assert s == "TEST"
