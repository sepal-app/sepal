from dataclasses import dataclass
from typing import Any, Callable

from flask import Blueprint, g, jsonify, redirect, render_template, request, url_for
from flask_login import current_user, login_required
from sqlalchemy import func, select
from sqlalchemy.orm import joinedload

import sepal.taxon.forms as forms
import sepal.utils.resource as resource
from sepal.forms import ListForm
from sepal.organization.lib import current_organization
from sepal.taxon.lib import TaxonEventAction, create_taxon_activity
from sepal.taxon.models import Taxon
from sepal.ui.paginator import Paginator

blueprint = Blueprint("taxon", __name__, template_folder="templates")
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
        lambda t: f"<a href=\"{url_for('taxon.detail', taxon_id=t.id)}\">{t.name}</a>",
    ),
    TableColumn("Rank", lambda t: t.rank.name),
    TableColumn("Author", lambda t: t.author),
    TableColumn("Parent", lambda t: t.parent.name if t.parent else ""),
]


@bp.route("")
@login_required
def list():
    form = ListForm(request.args)
    page = form.page.data
    page_size = form.page_size.data
    limit = page_size
    offset = limit * (page - 1)

    stmt = select(Taxon).where(Taxon.organization_id == current_organization.id)

    if form.data.get("q", None) is not None:
        stmt = stmt.where(
            Taxon.name.ilike(
                f"{form.data['q']}%",
            )
        )

    taxa = g.db.scalars(
        stmt.options(joinedload(Taxon.parent))
        .limit(limit)
        .offset(offset)
        .order_by("name")
    ).all()

    if request.accept_mimetypes.best == "application/json":
        # TODO: Use marshmallow
        #
        # TODO: If we change this to return something expected by TomSelect
        # options that is the same as a select field option then we can reuse
        # the render logic, e.g. {"id": id, "value": value}
        return jsonify([dict(name=t.name, id=t.id, author=t.author) for t in taxa])

    count = g.db.scalar(select(func.count("*")).select_from(stmt))
    paginator = Paginator.create(page, page_size, len(taxa), count)

    return render_template(
        "taxon/list.html",
        taxa=taxa,
        form=form,
        page=page,
        table_columns=list_table_columns,
        paginator=paginator,
    )


@bp.route("/create", methods=["GET", "POST"])
@login_required
def create():
    form = forms.CreateTaxonForm(organization_id=current_organization.id)
    if form.validate_on_submit():
        taxon = Taxon.create_from_form(form)
        create_taxon_activity(
            action=TaxonEventAction.Created, taxon=taxon, user=current_user
        )
        return redirect(url_for("taxon.detail", taxon_id=taxon.id))

    return render_template("taxon/create.html", form=form)


@bp.route("/<int:taxon_id>", methods=["GET", "POST"])
@login_required
def detail(taxon_id):
    taxon = resource.get_or_404(Taxon, taxon_id, options=joinedload(Taxon.parent))
    form = forms.EditTaxonForm(obj=taxon)
    if taxon.parent_id is not None:
        form.parent_id.choices = [
            (taxon.parent_id, f"{taxon.parent.name} {taxon.parent.author}")
        ]

    if form.validate_on_submit():
        taxon.save_form(form)
        create_taxon_activity(
            action=TaxonEventAction.Updated, taxon=taxon, user=current_user
        )
        return redirect(url_for("taxon.detail", taxon_id=taxon.id))

    return render_template("taxon/detail.html", taxon=taxon, form=form)
