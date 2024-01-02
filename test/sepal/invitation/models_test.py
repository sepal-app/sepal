from datetime import datetime, timedelta, timezone

import pytest
from flask import url_for
from sqlalchemy import select

from sepal.invitation.models import Invitation
from sepal.organization.models import OrganizationRole
from test.fixtures import *  # noqa


def test_get_active_by_token(session, invitation_factory, user):
    invitation = invitation_factory(role=OrganizationRole.Read, created_by=user)
    inv = Invitation.get_active_token(invitation.token, session=session)
    assert inv.token == invitation.token


def test_get_active_by_token_invalid(session, invitation_factory, user):
    invitation = invitation_factory(role=OrganizationRole.Read, created_by=user)
    assert Invitation.get_active_token("1234", session=session) is None


def test_get_active_by_token_expired(session, invitation_factory, user):
    invitation = invitation_factory(
        role=OrganizationRole.Read,
        created_by=user,
        created_at=datetime.now(timezone.utc) + timedelta(hours=100),
    )
    inv = Invitation.get_active_token(invitation.token, session=session)
    assert inv is None


def test_get_active_by_token_rejected(session, invitation_factory, user):
    invitation = invitation_factory(
        role=OrganizationRole.Read,
        created_by=user,
        rejected_at=datetime.now(timezone.utc),
        rejected_by=user,
    )
    inv = Invitation.get_active_token(invitation.token, session=session)
    assert inv is None


def test_get_active_by_token_accepted(session, invitation_factory, user):
    invitation = invitation_factory(
        role=OrganizationRole.Read,
        created_by=user,
        accepted_at=datetime.now(timezone.utc),
        accepted_by=user,
    )
    inv = Invitation.get_active_token(invitation.token, session=session)
    assert inv is None
