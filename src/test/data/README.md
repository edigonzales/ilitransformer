# Test Data

`src/test/data/` enthält dateibasierte Testartefakte, die nicht als Klassenpfad-Ressourcen behandelt werden.

## Struktur

- `models/` kleine lokale `.ili`-Modelle für Unit- und Integrationstests
- `av/models/` eingecheckte AV-Modelle für DM01/DMAV
- `DMAV_Version_1_1/` vollständige reale DM01- und DMAV-Datensätze

## Abgrenzung zu `src/test/resources/`

- `data/` ist für Modelle und grössere bzw. offiziell eingecheckte Datensätze gedacht.
- `resources/` ist für kleine kuratierte Fixtures, YAML-Mappings und Snapshots gedacht.

## INTERLIS-1-Fixtures

Aktuell gibt es hier keine separat gepflegten `interlis1/`-Fixtures. Falls solche wieder eingeführt werden, müssen sie mit `ili2c` und `ilivalidator` geprüft werden.
