from datetime import datetime, timedelta, timezone

from flask import url_for
from sqlalchemy import select

from sepal.invitation.models import Invitation
from sepal.organization.models import OrganizationRole
from test.fixtures import *  # noqa


def test_detail_view_login_required(client, invitation_factory, user):
    invitation = invitation_factory(role=OrganizationRole.Read, created_by=user)
    url = url_for("invitation.detail", token=invitation.token)
    r = client.get(url)
    assert r.status_code == 302
    assert r.headers["Location"] == url_for("auth.login", next=url)


def test_detail_view(
    session,
    user_client,
    invitation_factory,
    faker,
    organization_user,
    user,
    organization,
    organization_factory,
    user_factory,
):
    org2 = organization_factory()
    user2 = user_factory()
    invitation = invitation_factory(
        email=user.email,
        role=OrganizationRole.Read,
        created_by=user2,
        created_at=datetime.now(timezone.utc) - timedelta(hours=1),
        rejected_at=None,
        accepted_at=None,
        organization=org2,
    )
    session.commit()
    url = url_for("invitation.detail", token=invitation.token)
    r = user_client.get(url)
    assert r.status_code == 200


def test_reject_invitation(client, invitation_factory, user):
    invitation = invitation_factory(role=OrganizationRole.Read, created_by=user)
    url = url_for("invitation.reject", token=invitation.token)
    r = client.post(
        url,
    )
    assert r.status_code == 302
    assert r.headers["Location"] == url_for("auth.login", next=url)


def test_accept_invitation(client, invitation_factory, user):
    invitation = invitation_factory(role=OrganizationRole.Read, created_by=user)
    url = url_for("invitation.accept", token=invitation.token)
    r = client.post(
        url,
    )
    assert r.status_code == 302
    assert r.headers["Location"] == url_for("auth.login", next=url)
