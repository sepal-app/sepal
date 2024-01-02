import re
from datetime import datetime, timedelta, timezone

import sepal.accession.lib as lib


def test_create_accession_activity(session, accession, organization_user):
    user = organization_user.user
    organization = organization_user.organization
    activity = lib.create_accession_activity(
        lib.AccessionEventAction.Created, accession, user, session=session
    )
    assert activity is not None
    assert activity.organization.id == organization.id


def test_get_most_recent_accession(session, organization, accession_factory):
    now = datetime.now(timezone.utc)
    _acc1 = accession_factory(
        code=f"{now.year}.0001",
        organization=organization,
        created_at=now - timedelta(minutes=2),
    )
    _acc2 = accession_factory(
        code=f"{now.year}.0002",
        organization=organization,
        created_at=now - timedelta(minutes=1),
    )
    acc3 = accession_factory(
        code=f"{now.year}.0003",
        organization=organization,
        created_at=now - timedelta(minutes=2),
    )
    # returns 0003 even though it was created before 0002
    acc = lib.get_most_recent_accession(session, organization.id, r"^\d{4}.\d{4}$")
    assert acc is not None
    assert acc.code == acc3.code


def test_increment_accession_code(session, organization, accession_factory):
    now = datetime.now(timezone.utc)
    acc = accession_factory(code=f"{now.year}.0001", organization=organization)
    next = lib.increment_accession_code(acc, "{Y}.{NNNN}")
    assert next == f"{now.year}.0002"


def test_regex_from_accession_code_pattern():
    matches = lambda rx, s: re.match(lib.regex_from_accession_code_pattern(rx), s)
    assert matches("{Y}", "2023")
    assert matches("{Y}", "23") is None
    assert matches("{Y}", "12345") is None
    assert matches("{y}", "23")
    assert matches("{y}", "2") is None
    assert matches("{M}", "01")
    assert matches("{M}", "1") is None
    assert matches("{m}", "01")
    assert matches("{m}", "1")
    assert matches("{D}", "23")
    assert matches("{D}", "3") is None
    assert matches("{d}", "23")
    assert matches("{d}", "2")


def test_gen_next_accession(session, organization_user):
    dt = datetime.now(
        timezone.utc
    )  # TODO: needs to be in the data of the organizations timezone
    assert lib.format_accession_code("{Y}", dt.replace(year=1970), 1) == "1970"
    assert lib.format_accession_code("{y}", dt.replace(year=1970), 1) == "70"
    assert lib.format_accession_code("{M}", dt.replace(month=1), 1) == "01"
    assert lib.format_accession_code("{M}", dt.replace(month=12), 1) == "12"
    assert lib.format_accession_code("{m}", dt.replace(month=1), 1) == "1"
    assert lib.format_accession_code("{m}", dt.replace(month=12), 1) == "12"
    assert lib.format_accession_code("{D}", dt.replace(day=1), 1) == "01"
    assert lib.format_accession_code("{D}", dt.replace(day=28), 1) == "28"
    assert lib.format_accession_code("{d}", dt.replace(day=1), 1) == "1"
    assert lib.format_accession_code("{d}", dt.replace(day=28), 1) == "28"
    assert lib.format_accession_code("{N}", dt, 1) == "1"
    assert lib.format_accession_code("{NN}", dt, 1) == "01"
    assert lib.format_accession_code("{NNN}", dt, 1) == "001"
    assert lib.format_accession_code("{NNNN}", dt, 1) == "0001"
    assert lib.format_accession_code("{NNNNN}", dt, 1) == "00001"
    assert lib.format_accession_code("{NNNNNN}", dt, 1) == "000001"
    assert lib.format_accession_code("{NNNNNNN}", dt, 1) == "0000001"
    assert lib.format_accession_code("{NNNNNNNN}", dt, 1) == "00000001"
    assert lib.format_accession_code("{NNNNNNNN}", dt, 1) == "00000001"
