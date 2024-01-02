import flask


def test_detail_settings_view(
    app,
    user_client,
    session,
    user,
    organization,
    organization_user,
):
    r = user_client.post(
        f"/org/{organization.id}/settings",
        data={"accession_format": "x", "accession_item_separator": "."},
    )
    assert r.status_code == 302, r.data
    assert r.headers["Location"] == flask.url_for(
        "organization.detail_settings", id=organization.id
    )
    session.refresh(organization)
    assert organization.settings["accession_format"] == "x"
    assert organization.settings["accession_item_separator"] == "."
