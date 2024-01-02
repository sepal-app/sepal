import uuid

from flask import Blueprint, g, render_template, request
from flask_login import current_user, login_required
from sqlalchemy import select

import sepal.db as db
import sepal.media.forms as forms
import sepal.s3
from sepal.media.lib import MediaEventAction, create_media_activity
from sepal.media.models import Media
from sepal.organization.lib import current_organization
from sepal.settings import settings

blueprint = Blueprint(
    "media",
    __name__,
    template_folder="templates",
    static_folder="static",
)
bp = blueprint

page_size = 50


@bp.route("", methods=["GET", "POST"])
@login_required
def index():
    form = forms.MediaListForm(request.args)
    stmt = select(Media).where(Media.organization_id == current_organization.id)
    count = db.count(stmt)

    media = g.db.scalars(
        stmt.limit(page_size).offset(form.offset.data).order_by(Media.created_at.desc())
    ).all()

    next_page_offset = (
        form.offset.data + page_size
        if form.offset.data > 0
        else form.offset.data + page_size + 1
    )

    if next_page_offset > count:
        next_page_offset = None

    if "HX-Request" in request.headers:
        return "".join(
            render_template("media/_media_item.html", m=m) for m in media
        ) + render_template(
            "media/_next_page_button.html", next_page_offset=next_page_offset
        )

    return render_template(
        "media/index.html",
        media=media,
        upload_endpoint=settings.MEDIA_UPLOAD_ENDPOINT,
        create_upload_form=forms.CreateUploadForm(),
        next_page_offset=next_page_offset,
    )


@bp.route("/create", methods=["POST"])
@login_required
def create():
    form = forms.UploadForm()
    if form.validate_on_submit():
        media = Media.create(
            s3_bucket=settings.S3_MEDIA_BUCKET,
            s3_key=form.key.data,
            organization_id=form.organization_id.data,
            title=form.name.data,
            created_by=current_user,
            media_type=form.content_type.data,
            size_in_bytes=form.size.data,
        )
        create_media_activity(
            action=MediaEventAction.Created, media=media, user=current_user
        )
        return render_template("media/_media_item.html", m=media)
    else:
        # TODO: handle error
        print("INVALID form")
        print(form.errors)
        return ""


@bp.route("/create_upload_form", methods=["POST"])
@login_required
def create_upload_form():
    form = forms.CreateUploadForm(organiation_id=current_organization.id)
    if form.validate_on_submit():
        ext = form.extension.data
        key = f"organization_id={current_organization.id}/{uuid.uuid4()}.{ext}"
        r = sepal.s3.presign_upload_request(str(key), form.media_type.data)
        upload_form = forms.UploadForm(
            key=r["fields"]["key"],
            name=form.name.data,
            aws_access_key_id=r["fields"]["AWSAccessKeyId"],
            policy=r["fields"]["policy"],
            signature=r["fields"]["signature"],
            content_type=r["fields"]["Content-Type"],
            organization_id=current_organization.id,
            size=form.size.data,
        )
        return render_template(
            "media/_upload_form.html",
            form=upload_form,
            file_id=form.id.data,
        )

    print(form.errors)
    # TODO handle error
    return ""


@bp.route("/<int:media_id>", methods=["GET", "POST"])
@login_required
def detail(media_id):
    # TODO: create a media preview page
    return ""
