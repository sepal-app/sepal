import flask
from sqlalchemy import select

from sepal.auth.models import User
from test.fixtures import *  # noqa


def test_login_view(session, client, user, user__password):
    """Successful login"""
    user.password = "Password1"
    session.commit()

    r = client.post(
        "/login",
        data={"email": user.email, "password": "Password1", "csrf_token": "1234"},
    )
    assert r.status_code == 302, r.data
    assert r.headers["Location"] == "/"
    assert flask.session.get("_user_id") == str(user.id), flask.session


def test_register_view(session, client, faker):
    """Successful registration"""
    email = faker.email()
    user = session.scalar(select(User).where(User.email == email))

    r = client.post(
        "/register",
        data={
            "email": email,
            "password": "Password1",
            "confirm_password": "Password1",
            "csrf_token": "1234",
        },
    )
    assert r.status_code == 302, r.data
    assert r.headers["Location"] == "/"
    user = session.scalar(select(User).where(User.email == email))
    assert user is not None, user
    assert flask.session.get("_user_id") == str(user.id), flask.session


def test_logout_view(client, user):
    """Successful logout"""
    r = client.get(
        "/logout",
    )
    assert r.status_code == 302, r.data
    assert r.headers["Location"] == "/login"
    assert len(flask.session.keys()) == 0, flask.session
