# ADR 0001: Java-first statt Polyglot-first

## Status

Accepted (Phase 0).

## Context

Das Projekt steht vor der Entscheidung, in welcher Sprache die Mapping-Engine implementiert wird. Alternativen waren:
- Java (klar, robust, Nähe zu INTERLIS-Tools)
- Groovy/Kotlin (modernere Sprachfeatures)
- Python (für Data-Science-Affinität)

## Decision

Java-first. Der Core der Engine soll in reinem Java implementiert werden, ohne Abhängigkeiten zu Groovy, Kotlin oder Python.

## Rationale

- **Nähe zu INTERLIS-Tools**: `ili2c`, `iox-ili` und `ilivalidator` sind Java-Bibliotheken. Eine direkte Integration ohne Sprachbarrieren ist robuster.
- **Testbarkeit**: JUnit, AssertJ und Mockito haben das beste Ökosystem für Java.
- **Toolchain-Stabilität**: Gradle-Java-Projekt mit Java 21 LTS Toolchain (P7.1: von 25 auf 21 gesenkt, da keine Java-22+-Features benötigt).
- **Zielgruppe**: GIS-Entwickler, die INTERLIS-Werkzeuge kennen, sind in der Regel Java-kompetent.

## Consequences

- Keine Groovy/Kotlin im Hauptcode
- Keine Python-Scripting-Integration
- Expression-Language saß in Java (keine JEXL, kein JavaScript)
- Mapping-Dateien in YAML (Jackson-basiert), nicht in Skriptsprachen
- Build-Script in Gradle (Groovy DSL, akzeptiert da Build-Tool)
