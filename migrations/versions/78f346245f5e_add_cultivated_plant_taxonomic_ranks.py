"""Add cultivated plant taxonomic ranks

Revision ID: 78f346245f5e
Revises: 0ae5bb301f3e
Create Date: 2023-05-29 09:06:25.623531

"""
from alembic import op

# revision identifiers, used by Alembic.
revision = "78f346245f5e"
down_revision = "0ae5bb301f3e"
branch_labels = None
depends_on = None


def upgrade():
    op.execute("ALTER TYPE taxon_rank ADD VALUE 'cultivar'")
    op.execute("ALTER TYPE taxon_rank ADD VALUE 'cultivar_group'")
    op.execute("ALTER TYPE taxon_rank ADD VALUE 'grex'")


def downgrade():
    pass
