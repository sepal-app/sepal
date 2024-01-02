"""add user.name

Revision ID: fd96dabf467f
Revises: 0d2fe858299d
Create Date: 2022-09-04 21:18:41.646131

"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = "fd96dabf467f"
down_revision = "0d2fe858299d"
branch_labels = None
depends_on = None


def upgrade():
    # ### commands auto generated by Alembic - please adjust! ###
    op.add_column(
        "user", sa.Column("name", sa.Text(), server_default="", nullable=False)
    )
    # ### end Alembic commands ###


def downgrade():
    # ### commands auto generated by Alembic - please adjust! ###
    op.drop_column("user", "name")
    # ### end Alembic commands ###
