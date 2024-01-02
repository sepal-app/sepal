from flask_wtf import FlaskForm
from wtforms import (
    EmailField,
    FieldList,
    FormField,
    HiddenField,
    SelectField,
)

from sepal.organization.models import OrganizationRole


# TODO: This need to go in the invitation module
class EmailForm(FlaskForm):
    email = EmailField("Email", validators=[])
    role = SelectField(
        "Role",
        choices=[(r.value, r.name) for r in OrganizationRole],
        validators=[],
        default=OrganizationRole.Read.value,
    )


class InviteUsersForm(FlaskForm):
    emails = FieldList(FormField(EmailForm), min_entries=5)
    organization_id = HiddenField()
