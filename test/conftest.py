import os

from pytest_factoryboy import register

# import sepal.models  # noqa: F401
import sepal.accession.models
import sepal.activity.models
import sepal.auth.models
import sepal.db as db
import sepal.item.models
import sepal.location.models
import sepal.organization.models
import sepal.taxon.models

import test.factories as factories
import test.fixtures as fixtures

from test.factories import *  # noqa
from test.fixtures import *  # noqa

register(factories.AccessionFactory)
register(factories.OrganizationFactory)
register(factories.TaxonFactory)
register(factories.UserFactory)


def pytest_configure():
    """Setup pytest"""
    db.metadata.create_all(bind=factories.engine)


def pytest_unconfigure():
    """Cleanup pytest"""
    factories.Session.close_all()
    db.metadata.drop_all(bind=factories.engine)
