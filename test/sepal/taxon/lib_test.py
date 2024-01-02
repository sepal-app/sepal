from test.fixtures import *

from pytest_factoryboy import LazyFixture, register

import sepal.taxon.lib as lib


def test_create_taxon_activity(session, taxon, organization_user):
    user = organization_user.user
    organization = organization_user.organization
    activity = lib.create_taxon_activity(
        lib.TaxonEventAction.Created, taxon, user, session=session
    )
    assert activity is not None
    assert activity.organization.id == organization.id
