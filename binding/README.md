# Binding contract

`ffl.discovered.apebind.yaml` is the raw help-derived inventory.
`ffl.apebind.yaml` is the reviewed semantic contract used for generation.
`ffl.commands.yaml` seeds hidden commands such as `download` and `keygen`.

Upgrade flow:

1. `python scripts/inspect.py --ape /path/to/ffl.com`
2. review the discovered-schema diff
3. merge intended grammar changes into `ffl.apebind.yaml`
4. `python scripts/regenerate.py --ape /path/to/ffl.com`
5. `python scripts/test.py all`
