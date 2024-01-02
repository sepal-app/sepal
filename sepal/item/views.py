from dataclasses import dataclass
from typing import Any, Callable

from flask import Blueprint, g, jsonify, redirect, render_template, request, url_for
from flask_login import current_user, login_required
from sqlalchemy import func, select
from sqlalchemy.orm import joinedload

import sepal.item.forms as forms
import sepal.utils.html as html
import sepal.utils.resource as resource
from sepal.accession.models import Accession
from sepal.forms import ListForm
from sepal.item.lib import AccessionItemEventAction, create_accession_item_activity
from sepal.item.models import AccessionItem
from sepal.organization.lib import current_organization
from sepal.ui.paginator import Paginator

blueprint = Blueprint("item", __name__, template_folder="templates")
bp = blueprint


@dataclass
class TableColumn:
    title: str
    render: Callable[[Any], str]

    def __call__(self, row):
        return self.render(row)


list_table_columns = [
    TableColumn(
        "Code",
        lambda i: html.tag(
            "a", {"href": url_for("item.detail", accession_item_id=i.id)}, i.code
        ),
    ),
    TableColumn(
        "Accession",
        lambda i: html.tag(
            "a",
            {"href": url_for("accession.detail", accession_id=i.accession.id)},
            i.accession.code,
        ),
    ),
    TableColumn(
        "Taxon",
        lambda i: html.tag(
            "a",
            {"href": url_for("taxon.detail", accession_id=i.accession.taxon_id)},
            i.accession.taxon.name,
        ),
    ),
]


@bp.route("")
@login_required
def list():
    form = ListForm(request.args)
    page = form.page.data
    page_size = form.page_size.data
    limit = page_size
    offset = limit * (page - 1)

    stmt = select(AccessionItem).where(
        AccessionItem.organization_id == current_organization.id
    )

    if form.data.get("q", None) is not None:
        stmt = stmt.where(
            Accession.code.ilike(
                f"{form.data['q']}%",
            )
        )

    items = g.db.scalars(
        stmt.options(joinedload(AccessionItem.accession).joinedload(Accession.taxon))
        .limit(limit)
        .offset(offset)
        .order_by("code")
    ).all()
    count = g.db.scalar(select(func.count("*")).select_from(stmt))

    if request.accept_mimetypes.best == "application/json":
        # TODO: Use marshmallow
        return jsonify([dict(code=i.code, id=i.id) for i in items])

    paginator = Paginator.create(page, page_size, len(items), count)

    return render_template(
        "item/list.html",
        items=items,
        form=form,
        page=page,
        table_columns=list_table_columns,
        paginator=paginator,
    )


@bp.route("/create", methods=["GET", "POST"])
@login_required
def create():
    form = forms.CreateAccessionItemForm(organization_id=current_organization.id)
    if form.validate_on_submit():
        item = AccessionItem.create_from_form(form)
        create_accession_item_activity(
            action=AccessionItemEventAction.Created, item=item, user=current_user
        )
        return redirect(url_for("item.detail", accession_item_id=item.id))

    return render_template("item/create.html", form=form)


@bp.route("/<int:accession_item_id>", methods=["GET", "POST"])
@login_required
def detail(accession_item_id):
    # TODO: make sure the user is a member of the organization and if not then
    # return a 404 page
    item = resource.get(
        AccessionItem,
        accession_item_id,
        # options=joinedload("accession")
        # .joinedload("accession.taxon")
        # .joinedload("location"),
        options=joinedload(AccessionItem.accession).joinedload(Accession.taxon),
        # options=[
        #     joinedload(AccessionItem.accession).joinedload(Accession.taxon),
        #     # joinedload(AccessionItem.location),
        # ]
        # .joinedload(AccessionItem.location),
    )

    form = forms.EditAccessionItemForm(obj=item)
    if form.validate_on_submit():
        item.save_form(form)
        create_accession_item_activity(
            action=AccessionItemEventAction.Updated, item=item, user=current_user
        )
        return redirect(url_for("item.detail", accession_item_id=item.id))

    if item.accession_id is not None:
        form.accession_id.choices = [
            (
                item.accession_id,
                " ".join(
                    [
                        item.accession.code,
                        "-",
                        item.accession.taxon.name,
                        item.accession.taxon.author,
                    ]
                ),
            )
        ]
        # form.accession_id.choices = []

    if item.location_id is not None:
        form.location_id.choices = [(item.location_id, item.location.code)]

    return render_template("item/detail.html", item=item, form=form)
