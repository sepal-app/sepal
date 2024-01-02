"""add user avatar columns

Revision ID: 5d0507507293
Revises: f533d622fc8c
Create Date: 2023-01-08 14:37:31.399120

"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = "5d0507507293"
down_revision = "f533d622fc8c"
branch_labels = None
depends_on = None


def upgrade():
    # ### commands auto generated by Alembic - please adjust! ###
    with op.batch_alter_table("media", schema=None) as batch_op:
        batch_op.alter_column(
            "size_in_bytes", existing_type=sa.INTEGER(), nullable=False
        )

    with op.batch_alter_table("user", schema=None) as batch_op:
        batch_op.add_column(sa.Column("avatar_s3_bucket", sa.Text(), nullable=True))
        batch_op.add_column(sa.Column("avatar_s3_key", sa.Text(), nullable=True))

    # ### end Alembic commands ###


def downgrade():
    # ### commands auto generated by Alembic - please adjust! ###
    with op.batch_alter_table("user", schema=None) as batch_op:
        batch_op.drop_column("avatar_s3_key")
        batch_op.drop_column("avatar_s3_bucket")

    with op.batch_alter_table("media", schema=None) as batch_op:
        batch_op.alter_column(
            "size_in_bytes", existing_type=sa.INTEGER(), nullable=True
        )

    # ### end Alembic commands ###
