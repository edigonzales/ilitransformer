# DM01/DMAV ilimap-Profile

Dieses Dokument beschreibt die Verwendung der `.ilimap`-DSL fuer DM01/DMAV-Transformationsprofile.

## Ueberblick

DM01/DMAV-Transformationsprofile koennen sowohl als YAML als auch als `.ilimap` geschrieben
werden. Beide Formate werden in denselben `JobConfig`- und `TransformPlan`-Pfad uebersetzt.

Fuer die allgemeine `.ilimap`-Sprachreferenz siehe [`docs/ilimap-v2.md`](../../docs/ilimap-v2.md).

## Migration bestehender YAML-Profile

Bestehende YAML-Profile koennen mit dem CLI-Converter migriert werden:

```bash
ilitransformer convert-mapping \
  --from profiles/dm01-to-dmav/1.1/lfp3.yaml \
  --to profiles/dm01-to-dmav/1.1/lfp3.ilimap
```

## Beispielstruktur (LFP3)

```ilimap
mapping v2 "dm01-to-dmav-lfp3" {
  job {
    direction dm01-to-dmav;
    failPolicy strict;
    compileMode compatible;
    modeldir "https://models.geo.admin.ch/";
  }

  input dm01 {
    path "input/dm01.itf";
    model "DM01AVCH24LV95D";
    format itf;
  }

  output dmav {
    path "build/out/dmav.xtf";
    model "DMAV_FixpunkteAVKategorie3_V1_1";
    format xtf;
  }

  enum Zuverlaessigkeit_DM01_DMAV {
    "ja" => true;
    "nein" => false;
  }

  rule lfp3 {
    target dmav class "DMAV_FixpunkteAVKategorie3_V1_1.FixpunkteAVKategorie3.LFP3";
    source p from dm01 class "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3";
    identity p.NBIdent, p.Nummer;

    assign {
      Nummer = p.Nummer;
      IstLagezuverlaessig = enumMap(p.LageZuv, Zuverlaessigkeit_DM01_DMAV);
    }

    bag Textposition {
      from pos in dm01 class "DM01AVCH24LV95D.FixpunkteKategorie3.LFP3Pos"
        where refEquals(pos.LFP3Pos_von, p);

      assign {
        Position = pos.Pos;
        Orientierung = coalesce(pos.Ori, 100.0);
      }
    }

    ref Entstehung {
      association "Entstehung_LFP3";
      role "Entstehung";
      required;
      target rule lfp3-nachfuehrung sourceRef p.Entstehung;
    }
  }
}
```

## DM01/DMAV-spezifische Hinweise

- INTERLIS-Modell- und Klassenpfade werden in `.ilimap` als Strings geschrieben.
- Enum-Maps fuer DM01/DMAV-Werteabbildungen verwenden `=>` als Zuordnungsoperator.
- Symbolische Enum-Map-Referenzen (z.B. `Zuverlaessigkeit_DM01_DMAV` ohne Anfuehrungszeichen)
  werden beim Laden automatisch normalisiert.
- `bag`-Blocks bilden INTERLIS BAG OF STRUCTURE Beziehungen ab (z.B. Textpositionen).
- `ref`-Blocks bilden INTERLIS-Assoziationen ab (z.B. Entstehung).
- DM01-spezifische Logik gehoert in Profile, nicht in die generische Engine.
