from dataclasses import dataclass
from typing import Any, Callable

import sqlalchemy as sa
from flask import Blueprint, abort, g, redirect, render_template, request, url_for
from flask_login import current_user, login_required
from toolz import merge

from sepal.auth.models import User
from sepal.forms import ListForm
from sepal.organization import forms
from sepal.organization.lib import (
    OrganizationEventAction,
    create_organization_activity,
    set_current_organization,
)
from sepal.organization.models import Organization, OrganizationRole, OrganizationUser
from sepal.organization.permissions import MANAGE_ROLES
from sepal.permissions import has_permission
from sepal.ui.paginator import Paginator

blueprint = Blueprint("organization", __name__, template_folder="templates")
bp = blueprint

# TODO: when we visit any of the org routes then set the current organization in
# the cookie


@bp.route("/create", methods=["GET", "POST"])
@login_required
def create():
    form = forms.CreateOrganizationForm()
    if form.validate_on_submit():
        with g.db.begin_nested():
            org = Organization()
            form.populate_obj(org)
            org_user = OrganizationUser(
                organization=org, user=current_user, role=OrganizationRole.Owner.value
            )
            g.db.add_all([org, org_user])

        create_organization_activity(
            action=OrganizationEventAction.Created,
            organization=org,
            user=current_user,
            session=g.db,
        )

        set_current_organization(org)
        return redirect(url_for("dashboard.index", id=org.id))

    return render_template("organization/create.html", form=form)


def fetch_organization_or_404(organization_id):
    stmt = (
        sa.select(Organization)
        .join(OrganizationUser)
        .where(OrganizationUser.user_id == current_user.id)
    )
    org = g.db.scalars(stmt).first()
    if org is None:
        abort(404)

    return org


@bp.route("/<int:id>", methods=["GET"])
@login_required
def detail(id):
    return redirect(url_for("organization.detail_profile", id=id))


@bp.route("/<int:id>/profile", methods=["GET", "POST"])
@login_required
def detail_profile(id):
    org = fetch_organization_or_404(id)
    stmt = (
        sa.select(Organization)
        .join(OrganizationUser)
        .where(OrganizationUser.user_id == current_user.id)
    )
    org = g.db.scalars(stmt).first()
    if org is None:
        abort(404)

    form = forms.EditOrganizationForm(obj=org)
    if form.validate_on_submit():
        org.save_form(form)
        create_organization_activity(
            action=OrganizationEventAction.Updated, organization=org, user=current_user
        )
        return redirect(url_for("organization.detail_profile", id=org.id))

    set_current_organization(org)
    return render_template("organization/detail_profile.html", org=org, form=form)


@dataclass
class TableColumn:
    title: str
    render: Callable[[Any], str]

    def __call__(self, row):
        return self.render(row)


def list_table_columns(user: User, organization: Organization):
    columns = [
        TableColumn("Email", lambda ou: ou.user.email),
    ]

    if has_permission(MANAGE_ROLES, user, organization):
        columns += [
            TableColumn(
                "Role",
                lambda ou: render_template(
                    "organization/_detail_team_role_select.html",
                    role=ou.role,
                    role_type=OrganizationRole,
                    user_role=ou.role,
                ),
            ),
        ]

    return columns


@bp.route("/<int:id>/team", methods=["GET", "POST"])
@login_required
def detail_team(id):
    org = fetch_organization_or_404(id)
    form = ListForm(request.args)
    page = form.page.data
    page_size = form.page_size.data
    limit = page_size
    offset = limit * (page - 1)

    stmt = (
        sa.select(OrganizationUser)
        .options(sa.orm.joinedload(OrganizationUser.user))
        .join(User)
        .where(OrganizationUser.organization_id == id)
    )

    if form.data.get("q", None) is not None:
        stmt = stmt.where(
            User.email.ilike(
                f"{form.data['q']}%",
            )
        )

    users = g.db.scalars(stmt.limit(limit).offset(offset).order_by("email")).all()
    count = g.db.scalar(sa.select(sa.func.count("*")).select_from(stmt))
    paginator = Paginator.create(page, page_size, len(users), count)

    return render_template(
        "organization/detail_team.html",
        users=users,
        form=form,
        org=org,
        page=page,
        table_columns=list_table_columns(current_user, org),
        paginator=paginator,
    )
    return render_template("organization/detail_team.html", org=org, form=form)


@bp.route("/<int:id>/settings", methods=["GET", "POST"])
@login_required
def detail_settings(id):
    org = fetch_organization_or_404(id)
    form = forms.OrganizationSettingsForm(data=org.settings)
    if form.validate_on_submit():
        with g.db.begin_nested():
            org.settings = merge(org.settings, form.data)

        # g.db.refresh(org)
        return redirect(url_for("organization.detail_settings", id=org.id))
    return render_template("organization/detail_settings.html", org=org, form=form)


@bp.route("/<int:id>/billing", methods=["GET", "POST"])
@login_required
def detail_billing(id):
    org = fetch_organization_or_404(id)
    form = None
    return render_template("organization/detail_billing.html", org=org, form=form)


@bp.route("", methods=["GET", "POST"])
@login_required
def switch():
    # TODO:
    return ""
