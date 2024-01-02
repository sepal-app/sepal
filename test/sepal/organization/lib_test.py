from test.fixtures import *

from pytest_factoryboy import LazyFixture, register

import sepal.organization.lib as lib


def test_create_organization_activity(session, organization, organization_user):
    user = organization_user.user
    organization = organization_user.organization
    activity = lib.create_organization_activity(
        lib.OrganizationEventAction.Created, organization, user, session=session
    )
    assert activity is not None
    assert activity.organization.id == organization.id
