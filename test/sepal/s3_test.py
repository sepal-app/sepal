from pathlib import Path

import sepal.s3


def test_presign_request():
    path = Path("/Users/brett/Desktop/rpa-dec-2022.csv")
    content_type = "image/png"
    r = sepal.s3.presign_upload_request(path.name, content_type)
    assert r is not None
    assert "fields" in r
    fields = r["fields"]
    assert isinstance(fields["key"], str)
    assert isinstance(fields["AWSAccessKeyId"], str)
    assert isinstance(fields["policy"], str)
    assert isinstance(fields["signature"], str)
    assert fields["Content-Type"] == content_type
