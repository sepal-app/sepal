import logging
from datetime import datetime
from typing import Optional
from urllib.parse import urlencode

from botocore.exceptions import ClientError
from sqlalchemy import DateTime, ForeignKey, Text, UniqueConstraint, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

import sepal.db as db
import sepal.s3
from sepal.auth.models import User
from sepal.organization.models import Organization
from sepal.settings import settings

# TODO: How do we associate media to things like taxa and accessions. We
# could have tables like taxon_media(media_id, taxon_id) or we cold have
# something like media_resource(media_id, resource_id, resource_type='taxon')

THUMBNAIL_URL_PARAMS = urlencode({"max-h": 300, "max-w": 300, "fit": "crop"})
PREVIEW_URL_PARAMS = urlencode({"max-h": 2048, "max-w": 2048, "fit": "clip"})


class Media(db.Model, db.IdMixin):
    s3_bucket: Mapped[str] = mapped_column(Text)
    s3_key: Mapped[str] = mapped_column(Text)

    title: Mapped[Optional[str]] = mapped_column(Text)
    description: Mapped[Optional[str]] = mapped_column(Text)

    size_in_bytes: Mapped[int] = mapped_column()

    media_type: Mapped[str] = mapped_column(Text)

    organization_id: Mapped[int] = mapped_column(ForeignKey("organization.id"))
    organization: Mapped[Organization] = relationship()

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )
    _created_by: Mapped[int] = mapped_column("created_by", ForeignKey("user.id"))
    created_by: Mapped[User] = relationship()

    __table_args__ = (UniqueConstraint(organization_id, s3_bucket, s3_key),)

    def is_image(self):
        return self.media_type in [
            "image/png",
            "image/jpeg",
            "image/gif",
            "image/webp",
            "image/bmp",
            "image/tiff",
        ]

    def url(self):
        s3 = sepal.s3.client()
        expiration = 60 * 60 * 24  # 1 day
        try:
            return s3.generate_presigned_url(
                "get_object",
                Params={"Bucket": self.s3_bucket, "Key": self.s3_key},
                ExpiresIn=expiration,
            )
        except ClientError as e:
            logging.error(e)
            return None

    def preview_url(self):
        return (
            settings.MEDIA_BASE_URL_TEMPLATE.format(
                s3_key=self.s3_key, s3_bucket=self.s3_bucket
            )
            + f"?{PREVIEW_URL_PARAMS}"
        )

    def thumbnail_url(self):
        return (
            settings.MEDIA_BASE_URL_TEMPLATE.format(
                s3_key=self.s3_key, s3_bucket=self.s3_bucket
            )
            + f"?{THUMBNAIL_URL_PARAMS}"
        )
