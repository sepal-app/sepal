import random

import pytest
from flask_login import FlaskLoginClient


from sepal.app import create_app
from sepal.organization.models import OrganizationRole, OrganizationUser

from . import factories


@pytest.fixture
def app():
    app = create_app()
    app.config.update(
        {
            "TESTING": True,
            # Disable csrf validation in tests
            "WTF_CSRF_CHECK_DEFAULT": False,
            "WTF_CSRF_ENABLED": False,
            "SESSION_PROTECTION": None,
        }
    )
    yield app


@pytest.fixture
def user_client(app, user):
    old_class = app.test_client_class
    app.test_client_class = FlaskLoginClient
    with app.test_client(user=user) as tc:
        yield tc
        app.test_client_class = old_class


@pytest.fixture
def client(app):
    with app.test_client() as tc:
        yield tc


@pytest.fixture
def session():
    return factories.Session()


@pytest.fixture
def password():
    return "Password1"


@pytest.fixture
def organization_user(session, user, organization):
    org_user = OrganizationUser(
        organization=organization,
        user=user,
        role=OrganizationRole.Owner,
    )
    session.add(org_user)
    session.commit()
    yield org_user


@pytest.fixture(scope="session", autouse=True)
def faker_seed():
    return random.randint(1, 1000)
