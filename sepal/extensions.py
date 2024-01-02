from flask_debugtoolbar import DebugToolbarExtension
from flask_login import LoginManager
from flask_migrate import Migrate
from flask_static_digest import FlaskStaticDigest
from flask_wtf.csrf import CSRFProtect

csrf_protect = CSRFProtect()
debug_toolbar = DebugToolbarExtension()
flask_static_digest = FlaskStaticDigest()
login_manager = LoginManager()
migrate = Migrate()
