from flask import Blueprint, render_template
from flask_login import login_required

from sepal.organization.lib import (
    current_organization,
)

blueprint = Blueprint("dashboard", __name__, template_folder="templates")
bp = blueprint


@bp.route("")
@login_required
def index():
    return render_template("dashboard/index.html", org=current_organization)
