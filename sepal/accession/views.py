from dataclasses import dataclass
from typing import Any, Callable

from flask import (
    Blueprint,
    Response,
    g,
    jsonify,
    redirect,
    render_template,
    request,
    url_for,
)
from flask_login import current_user, login_required
from sqlalchemy import func, select
from sqlalchemy.orm import joinedload

import sepal.accession.forms as forms
import sepal.utils.html as html
import sepal.utils.resource as resource
from sepal.accession.lib import (
    AccessionEventAction,
    create_accession_activity,
    get_most_recent_accession,
)
from sepal.accession.models import Accession
from sepal.forms import ListForm
from sepal.organization.lib import current_organization
from sepal.ui.paginator import Paginator

blueprint = Blueprint("accession", __name__, template_folder="templates")
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
        lambda a: html.tag(
            "a", {"href": url_for("accession.detail", accession_id=a.id)}, a.code
        ),
    ),
    TableColumn(
        "Taxon",
        lambda a: html.tag(
            "a",
            {"href": url_for("taxon.detail", taxon_id=a.taxon_id)},
            a.taxon.name,
        ),
    ),
]


@bp.route("")
@login_required
def list() -> Response:
    form = ListForm(request.args)
    page = form.page.data
    page_size = form.page_size.data
    limit = page_size
    offset = limit * (page - 1)

    stmt = select(Accession).where(Accession.organization_id == current_organization.id)

    if form.data.get("q", None) is not None:
        stmt = stmt.where(
            Accession.code.ilike(
                f"{form.data['q']}%",
            )
        )

    accessions = g.db.scalars(
        stmt.options(joinedload(Accession.taxon))
        .limit(limit)
        .offset(offset)
        .order_by("code")
    ).all()
    count = g.db.scalar(select(func.count("*")).select_from(stmt))

    # JSON response for autocomplete inputs
    if request.accept_mimetypes.best == "application/json":
        # TODO: Use marshmallow
        return jsonify(
            [
                dict(code=a.code, id=a.id, taxon=dict(id=a.taxon.id, name=a.taxon.name))
                for a in accessions
            ]
        )

    paginator = Paginator.create(page, page_size, len(accessions), count)

    return render_template(
        "accession/list.html",
        accessions=accessions,
        form=form,
        page=page,
        table_columns=list_table_columns,
        paginator=paginator,
    )


@bp.route("/create", methods=["GET", "POST"])
@login_required
def create() -> Response:
    last_accession = get_most_recent_accession(
        g.db,
        organization_id=current_organization.id,
        options=joinedload(Accession.taxon),
    )
    form = forms.CreateAccessionForm(organization_id=current_organization.id)
    if form.validate_on_submit():
        accession = Accession.create_from_form(form)
        create_accession_activity(
            action=AccessionEventAction.Created, accession=accession, user=current_user
        )
        return redirect(url_for("accession.detail", accession_id=accession.id))

    # TODO: prepopulate with next accession according to org settings
    return render_template(
        "accession/create.html", form=form, last_accession=last_accession
    )


@bp.route("/<int:accession_id>", methods=["GET", "POST"])
@login_required
def detail(accession_id) -> Response:
    accession = resource.get(
        Accession, accession_id, options=joinedload(Accession.taxon)
    )

    form = forms.EditAccessionForm(obj=accession)
    if form.validate_on_submit():
        accession.save_form(form)
        create_accession_activity(
            action=AccessionEventAction.Updated, accession=accession, user=current_user
        )
        return redirect(url_for("accession.detail", accession_id=accession.id))

    if accession.taxon_id is not None:
        # form.taxon_id.choices = [(accession.taxon_id, accession.taxon.name)]
        form.taxon_id.choices = [
            (accession.taxon_id, f"{accession.taxon.name} {accession.taxon.author}")
        ]

    return render_template("accession/detail.html", accession=accession, form=form)
