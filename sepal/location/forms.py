from flask_wtf import FlaskForm
from wtforms import HiddenField, IntegerField, SelectField, StringField, TextAreaField
from wtforms.validators import DataRequired, NumberRange


# TODO: the list form can be made generic
class ListLocationsForm(FlaskForm):
    q = StringField()
    page = IntegerField(default=1, validators=[NumberRange(min=1)])
    page_size = SelectField(
        default=25,
        choices=["25", "50", "100"],
        coerce=int,
        validators=[NumberRange(min=1, max=1000)],
    )


class CreateLocationForm(FlaskForm):
    name = StringField("Name", validators=[DataRequired()])
    code = StringField("Code", validators=[])
    description = TextAreaField("Description", validators=[])
    organization_id = HiddenField()


EditLocationForm = CreateLocationForm
# class EditLocationForm(FlaskForm):
#     name = StringField("Name", validators=[DataRequired()])
#     code = StringField("Author", validators=[])
#     organization_id = HiddenField()
