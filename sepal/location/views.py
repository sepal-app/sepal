from dataclasses import dataclass
from typing import Any, Callable

from flask import Blueprint, g, jsonify, redirect, render_template, request, url_for
from flask_login import current_user, login_required
from sqlalchemy import func, select

import sepal.location.forms as forms
import sepal.utils.html as html
import sepal.utils.resource as resource
from sepal.location.lib import LocationEventAction, create_location_activity
from sepal.location.models import Location
from sepal.organization.lib import current_organization
from sepal.ui.paginator import Paginator

blueprint = Blueprint("location", __name__, template_folder="templates")
bp = blueprint


@dataclass
class TableColumn:
    title: str
    render: Callable[[Any], str]

    def __call__(self, row):
        return self.render(row)


list_table_columns = [
    TableColumn(
        "Name",
        lambda loc: html.tag(
            "a", {"href": url_for("location.detail", location_id=loc.id)}, loc.name
        ),
    ),
    TableColumn("Code", lambda loc: loc.code),
]


@bp.route("")
@login_required
def list():
    form = forms.ListLocationsForm(request.args)
    page = form.data["page"]
    page_size = form.data["page_size"]
    limit = page_size
    offset = limit * (page - 1)

    stmt = select(Location).where(Location.organization_id == current_organization.id)

    if form.data.get("q", None) is not None:
        stmt = stmt.where(
            Location.name.ilike(
                f"%{form.data['q']}%",
            )
        )

    locations = g.db.scalars(stmt.limit(limit).offset(offset)).all()

    if request.accept_mimetypes.best == "application/json":
        # TODO: Use marshmallow
        return jsonify([dict(name=t.name, id=t.id) for t in locations])

    count = g.db.scalar(select(func.count("*")).select_from(stmt))
    paginator = Paginator.create(page, page_size, len(locations), count)

    return render_template(
        "location/list.html",
        locations=locations,
        form=form,
        page=page,
        page_size=page_size,
        table_columns=list_table_columns,
        paginator=paginator,
    )


@bp.route("/create", methods=["GET", "POST"])
@login_required
def create():
    form = forms.CreateLocationForm(organization_id=current_organization.id)
    if form.validate_on_submit():
        location = Location.create_from_form(form)
        create_location_activity(
            action=LocationEventAction.Created, location=location, user=current_user
        )
        return redirect(url_for("location.detail", location_id=location.id))

    return render_template("location/create.html", form=form)


@bp.route("/<int:location_id>", methods=["GET", "POST"])
@login_required
def detail(location_id):
    location = resource.get_or_404(Location, location_id)

    form = forms.EditLocationForm(obj=location)
    if form.validate_on_submit():
        location.save_form(form)
        create_location_activity(
            action=LocationEventAction.Updated, location=location, user=current_user
        )
        return redirect(url_for("location.detail", location_id=location.id))

    return render_template("location/detail.html", location=location, form=form)
