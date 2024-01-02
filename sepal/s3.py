import logging

import boto3
from botocore.exceptions import ClientError

from sepal.settings import settings


def client():
    return boto3.client(
        "s3",
        endpoint_url=settings.S3_ENDPOINT_URL,
        aws_access_key_id=settings.AWS_ACCESS_KEY_ID,
        aws_secret_access_key=settings.AWS_SECRET_ACCESS_KEY,
    )


def presign_upload_request(key: str, content_type: str):
    try:
        s3 = client()
        response = s3.generate_presigned_post(
            settings.S3_MEDIA_BUCKET,
            key,
            Fields={"Content-Type": content_type},
            Conditions=[{"Content-Type": content_type}],
            # expires in 1 hour
            ExpiresIn=60 * 60,
        )
        return response
    except ClientError as e:
        logging.error(e)
        return None
