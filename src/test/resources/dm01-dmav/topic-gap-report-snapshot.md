# DM01 ↔ DMAV Topic Gap Report

> Generated: {{TIMESTAMP}}

## 1. Summary

| Metric | Count |
|---|---|
| DM01 topics | 5 |
| Topics with XLSX hints | 5 |
| Topics with generated candidates | 4 |
| High-risk topics | 0 |
| Total XLSX hints | 9 |
| Total candidates | 9 |

### Complexity Distribution

| Complexity | Count | Topics |
|---|---|---|
| **mittel** | 3 | Bodenbedeckung, Einzelobjekte, Liegenschaften |
| **einfach** | 1 | FixpunkteKategorie3 |
| **manual** | 1 | Nomenklatur |

## 2. Topic Analysis

### 2.3 Bodenbedeckung

| Property | Value |
|---|---|
| Complexity | **mittel** |
| DMAV Model | DMAV |
| Hints | 1 |
| Candidates | 2 |
| Risk Flags | 🔴 AREA geometry present |

**DM01 Classes:** `BoFlaeche`

**DMAV Classes:** `Bodenbedeckungsflaeche`

**Open Issues:**
- AREA vs SURFACE conversion: see §31.4 Q1
- Topology repair vs validation: see §31.4 Q2

---

### 2.4 Einzelobjekte

| Property | Value |
|---|---|
| Complexity | **mittel** |
| DMAV Model | DMAV |
| Hints | 2 |
| Candidates | 2 |
| Risk Flags | 🟡 No XLSX hints — synonym-based only |

**DM01 Classes:** `Flaechenelement`, `Linienelement`

**DMAV Classes:** `Flaechenelement`, `Linienelement`

---

### 2.2 Liegenschaften

| Property | Value |
|---|---|
| Complexity | **mittel** |
| DMAV Model | DMAV |
| Hints | 2 |
| Candidates | 3 |
| Risk Flags | 🔴 LINEATTR present |

**DM01 Classes:** `Grenzpunkt`, `Liegenschaft`

**DMAV Classes:** `Grenzpunkt`, `Grundstueck`

**Open Issues:**
- AREA vs SURFACE conversion: see §31.4 Q1
- Topology repair vs validation: see §31.4 Q2
- LINEATTR handling: see §31.4 Q3

---

### 2.1 FixpunkteKategorie3 — **PILOT IMPLEMENTED** (LFP3)

| Property | Value |
|---|---|
| Complexity | **einfach** |
| DMAV Model | DMAV |
| Hints | 3 |
| Candidates | 2 |

**DM01 Classes:** `LFP3`

**DMAV Classes:** `LFP3`

**Open Issues:**
- Point deduplication: LFP3 ↔ Grenzpunkt ↔ Hoheitsgrenzpunkt: see §31.2 Q6

---

### 2.5 Nomenklatur

| Property | Value |
|---|---|
| Complexity | **manual** |
| DMAV Model | unknown |
| Hints | 1 |
| Candidates | 0 |
| Risk Flags | ⚪ No candidates generated |

---

## 3. Cross-Cutting Concerns

### 3.1 Geometry Types

| Type | Topics Affected | Status |
|---|---|---|
| AREA | 1 topics | Phase 14+: deferred, passthrough in Phase 13 |
| SURFACE | 0 topics | Phase 14+: deferred |
| LINEATTR | 1 topics | Phase 14+: GEOM-LINEATTR-UNSUPPORTED diagnostic |
| COORD/POLYLINE | All | Phase 13: passthrough implemented |

### 3.2 Data Model Mismatches

| Issue | Impact |
|---|---|
| STANDARDOID ↔ UUIDOID | All DMAV targets |
| ITF geometry splitting | AREA/SURFACE in DM01 |
| INTERLIS 1 → INTERLIS 2 | All DM01 → DMAV |
| Point deduplication | Fixpunkte ↔ Grenzpunkte |
## 4. Recommended Next Slices

1. **Liegenschaften** (mittel) — 3 candidates
2. **Bodenbedeckung** (mittel) — 2 candidates
3. **Einzelobjekte** (mittel) — 2 candidates

## 5. Open Questions Summary

> See also: `docs/dm01-dmav/open-questions.md`

- **AREA vs SURFACE**: 1/5 topics affected — see §31.4 Q1
- **LINEATTR**: 1/5 topics affected — see §31.4 Q3
- **Default values**: GueltigerEintrag, Protokoll, AktiverUnterhalt, Grenzpunktfunktion — see §31.2 Q1-Q5
- **Reverse direction**: DMAV→DM01 lossiness — see §31.3
- **ilivalidator integration**: library vs external process — see §31.5 Q1

## 6. Source

- XLSX Correlation Table: `docs/dm01-dmav/DMAV_Korrelationstabelle_20260301.xlsx`
- Hints JSON: `build/generated/dm01-dmav/correlation-hints.json`
- Candidates JSON: `build/generated/dm01-dmav/mapping-candidates.json`
- Report: `{{REPORT_PATH}}`
