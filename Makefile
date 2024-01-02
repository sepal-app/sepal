.PHONY: clean reqs

requirements.txt: pyproject.toml
	pip-compile -o requirements.txt pyproject.toml

dev-requirements.txt: pyproject.toml
	pip-compile --extra=dev --output-file=dev-requirements.txt pyproject.toml

clean:
	find . -name \*.min.js | xargs rm
	find . -name \*.min.js.map | xargs rm
	find . -name \*.min.css | xargs rm
	flask digest clean

reqs: dev-requirements.txt requirements.txt
