import secrets
from datetime import datetime, timedelta

from flask import (
    Blueprint,
    flash,
    g,
    redirect,
    render_template,
    request,
    url_for,
)
from flask_login import current_user, login_user, logout_user
from sendgrid import SendGridAPIClient
from sendgrid.helpers.mail import (
    ClickTracking,
    Mail,
    OpenTracking,
    TrackingSettings,
)
from sqlalchemy import select, true

import sepal.auth.forms as forms
import sepal.s3
from sepal.auth.models import PasswordResetToken, User
from sepal.extensions import login_manager
from sepal.organization.lib import clear_current_organization
from sepal.settings import settings

login_manager.login_view = "auth.login"
login_manager.next_url = "root"


@login_manager.user_loader
def load_user(user_id):
    return User.get_by_id(user_id)


blueprint = Blueprint(
    "auth",
    __name__,
    template_folder="templates",
    static_folder="static",
    # static_url_path needs to be "/" since this blueprint is mounted at /
    static_url_path="/",  #
)
bp = blueprint


@bp.route("/register", methods=["GET", "POST"])
def register():
    form = forms.RegisterForm(invitation=request.args.get("invitation"))
    if form.validate_on_submit():
        stmt = select(User).where(User.email == form.email.data).exists()
        existing_user = g.db.scalar(select(true()).where(stmt))
        if existing_user is True:
            flash(f"A user with the email {form.email.data} already exists.", "error")
            return redirect(url_for("auth.register"))

        user = User()
        with g.db.begin_nested():
            form.populate_obj(user)
            g.db.add(user)

        g.db.merge(user)
        login_user(user)

        next = request.args.get("next", url_for("public.root"))
        return redirect(next)

    return render_template("auth/register.html", form=form)


@bp.route("/login", methods=["GET", "POST"])
def login():
    form = forms.LoginForm(invitation=request.args.get("invitation"))
    if form.validate_on_submit():
        stmt = select(User).where(
            User.email == form.email.data, User.password == form.password.data
        )
        user = g.db.scalar(stmt)
        if user is None:
            flash("Invalid login", "error")
            return redirect(url_for("auth.login"))

        login_user(user)
        next = request.args.get("next", url_for("public.root"))
        return redirect(next)

    return render_template("auth/login.html", form=form)


@bp.route("/logout")
def logout():
    logout_user()
    clear_current_organization()
    return redirect(url_for("auth.login"))


def send_password_reset_email(user: User) -> None:
    token = PasswordResetToken.create(user=user)

    mail = Mail(
        from_email="Sepal Support <support@sepal.app>",
        to_emails=user.email,
        subject="Sepal - Reset Password",
        html_content=render_template(
            "auth/reset_password_email.html", token=token.token
        ),
        plain_text_content=render_template(
            "auth/reset_password_email.txt", token=token.token
        ),
    )
    try:
        sg = SendGridAPIClient(api_key=settings.SENDGRID_API_KEY)
        #  disable link tracking for password reset emails
        tracking_settings = TrackingSettings()
        tracking_settings.click_tracking = ClickTracking(False, False)
        tracking_settings.open_tracking = OpenTracking(False)
        mail.tracking_settings = tracking_settings
        response = sg.client.mail.send.post(request_body=mail.get())
        # TODO: handle any errors
        print(response)
    except Exception as e:
        print(e)


@bp.route("/forgot_password", methods=["GET", "POST"])
def forgot_password():
    form = forms.ForgotPasswordForm()
    if form.validate_on_submit():
        user = User.get_by_email(email=form.data["email"])
        if user:
            send_password_reset_email(user)

        # If the user doesn't exist then we don't actually send the email.
        flash(f"An email to reset your password was sent to {form.data['email']}.")
        return redirect(url_for("auth.login"))

    return render_template("auth/forgot_password.html", form=form)


@bp.route("/reset_password", methods=["GET", "POST"])
def reset_password():
    password_reset_token = PasswordResetToken.get_by_active_token(
        request.form.get("password_reset_token", request.args.get("token")),
        expires_at=datetime.now() - timedelta(hours=1),
    )

    if password_reset_token is None:
        flash("The password reset token is expired.", "error")
        return redirect(url_for("auth.login"))

    user = password_reset_token.user
    form = forms.ResetPasswordForm(password_reset_token=password_reset_token.token)
    if form.validate_on_submit():
        user.password = form.data.get("password")
        # password_reset_token.active = False
        g.db.commit()
        flash(f"The password was reset for {user.email}")
        return redirect(url_for("auth.login"))

    return render_template(
        "auth/reset_password.html", form=form, email=password_reset_token.user.email
    )


@bp.route("/profile", methods=["GET", "POST"])
def profile():
    update_profile_form = forms.UpdateProfileForm(obj=current_user)
    action = request.form.get("action", None)
    if action == "change_email":
        return redirect(url_for("auth.profile"))
    elif action == "reset_password":
        try:
            send_password_reset_email(current_user)
            flash(f"A password reset email has been sent to {current_user.email}")
        except Exception as e:
            print(e)
            flash("Could not send password reset email.", "error")
        return redirect(url_for("auth.profile"))
    elif action == "update_profile":
        if update_profile_form.validate_on_submit():
            update_profile_form.populate_obj(current_user)
            g.db.commit()
            return redirect(url_for("auth.profile"))
        else:
            print("ERRORs")
            print(update_profile_form.errors)
    else:
        # TODO: what error?
        pass

    return render_template(
        "auth/profile.html",
        form=update_profile_form,
    )


@bp.route("/profile/avatar", methods=["POST"])
def profile_avatar():
    s3 = sepal.s3.client()
    s3_key = f"avatars/user_id={current_user.id}/{secrets.token_hex(4)}"
    # TODO: handle upload error
    s3.upload_fileobj(
        request.files["image"],
        settings.S3_MEDIA_BUCKET,
        s3_key,
        ExtraArgs={"ContentType": request.content_type},
    )
    current_user.avatar_s3_bucket = settings.S3_MEDIA_BUCKET
    current_user.avatar_s3_key = s3_key
    g.db.commit()

    # TODO: render errors
    return render_template("auth/_profile_avatar_upload_section.html")
