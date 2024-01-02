from flask_wtf import FlaskForm
from wtforms import HiddenField, SelectField, StringField
from wtforms.validators import DataRequired

import sepal.forms as forms


class CreateAccessionForm(FlaskForm):
    code = StringField("Code", validators=[DataRequired()])
    taxon_id = SelectField(
        "Taxon",
        choices=[],
        filters=[forms.empty_str_to_none_filter],
        coerce=forms.coerce_int_or_none,
        validate_choice=False,
    )
    organization_id = HiddenField()


EditAccessionForm = CreateAccessionForm
# class EditAccessionForm(FlaskForm):
#     name = StringField("Name", validators=[DataRequired()])
#     author = StringField("Author", validators=[])
#     rank = SelectField(
#         "Rank",
#         choices=[(rank.value, rank.name) for rank in AccessionRank],
#         coerce=AccessionRank,
#     )
#     parent_id = StringField("Parent", filters=[empty_str_to_none_filter])
#     organization_id = HiddenField()
