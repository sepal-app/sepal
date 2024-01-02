import secrets
from datetime import datetime
from typing import Optional
from urllib.parse import urlencode

import flask
from flask_login import UserMixin
from sqlalchemy import DateTime, ForeignKey, Text, func, select
from sqlalchemy.ext.associationproxy import AssociationProxy, association_proxy
from sqlalchemy.ext.hybrid import Comparator, hybrid_property
from sqlalchemy.orm import Mapped, mapped_column, relationship

import sepal.db as db
from sepal.db import text
from sepal.settings import settings


def organization_creator(org: "Organization"):
    # avoid circular dependency
    from sepal.organization.models import OrganizationUser

    return (OrganizationUser(organization=org),)


class User(db.Model, db.IdMixin, UserMixin):
    avatar_s3_bucket: Mapped[Optional[text]] = mapped_column(Text)
    avatar_s3_key: Mapped[Optional[text]] = mapped_column(Text)

    email: Mapped[text] = mapped_column(unique=True)
    name: Mapped[text] = mapped_column(default="", server_default="")

    # deferred prevents the column being returned in select statements by
    # unless explicitly requested
    password_hashed: Mapped[text] = mapped_column(
        "password", nullable=False, deferred=True
    )
    # active = Column(Boolean, default=True, server_default=True)

    password_reset_tokens: Mapped["PasswordResetToken"] = relationship(
        cascade="all, delete-orphan", back_populates="user"
    )

    organizations: AssociationProxy[list["Organization"]] = association_proxy(
        "_user_organizations", "organization", creator=organization_creator
    )

    _user_organizations: Mapped[list["OrganizationUser"]] = relationship(
        cascade="all, delete-orphan",
        back_populates="user",
    )

    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )

    @hybrid_property
    def password(self):
        """Relying upon database-side crypt() only, so in-Python usage
        is notimplemented.

        """
        raise NotImplementedError("Comparison only supported via the database")

    class CryptComparator(Comparator):
        """A Comparator which provides an __eq__() method that will run
        crypt() against both sides of the expression, to provide the
        test password/salt pair.

        """

        def __init__(self, password_hashed):
            self.password_hashed = password_hashed

        def __eq__(self, other):
            return self.password_hashed == func.crypt(other, self.password_hashed)

    @password.comparator  # type: ignore
    def password(cls):
        """Provide a Comparator object which calls crypt in the
        appropriate fashion.

        """
        return User.CryptComparator(cls.password_hashed)

    @password.setter  # type: ignore
    def password(self, value):
        """assign the value of 'password',
        using a UOW-evaluated SQL function.

        See http://www.sqlalchemy.org/docs/orm/session.html#embedding-sql-insert-update-expressions-into-a-flush
        for a description of SQL expression assignment.

        """  # noqa
        self.password_hashed = func.crypt(value, func.gen_salt("bf"))

    @classmethod
    def get_by_email(cls, email, options=None) -> Optional["User"]:
        """Get user by email."""
        stmt = select(User).where(User.email == email)
        if options:
            stmt = stmt.options(options)
        return flask.g.db.scalars(stmt).first()

    def avatar_url(self, size: int):
        if not self.avatar_s3_bucket and not self.avatar_s3_key:
            return None

        query = urlencode(
            {
                "max-h": size,
                "max-w": size,
                "auth": "format",
                "fit": "facearea",
                "facepad": 2,
                "q": 8,
            }
        )
        return (
            settings.MEDIA_BASE_URL_TEMPLATE.format(s3_key=self.avatar_s3_key)
            + f"?{query}"
        )


class PasswordResetToken(db.Model, db.IdMixin):
    token: Mapped[text] = mapped_column(unique=True, default=secrets.token_hex(16))
    # active = Column(Boolean, default=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("user.id"))
    user: Mapped[User] = relationship(back_populates="password_reset_tokens")
    created_at: Mapped[datetime] = mapped_column(
        DateTime(timezone=True), server_default=func.now()
    )

    @classmethod
    def get_by_active_token(
        cls, token, expires_at: datetime = None, options=None
    ) -> Optional["PasswordResetToken"]:
        """Get PasswordResetToken by token with optional expiration time."""
        stmt = select(PasswordResetToken).where(
            PasswordResetToken.token == token  # , PasswordResetToken.active == True
        )
        if expires_at:
            stmt = stmt.where(PasswordResetToken.created_at > expires_at)
        if options:
            stmt = stmt.options(options)
        return flask.g.db.scalars(stmt).first()
