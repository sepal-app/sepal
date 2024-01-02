from flask import Blueprint, escape, g, render_template, url_for
from flask_login import login_required
from sqlalchemy import select
from sqlalchemy.orm import joinedload
from toolz.itertoolz import get

import sepal.utils.html as html
from sepal.accession.lib import AccessionEventAction
from sepal.activity.models import Activity, ActivityType
from sepal.item.lib import AccessionItemEventAction
from sepal.location.lib import LocationEventAction
from sepal.media.lib import MediaEventAction
from sepal.organization.lib import OrganizationEventAction, current_organization
from sepal.taxon.lib import TaxonEventAction

blueprint = Blueprint("activity", __name__, template_folder="templates")
bp = blueprint


def format_description(activity):
    user = activity.user.name if activity.user.name else activity.user.email
    if activity.type == ActivityType.TaxonEvent:
        taxon_id, taxon_name = get(["taxon_id", "taxon_name"], activity.payload)
        taxon_anchor_tag = html.tag(
            "a",
            {"html": url_for("taxon.detail", taxon_id=taxon_id)},
            escape(taxon_name),
        )
        match activity.payload.get("action"):  # noqa
            case TaxonEventAction.Created:
                return f"{user} added the taxon {taxon_anchor_tag}"
            case TaxonEventAction.Deleted:
                return f"{user} deleted the taxon {taxon_anchor_tag}"
            case TaxonEventAction.Updated:
                return f"{user} updated the taxon {taxon_anchor_tag}"
            case _:
                return "Unknown taxon activity"
    if activity.type == ActivityType.AccessionEvent:
        accession_id, accession_code, taxon_name = get(
            ["accession_id", "accession_code", "taxon_name"], activity.payload
        )
        accession_anchor_tag = html.tag(
            "a",
            {"html": url_for("accession.detail", accession_id=accession_id)},
            escape(accession_code),
        )
        match activity.payload.get("action"):
            case AccessionEventAction.Created:
                return f"{user} added the accession {accession_anchor_tag}"
            case AccessionEventAction.Deleted:
                return f"{user} deleted the accession {accession_anchor_tag}"
            case AccessionEventAction.Updated:
                return f"{user} updated the accession {accession_anchor_tag}"
            case _:
                return "Unknown accession activity"
    if activity.type == ActivityType.AccessionItemEvent:
        item_id, item_code, accession_code = get(
            ["accession_item_id", "accession_item_code", "accession_code"],
            activity.payload,
        )
        item_anchor_tag = html.tag(
            "a",
            {"href": url_for("item.detail", accession_item_id=item_id)},
            escape(".".join([accession_code, item_code])),
        )
        match activity.payload.get("action"):
            case AccessionItemEventAction.Created:
                return f"{user} added the accession item {item_anchor_tag}"
            case AccessionItemEventAction.Deleted:
                return f"{user} deleted the accession itme {item_anchor_tag}"
            case AccessionItemEventAction.Updated:
                return f"{user} updated the accession item {item_anchor_tag}"
            case _:
                return "Unknown location activity"
    if activity.type == ActivityType.LocationEvent:
        location_id, location_name, location_code = get(
            ["location_id", "location_name", "location_code"], activity.payload
        )
        location_anchor_tag = html.tag(
            "a",
            {"href": url_for("location.detail", location_id=location_id)},
            escape(location_name),
        )
        match activity.payload.get("action"):
            case LocationEventAction.Created:
                return f"{user} added the location {location_anchor_tag}"
            case LocationEventAction.Deleted:
                return f"{user} deleted the location {location_anchor_tag}"
            case LocationEventAction.Updated:
                return f"{user} updated the location {location_anchor_tag}"
            case _:
                return "Unknown location activity"
    if activity.type == ActivityType.OrganizationEvent:
        print(f"payload: {activity.payload}")
        organization_id, organization_name = get(
            ["organization_id", "organization_name"], activity.payload
        )
        print(f"organization id: {organization_id}")
        print(f"organization name: {organization_name}")
        organization_anchor_tag = html.tag(
            "a",
            {"html": url_for("organization.detail", id=organization_id)},
            escape(organization_name),
        )
        match activity.payload.get("action"):
            case OrganizationEventAction.Created:
                return f"{user} added the organization {organization_anchor_tag}"
            case OrganizationEventAction.Deleted:
                return f"{user} deleted the organization {organization_anchor_tag}"
            case OrganizationEventAction.Updated:
                return f"{user} updated the organization {organization_anchor_tag}"
            case _:
                return "Unknown organization activity"
    if activity.type == ActivityType.MediaEvent:
        media_id, media_name = get(["media_id", "media_title"], activity.payload)
        media_anchor_tag = html.tag(
            "a",
            {"html": url_for("media.detail", media_id=media_id)},
            escape(media_name),
        )
        match activity.payload.get("action"):
            case MediaEventAction.Created:
                return f"{user} added the media {media_anchor_tag}"
            case MediaEventAction.Deleted:
                return f"{user} deleted the media {media_anchor_tag}"
            case MediaEventAction.Updated:
                return f"{user} updated the media {media_anchor_tag}"
            case _:
                return "Unknown media activity"
    else:
        return "Unknown activity type"


@bp.route("")
@login_required
def list():
    stmt = (
        select(Activity)
        .where(Activity.organization_id == current_organization.id)
        .options(joinedload(Activity.user))
        .limit(20)
        .order_by(Activity.created_at.desc())
    )
    activity = g.db.scalars(
        stmt
        # stmt.options(joinedload(Activity.user)).order_by(Activity.created_at.desc())
    ).all()

    descriptions = {a.id: format_description(a) for a in activity}
    print(activity)

    return render_template(
        "activity/list.html", activity=activity, descriptions=descriptions
    )
