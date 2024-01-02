"""add extensions

Revision ID: 0ae5bb301f3e
Revises: 54274de8b1af
Create Date: 2023-04-08 12:28:50.712098

"""
from alembic import op


# revision identifiers, used by Alembic.
revision = "0ae5bb301f3e"
down_revision = "54274de8b1af"
branch_labels = None
depends_on = None


def upgrade():
    op.execute("create extension if not exists pgcrypto;")
    op.execute("create extension if not exists postgis;")
    op.execute("create extension if not exists btree_gin;")
    op.execute("create extension if not exists btree_gist;")
    op.execute("create extension if not exists plpgsql;")
    op.execute('create extension if not exists "uuid-ossp";')


def downgrade():
    op.execute("drop extension pgcrypto;")
    op.execute("drop extension postgis;")
    op.execute("drop extension btree_gin;")
    op.execute("drop extension btree_gist;")
    op.execute("drop extension plpgsql;")
    op.execute('drop "uuid-ossp";')
