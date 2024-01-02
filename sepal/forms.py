from flask_wtf import FlaskForm
from wtforms import IntegerField, SelectField, StringField
from wtforms.validators import NumberRange


def empty_str_to_none_filter(v):
    if hasattr(v, "strip") and v.strip() == "":
        return None
    else:
        return v


def coerce_int_or_none(v):
    if v is None:
        return None
    if hasattr(v, "strip") and v.strip() == "":
        return None
    else:
        return int(v)


class ListForm(FlaskForm):
    q = StringField()
    page = IntegerField(default=1, validators=[NumberRange(min=1)])
    page_size = SelectField(
        default=25,
        choices=["25", "50", "100"],
        coerce=int,
        validators=[NumberRange(min=1, max=1000)],
    )
