from flask import Blueprint, redirect, session, url_for
from flask_login import current_user

from sepal.auth.models import User
from sepal.extensions import login_manager
from sepal.organization.lib import (
    get_current_organization,
    is_member,
    set_current_organization,
)


@login_manager.user_loader
def load_user(user_id):
    return User.get_by_id(user_id)


blueprint = Blueprint("public", __name__, template_folder="templates")
bp = blueprint


@bp.route("")
def root():
    if not current_user.is_authenticated:
        # redirect if not logged in instead of using the login_required decorator
        # so to avoid showing the login_message
        return redirect(url_for(login_manager.login_view))

    orgs = current_user.organizations
    if orgs is None or len(orgs) == 0:
        return redirect(url_for("organization.create"))

    session["show_switch_organizations"] = "true" if len(orgs) > 1 else "false"

    org = get_current_organization()
    if org is not None and is_member(org.id, current_user.id):
        return redirect(url_for("dashboard.index"))

    set_current_organization(orgs[0])
    return redirect(url_for("dashboard.index"))


@bp.route("/ok")
def ok():
    return ""
