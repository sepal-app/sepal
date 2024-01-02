import logging
import os
import sys
from http.client import HTTPException
from typing import Any

from flask import Flask, render_template

import sepal.accession.views
import sepal.activity.views
import sepal.auth.models
import sepal.auth.views
import sepal.dashboard.views
import sepal.db as db
import sepal.invitation
import sepal.item.views
import sepal.location.views
import sepal.media.views
import sepal.organization.views
import sepal.permissions as permissions
import sepal.public.views
import sepal.taxon.views
from sepal.extensions import (
    csrf_protect,
    debug_toolbar,
    flask_static_digest,
    login_manager,
    migrate,
)
from sepal.organization.lib import current_organization

# import sepal.organization.models
# import sepal.taxon.models


def create_app(config_object: str = "sepal.settings.settings") -> Flask:
    app = Flask(
        __name__.split(".")[0], static_folder="static", static_url_path="/static"
    )
    app.config.from_object(config_object)
    register_extensions(app)
    register_blueprints(app)
    # register_errorhandlers(app)
    register_shellcontext(app)
    # # register_commands(app)
    configure_logger(app)
    return app


def register_extensions(app: Flask) -> None:
    """Register Flask extensions."""
    db.init_app(app)
    debug_toolbar.init_app(app)
    login_manager.init_app(app)
    migrate.init_app(app, db)
    permissions.init_app(app)
    # TODO: Use Flask-SeaSurf rather than CSRFPrtect from flask.wtf
    csrf_protect.init_app(app)
    flask_static_digest.init_app(app)


def register_blueprints(app: Flask) -> None:
    """Register Flask blueprints."""
    app.register_blueprint(sepal.accession.views.blueprint, url_prefix="/accession")
    app.register_blueprint(sepal.activity.views.blueprint, url_prefix="/activity")
    app.register_blueprint(sepal.auth.views.blueprint, url_prefix="/")
    app.register_blueprint(sepal.dashboard.views.blueprint, url_prefix="/dashboard")
    app.register_blueprint(sepal.invitation.blueprint, url_prefix="/invitation")
    app.register_blueprint(sepal.item.views.blueprint, url_prefix="/item")
    app.register_blueprint(sepal.location.views.blueprint, url_prefix="/location")
    app.register_blueprint(sepal.media.views.blueprint, url_prefix="/media")
    app.register_blueprint(sepal.organization.views.blueprint, url_prefix="/org")
    app.register_blueprint(sepal.public.views.blueprint, url_prefix="/")
    app.register_blueprint(sepal.taxon.views.blueprint, url_prefix="/taxon")

    # Make current_organization available globally in templates
    app.context_processor(lambda: dict(current_organization=current_organization))


# def register_assets(app: Flask) -> None:
#     # TODO: move this to the view.py?
#     build_path = Path("./build")
#     bundle = Bundle(
#         "auth/jose-fontano-WVAVwZ0nkSw-unsplash_1080x1620.jpg",
#         output=build_path / "out.jpg",
#     )
#     assets.register("_all", bundle)


def register_errorhandlers(app: Flask) -> None:
    """Register error handlers."""

    def render_error(error: HTTPException) -> tuple[str, int]:
        """Render error template."""
        # If a HTTPException, pull the `code` attribute; default to 500
        error_code = getattr(error, "code", 500)
        return render_template(f"{error_code}.html"), error_code

    for errcode in [401, 404, 500]:
        app.errorhandler(errcode)(render_error)


def register_shellcontext(app: Flask) -> None:
    """Register shell context objects."""

    def shell_context() -> dict[str, Any]:
        """Shell context objects."""
        return {"db": db, "User": sepal.auth.models.User}

    app.shell_context_processor(shell_context)


# def register_commands(app):
#     """Register Click commands."""
#     app.cli.add_command(commands.test)
#     app.cli.add_command(commands.lint)


def configure_logger(app: Flask) -> None:
    """Configure loggers."""
    handler = logging.StreamHandler(sys.stdout)
    if not app.logger.handlers:
        app.logger.addHandler(handler)


def run() -> None:
    """Run the development mode server with livereload."""

    from livereload import Server

    app = create_app()
    server = Server(app)

    def ignore(f):
        return ".DS_Store" in f

    # limit the paths that we're watching to limit CPU usage
    module_path = os.path.dirname(__file__)
    for dir, subdirs, files in os.walk(module_path):
        if os.path.isdir(dir):
            if "static" in dir or "templates" in dir:
                for f in files:
                    f = os.path.join(dir, f)
                    if not ignore(f):
                        server.watch(f)

    server.serve(port=os.environ.get("PORT", 5000))


if __name__ == "__main__":
    run()
