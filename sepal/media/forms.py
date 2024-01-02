from flask_wtf import FlaskForm
from wtforms import IntegerField, StringField


class CreateUploadForm(FlaskForm):
    id = StringField()
    name = StringField()
    extension = StringField()
    media_type = StringField()
    size = IntegerField()


class UploadForm(FlaskForm):
    key = StringField()
    name = StringField()
    aws_access_key_id = StringField(name="AWSAccessKeyId")
    policy = StringField()
    signature = StringField()
    content_type = StringField(name="Content-Type")
    organization_id = StringField()
    size = IntegerField()


class MediaListForm(FlaskForm):
    q = StringField()
    offset = IntegerField(default=0)
    order_by = StringField()
