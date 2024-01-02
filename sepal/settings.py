from typing import Optional

from pydantic import BaseSettings, Field, HttpUrl, PostgresDsn


class Settings(BaseSettings):
    DATABASE_URL: PostgresDsn = Field("postgresql://postgres@localhost/sepal")
    SQLALCHEMY_ECHO: bool = False
    DEBUG_TB_INTERCEPT_REDIRECTS = False
    FLASK_DEBUG: bool = False
    S3_MEDIA_BUCKET: str = "sepal_media"
    S3_ENDPOINT_URL: HttpUrl = "https://s3.amazonaws.com"
    AWS_ACCESS_KEY_ID: str
    AWS_SECRET_ACCESS_KEY: str
    SECRET_KEY: str
    SENDGRID_API_KEY: str

    # A string template that when called with:
    # MEDIA_BASE_URL_TEMPLATE.format(s3_key=media.s3_key, s3_bucket=media.s3_bucket)
    # will return the base url for media.
    MEDIA_BASE_URL_TEMPLATE: str

    # The endpoint used
    MEDIA_UPLOAD_ENDPOINT: str

    TEMPLATES_AUTO_RELOAD: bool = True

    DISABLE_EMAIL_TRACKING: bool = False

    SESSION_PROTECTION: Optional[str] = "strong"

    INVITATION_TTL_MINUTES = 1440  # 1440 minutes = 24 hours


settings = Settings()
