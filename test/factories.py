import factory
import factory.fuzzy
from pytest_factoryboy import register
from sqlalchemy import create_engine
from sqlalchemy.orm import scoped_session, sessionmaker

from sepal.settings import settings
from sepal.auth.models import User
from sepal.invitation.models import Invitation
from sepal.organization.models import Organization
from sepal.taxon.models import Taxon, TaxonRank
from sepal.accession.models import Accession

# Override the DATABASE_URL if it doesn't end with "_test". This isn't necessary
# when running from tox but it can help when running the tests from the command
# line and using the wrong DATABASE_URL
settings.DATABASE_URL = (
    settings.DATABASE_URL + "_test"
    if not settings.DATABASE_URL.endswith("_test")
    else settings.DATABASE_URL
)

print(f"Connecting to {settings.DATABASE_URL}...")

engine = create_engine(settings.DATABASE_URL, echo=settings.SQLALCHEMY_ECHO)
session_factory = sessionmaker(bind=engine)
Session = scoped_session(session_factory)


@register
class UserFactory(factory.alchemy.SQLAlchemyModelFactory):
    email = factory.Faker("email")
    password = factory.fuzzy.FuzzyText()

    class Meta:
        model = User
        sqlalchemy_session = Session
        sqlalchemy_session_persistence = "flush"


@register
class OrganizationFactory(factory.alchemy.SQLAlchemyModelFactory):
    name = factory.fuzzy.FuzzyText()

    class Meta:
        model = Organization
        sqlalchemy_session = Session
        sqlalchemy_session_persistence = "flush"


@register
class InvitationFactory(factory.alchemy.SQLAlchemyModelFactory):
    email = factory.Faker("email")
    organization = factory.SubFactory(OrganizationFactory)
    token = factory.fuzzy.FuzzyText()

    class Meta:
        model = Invitation
        sqlalchemy_session = Session
        sqlalchemy_session_persistence = "flush"


@register
class TaxonFactory(factory.alchemy.SQLAlchemyModelFactory):
    name = factory.fuzzy.FuzzyText()
    rank = factory.fuzzy.FuzzyChoice(TaxonRank)
    organization = factory.SubFactory(OrganizationFactory)

    class Meta:
        model = Taxon
        sqlalchemy_session = Session
        sqlalchemy_session_persistence = "flush"


@register
class AccessionFactory(factory.alchemy.SQLAlchemyModelFactory):
    code = factory.fuzzy.FuzzyText()
    organization = factory.SubFactory(OrganizationFactory)
    taxon = factory.SubFactory(TaxonFactory)

    class Meta:
        model = Accession
        sqlalchemy_session = Session
        sqlalchemy_session_persistence = "flush"
