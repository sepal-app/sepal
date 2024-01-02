from flask_wtf import FlaskForm
from wtforms import HiddenField, SelectField, StringField
from wtforms.validators import DataRequired

import sepal.forms as forms
from sepal.taxon.models import TaxonRank


class CreateTaxonForm(FlaskForm):
    name = StringField("Name", validators=[DataRequired()])
    author = StringField("Author", validators=[])
    rank = SelectField(
        "Rank",
        choices=TaxonRank.choices(),
        coerce=TaxonRank,
    )
    parent_id = SelectField(
        "Parent",
        choices=[],
        filters=[forms.empty_str_to_none_filter],
        coerce=forms.coerce_int_or_none,
        validate_choice=False,
    )
    organization_id = HiddenField()


EditTaxonForm = CreateTaxonForm
