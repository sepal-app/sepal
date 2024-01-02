from flask_wtf import FlaskForm
from wtforms import HiddenField, SelectField, StringField
from wtforms.validators import DataRequired

import sepal.forms as forms


class CreateAccessionItemForm(FlaskForm):
    code = StringField("Code", validators=[DataRequired()])
    location_id = SelectField(
        "Location",
        choices=[],
        filters=[forms.empty_str_to_none_filter],
        coerce=forms.coerce_int_or_none,
        validate_choice=False,
    )
    accession_id = SelectField(
        "Accession",
        choices=[],
        filters=[forms.empty_str_to_none_filter],
        coerce=forms.coerce_int_or_none,
        validate_choice=False,
    )
    organization_id = HiddenField()


EditAccessionItemForm = CreateAccessionItemForm
