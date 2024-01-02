from flask_wtf import FlaskForm
from wtforms import (
    SelectField,
    StringField,
)
from wtforms.validators import DataRequired


class CreateOrganizationForm(FlaskForm):
    name = StringField("Name", validators=[DataRequired()])
    short_name = StringField("Short name", validators=[])
    abbreviation = StringField("Abbreviation", validators=[])


EditOrganizationForm = CreateOrganizationForm


class OrganizationSettingsForm(FlaskForm):
    # TODO: validate format
    accession_format = StringField("Accession format")
    accession_item_separator = SelectField(
        "Accession item separator", choices=[".", "-", "/", ":"]
    )
