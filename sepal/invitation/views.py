from datetime import datetime, timezone

from flask import Blueprint, abort, flash, g, redirect, render_template, url_for
from flask_login import current_user, login_required
from flask_wtf import FlaskForm
from sendgrid import SendGridAPIClient
from sendgrid.helpers.mail import ClickTracking, Mail, OpenTracking, TrackingSettings

from sepal.invitation import forms
from sepal.invitation.models import Invitation
from sepal.organization.lib import (
    current_organization,
    is_member,
    set_current_organization,
)
from sepal.organization.models import Organization
from sepal.settings import settings

bp = Blueprint("invitation", __name__, template_folder="templates")


def send_invitation_email(invitation: Invitation) -> None:
    mail = Mail(
        from_email="Sepal Support <support@sepal.app>",
        to_emails=invitation.email,
        subject=f"Sepal - Invitation to join {invitation.organization.name}",
        html_content=render_template(
            "invitation/invite_email.html", invitation=invitation
        ),
        plain_text_content=render_template(
            "invitation/invite_email.txt", invitation=invitation
        ),
    )
    try:
        sg = SendGridAPIClient(api_key=settings.SENDGRID_API_KEY)
        if settings.DISABLE_EMAIL_TRACKING:
            tracking_settings = TrackingSettings()
            tracking_settings.click_tracking = ClickTracking(False, False)
            tracking_settings.open_tracking = OpenTracking(False)
            mail.tracking_settings = tracking_settings

        response = sg.client.mail.send.post(request_body=mail.get())
        # TODO: handle any errors
        print(response)
    except Exception as e:
        print(e)

    return invitation


@bp.route("/", methods=["GET", "POST"])
@login_required
def create():
    # TODO: Test that this user has permission to send invitations to other
    # users for this organization
    form = forms.InviteUsersForm(organization_id=current_organization.id)
    if form.validate_on_submit():
        for entry in form.emails.entries:
            org = Organization.get_by_id(form.organization_id.data)
            if entry.email.data in (None, ""):
                continue

            invitation = Invitation.create(
                organization=org,
                email=entry.email.data,
                role=entry.role.data,
                _created_by=current_user.id,
            )
            print(f"invitation: {invitation}")
            send_invitation_email(invitation)

        # TODO: show flash messages
        flash("Invitations sent")
        return redirect(url_for("invitation.create"))

    return render_template(
        "invitation/create.html", org=current_organization, form=form
    )


@bp.route("/<string:token>", methods=["GET"])
@login_required
def detail(token):
    invitation = Invitation.get_active_token(token)
    if invitation is None:
        # TODO: better invalid invitation page
        abort(404)

    if is_member(invitation.organization_id, current_user.id):
        set_current_organization(invitation.organization)
        flash(f"You are already a member of {invitation.organization.name}")
        return redirect(url_for("public.root"))

    return render_template(
        "invitation/detail.html", invitation=invitation, form=FlaskForm()
    )


@bp.route("/<string:token>/accept", methods=["POST"])
@login_required
def accept(token):
    invitation = Invitation.get_active_token(token)
    if invitation is None:
        # TODO: better invalid invitation page
        abort(404)

    with g.db.begin_nested():
        OrganizationUser.create(
            organization=invitation.organization,
            user=current_user,
            role=invitation.role,
        )
        invitation.accepted_at = datetime.now(timezone.utc)
        invitation.accepted_by = current_user
        g.db.commit()

    return redirect(url_for("public.root"))


@bp.route("/<string:token>/reject", methods=["POST"])
@login_required
def reject(token):
    invitation = Invitation.get_active_token(token)
    if invitation is None:
        # TODO: better invalid invitation page
        abort(404)
    invitation.rejected_at = datetime.now(timezone.utc)
    invitation.rejected_by = current_user
    g.db.commit()
    return redirect(url_for("public.root"))
