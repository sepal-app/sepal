## Start the local server

```sh
. venv/bin/activate
python -m sepal
```

### Generate requirements.txt

```sh
pip-compile --output-file=requirements.txt pyproject.toml
```

### Generate dev-requirements.txt

```sh
pip-compile --extra=dev --output-file=dev-requirements.txt pyproject.toml
```
