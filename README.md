# update-terms-in-full-annot-pipeline

Internal QC pipeline for the `FULL_ANNOT` table: keeps denormalized term names
and aspects in sync with the ontology tables, and fixes missing reference hard links.

## Overview

`FULL_ANNOT` stores denormalized annotation data including term names and aspects.
When ontology terms are renamed or aspects change, these cached values become stale.
This pipeline detects and corrects such mismatches.

## Logic

The pipeline runs three independent fix passes:

1. **Fix term aspects** — compares the aspect in each annotation against the canonical
   aspect from the `ONTOLOGIES` table. Updates any mismatches.
2. **Fix term names** — finds annotations where the term name differs from the current
   name in `ONT_TERMS`. Before updating, checks for duplicate annotations that would
   conflict after the rename and deletes them (transferring notes where applicable).
3. **Fix missing reference hard links** — finds `JOURNAL ARTICLE` annotations that lack
   entries in `RGD_REF_RGD_ID` and inserts the missing associations. This ensures
   reference report pages can display the annotated data objects. Can run in dry-run
   mode via the `fixMissingHardLinks` configuration property.

## Logging

- `fullAnnot` — main pipeline progress, term/aspect update summaries
- `aspectFixes` — detailed log of each aspect correction
- `insertRefRgdId` — audit log for reference hard link insertions

## Configuration

Configured in `properties/AppConfigure.xml`:
- `lastModifiedBy` — pipeline user account ID (172) stamped on updated annotations
- `fixMissingHardLinks` — set to `true` to insert missing hard links, `false` for dry-run

## Build and run

Requires Java 17. Built with Gradle:
```
./gradlew clean assembleDist
```
