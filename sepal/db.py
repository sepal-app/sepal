import re
from typing import Annotated, Any, Optional, Self, cast

import flask
from sqlalchemy import (
    Engine,
    Identity,
    MetaData,
    Select,
    Text,
    create_engine,
    func,
    select,
)
from sqlalchemy.orm import (
    DeclarativeBase,
    Mapped,
    Session,
    declarative_mixin,
    mapped_column,
    registry,
    sessionmaker,
)
from sqlalchemy.sql.base import ExecutableOption
from wtforms import Form

engine = None
session_factory = None


def get_engine() -> Optional[Engine]:
    return engine


class TableNamer:  # pragma: no cover
    def __get__(self, obj: Any, type: Any) -> str | None:
        if (
            type.__dict__.get("__tablename__") is None
            and type.__dict__.get("__table__") is None
        ):
            type.__tablename__ = (
                re.sub(
                    r"((?<=[a-z0-9])[A-Z]|(?!^)[A-Z](?=[a-z]))", r"_\1", type.__name__
                )
                .lower()
                .lstrip("_")
            )
        return getattr(type, "__tablename__", None)


convention = {
    "ix": "%(column_0_label)s_idx",
    "uq": "%(table_name)s_%(column_0_N_name)s_unq",
    "ck": "%(table_name)s_%(constraint_name)s_chk",
    "fk": "%(table_name)s_%(column_0_N_name)s_%(referred_table_name)s_fk",
    "pk": "%(table_name)s_pk",
}

metadata = MetaData(naming_convention=convention)

text = Annotated[str, mapped_column(Text)]


class Model(DeclarativeBase):
    # __abstract__ = True
    __tablename__ = TableNamer()
    metadata = metadata

    registry = registry(
        type_annotation_map={
            text: Text,
        }
    )

    def _create_in_session(self, session: Optional[Session] = None) -> Self:
        if session is None:
            session = flask.g.db

        with session.begin_nested():
            session.add(self)

        flask.g.db.merge(self)
        return self

    @classmethod
    def create(cls, session: Optional[Session] = None, **data: dict[str, Any]) -> Self:
        if session is None:
            session = flask.g.db

        o = cls(**data)
        with session.begin_nested():
            session.add(o)

        session.merge(o)
        return o

    @classmethod
    def create_from_form(
        cls, form: Form, session: Optional[Session] = None, **data: dict[str, Any]
    ) -> Self:
        o = cls()
        form.populate_obj(o)
        return o._create_in_session(session=session)

    def save_form(
        self, form: Form, session: Optional[Session] = None, **data: dict[str, Any]
    ) -> Self:
        if session is None:
            session = flask.g.db

        with session.begin_nested():
            form.populate_obj(self)
            session.add(self)

        return self


def init_app(app: flask.Flask) -> None:
    global engine, session_factory

    engine = create_engine(
        app.config.get("DATABASE_URL", ""),
        echo=app.config.get("SQLALCHEMY_ECHO"),
        future=True,
    )
    session_factory = sessionmaker(engine, future=True)

    @app.before_request
    def setup() -> None:
        if session_factory is not None:
            flask.g.db = session_factory()

    @app.teardown_request
    def teardown(exc: Exception) -> None:
        if exc:
            # TODO: log and send to sentry
            print(exc)
        else:
            if hasattr(flask.g, "db"):
                flask.g.db.commit()

        if hasattr(flask.g, "db"):
            flask.g.db.close()


# @contextmanager
# def begin():
#     """Context manager for a database transaction."""
#     with Session() as session:
#         with session.begin():
#             yield session


@declarative_mixin
class IdMixin:
    id: Mapped[int] = mapped_column(Identity(), primary_key=True)

    @classmethod
    def get_by_id(
        cls,
        record_id: int | str | float,
        options: Optional[ExecutableOption] = None,
        session: Optional[Session] = None,
    ) -> Self | None:
        """Get record by ID."""
        if any(
            (
                isinstance(record_id, str) and record_id.isdigit(),
                isinstance(record_id, (int, float)),
            )
        ):
            stmt = select(cls).where(cls.id == (int(record_id)))
            if options:
                stmt = stmt.options(options)

            if session is None:
                session = flask.g.db

            return cast(Self | None, session.scalar(stmt))
        return None


def count(stmt: Select[Any], session: Optional[Session] = None) -> int:
    if session is None:
        session = flask.g.db

    return cast(int, session.scalar(select(func.count("*")).select_from(stmt)))
