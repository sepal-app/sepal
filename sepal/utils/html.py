from typing import Any


def tag(name: str, attributes: dict[str, Any], children=None) -> str:
    attrs = " ".join([f'{k}="{v}"' for k, v in attributes.items()])
    return f"<{name} {attrs}>{children if children is not None else ''}</{name}>"
