"""add media table

Revision ID: f533d622fc8c
Revises: 6695940c4f13
Create Date: 2022-12-31 18:23:13.577808

"""
from alembic import op
import sqlalchemy as sa


# revision identifiers, used by Alembic.
revision = "f533d622fc8c"
down_revision = "6695940c4f13"
branch_labels = None
depends_on = None


def upgrade():
    # ### commands auto generated by Alembic - please adjust! ###
    op.create_table(
        "media",
        sa.Column("s3_bucket", sa.Text(), nullable=False),
        sa.Column("s3_key", sa.Text(), nullable=False),
        sa.Column("title", sa.Text(), nullable=True),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column("size_in_bytes", sa.Integer(), nullable=True),
        sa.Column("media_type", sa.Text(), nullable=False),
        sa.Column("organization_id", sa.Integer(), nullable=False),
        sa.Column(
            "created_at",
            sa.DateTime(timezone=True),
            server_default=sa.text("now()"),
            nullable=False,
        ),
        sa.Column("created_by", sa.Integer(), nullable=False),
        sa.Column("id", sa.Integer(), sa.Identity(always=False), nullable=False),
        sa.ForeignKeyConstraint(
            ["created_by"], ["user.id"], name=op.f("media_created_by_user_fk")
        ),
        sa.ForeignKeyConstraint(
            ["organization_id"],
            ["organization.id"],
            name=op.f("media_organization_id_organization_fk"),
        ),
        sa.PrimaryKeyConstraint("id", name=op.f("media_pk")),
        sa.UniqueConstraint(
            "organization_id",
            "s3_bucket",
            "s3_key",
            name=op.f("media_organization_id_s3_bucket_s3_key_unq"),
        ),
    )
    # ### end Alembic commands ###


def downgrade():
    # ### commands auto generated by Alembic - please adjust! ###
    op.drop_table("media")
    # ### end Alembic commands ###
