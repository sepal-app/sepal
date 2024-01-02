from flask_wtf import FlaskForm
from wtforms import EmailField, HiddenField, PasswordField, StringField
from wtforms.validators import DataRequired, EqualTo


class RegisterForm(FlaskForm):
    email = EmailField("Email", validators=[DataRequired()])
    password = PasswordField("Password", validators=[DataRequired()])
    confirm_password = PasswordField(
        "Confirm password", validators=[DataRequired(), EqualTo("password")]
    )


class LoginForm(FlaskForm):
    email = EmailField("Email", validators=[DataRequired()])
    password = PasswordField("Password", validators=[DataRequired()])


class UpdateProfileForm(FlaskForm):
    name = StringField("Name", validators=[DataRequired()])


class ForgotPasswordForm(FlaskForm):
    email = StringField("Email", validators=[DataRequired()])


class ResetPasswordForm(FlaskForm):
    password_reset_token = HiddenField(validators=[DataRequired()])
    password = PasswordField("Password", validators=[DataRequired()])
    confirm_password = PasswordField(
        "Confirm password", validators=[DataRequired(), EqualTo("password")]
    )
