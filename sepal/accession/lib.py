import enum
import re
from dataclasses import asdict, dataclass
from datetime import datetime
from typing import Optional

import sqlalchemy as sa
from sqlalchemy.orm import Session
from sqlalchemy.sql.base import ExecutableOption

from sepal.accession.models import Accession
from sepal.activity.models import Activity, ActivityType


class AccessionEventAction(enum.StrEnum):
    Created = "created"
    Deleted = "deleted"
    Updated = "updated"


@dataclass
class AccessionEventPayload:
    action: AccessionEventAction
    accession_id: int
    accession_code: str
    taxon_id: int
    taxon_name: str


def create_accession_activity(
    action: AccessionEventAction,
    accession: "Accession",
    user: "User",
    session: Optional[Session] = None,
) -> Activity:
    payload = AccessionEventPayload(
        action=action,
        accession_code=accession.code,
        accession_id=accession.id,
        taxon_id=accession.taxon.id,
        taxon_name=accession.taxon.name,
    )
    return Activity.create(
        payload=asdict(payload),
        user=user,
        organization=accession.organization,
        type=ActivityType.AccessionEvent,
        session=session,
    )


def get_most_recent_accession(
    session: Session,
    organization_id: str,
    pattern: Optional[str] = None,
    options: Optional[ExecutableOption] = None,
) -> Optional[Accession]:
    """Get the most recent accession matching the pattern."""
    q = (
        sa.select(Accession)
        .where(
            Accession.organization_id == organization_id,
        )
        .order_by(Accession.code.desc(), Accession.created_at.desc())
    )

    if pattern:
        q = q.where(Accession.code.regexp_match(pattern))

    if options:
        # TODO: can these be added by the caller
        q = q.options(options)

    q = q.order_by(Accession.code.desc(), Accession.created_at.desc())

    return session.scalars(q).first()


def increment_accession_code(accession: Accession, pattern: str) -> Optional[str]:
    """Get the most recent accession matching the pattern."""
    rx = regex_from_accession_code_pattern(pattern)
    m = re.match(rx, accession.code)
    if m is None:
        return None

    try:
        number = m.group("number")
        next = str(int(number) + 1)
        start, end = m.span("number")
        return accession.code[:start] + next.zfill(len(number)) + accession.code[end:]
    except IndexError:
        return None


def format_accession_code(pattern: str, dt: datetime, base_number: int) -> str:
    formatting = {
        "Y": dt.year,
        "y": str(dt.year)[2:4],
        "M": str(dt.month).zfill(2),
        "m": dt.month,
        "D": str(dt.day).zfill(2),
        "d": dt.day,
        "N": base_number,
        "NN": str(base_number).zfill(2),
        "NNN": str(base_number).zfill(3),
        "NNNN": str(base_number).zfill(4),
        "NNNNN": str(base_number).zfill(5),
        "NNNNNN": str(base_number).zfill(6),
        "NNNNNNN": str(base_number).zfill(7),
        "NNNNNNNN": str(base_number).zfill(8),
    }
    return pattern.format(**formatting)


def regex_from_accession_code_pattern(pattern: str) -> str:
    """Return a pattern to use for an HTML text input."""
    rx = (
        pattern.replace("{Y}", r"(?P<year>\d{4})")
        .replace("{y}", r"(?P<year>\d{2})")
        .replace("{M}", r"(?P<month>\d{2})")
        .replace("{m}", r"(?P<month>\d{1,2}?)")
        .replace("{D}", r"(?P<day>\d{2})")
        .replace("{d}", r"(?P<day>\d{1,2}?)")
        .replace("{N}", r"(?P<number>\d{1}?)")
        .replace("{NN}", r"(?P<number>\d{2}?)")
        .replace("{NNN}", r"(?P<number>\d{3}?)")
        .replace("{NNNN}", r"(?P<number>\d{4}?)")
        .replace("{NNNNN}", r"(?P<number>\d{5}?)")
        .replace("{NNNNNN}", r"(?P<number>\d{6}?)")
        .replace("{NNNNNNN}", r"(?P<number>\d{7}?)")
        .replace("{NNNNNNNN}", r"(?P<number>\d{8}?)")
    )
    return f"^{rx}$"


# def html_input_pattern_from_accession_code_pattern(pattern: str) -> str:
#     """Return a pattern to use for an HTML text input."""
#     # TODO:
#     rx = (
#         pattern.replace("{Y}", r"\d{4}")
#         .replace("{y}", r"\d{2}")
#         .replace("{M}", r"\d{2}")
#         .replace("{m}", r"\d{1,2}?")
#         .replace("{D}", r"\d{2}")
#         .replace("{d}", r"\d{1,2}?")
#     )
#     return f"^{rx}$"
