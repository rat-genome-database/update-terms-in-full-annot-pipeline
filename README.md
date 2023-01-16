# update-terms-in-full-annot-pipeline
Internal QC pipeline for FULL_ANNOT table: updates term names and aspects, and also reference hard links.

Details:

FULL_ANNOT is a denormalized table. This pipeline ensures that columns TERM and ASPECT
for annotations are in-sync with the ontology tables ONTOLOGIES and ONT_TERMS.
