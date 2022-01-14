package edu.mcw.rgd.pipelines;

import edu.mcw.rgd.dao.impl.AnnotationDAO;
import edu.mcw.rgd.dao.impl.AssociationDAO;
import edu.mcw.rgd.dao.impl.OntologyXDAO;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.Ontology;
import edu.mcw.rgd.datamodel.ontologyx.Term;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author mtutaj
 * @since 12/27/12
 */
public class UpdateTermsInFullAnnotDao {

    private AnnotationDAO annotationDAO = new AnnotationDAO();
    private AssociationDAO associationDAO = new AssociationDAO();
    private OntologyXDAO ontologyDAO = new OntologyXDAO();

    public String getConnectionInfo() {
        return annotationDAO.getConnectionInfo();
    }

    /**
     * Update annotation object in FULL_ANNOT table. Note: Annotation.getKey() must be a valid full_annot_key
     *
     * @param annot Annotation object representing properties to be updated
     * @throws Exception on spring framework dao failure
     * @return number of rows affected by the update
     */
    public int updateAnnotation(Annotation annot) throws Exception{
        return annotationDAO.updateAnnotation(annot);
    }

    /**
     * get annotations with stale term names;<p>
     * term names for these annotations are different than term names in ONT_TERMS table;
     * in other words, an annotation has a stale term name if its term name is not up-to-date with ONT_TERMS table
     * @return list of Annotation objects; could be empty
     * @throws Exception on spring framework dao failure
     */
    public List<Annotation> getAnnotationsWithStaleTermNames() throws Exception {
        return annotationDAO.getAnnotationsWithStaleTermNames();
    }

    /**
     * get annotations with stale aspect;<p>
     * aspect for these annotations is different than ontology aspect in ONTOLOGIES table;
     * in other words, an annotation has a stale aspect if its aspect does not match the ontology aspect of the annotated term
     * @return list of Annotation objects; could be empty
     * @throws Exception on spring framework dao failure
     */
    public List<Annotation> getAnnotationsWithStaleAspect() throws Exception {
        return annotationDAO.getAnnotationsWithStaleAspect();
    }

    /**
     * get list of annotations matching the parameters
     * @param annotatedObjectRGDId annotated object rgd id
     * @param evidence evidence code
     * @param termAcc term acc id
     * @param xrefSource xref source
     * @return list of annotations
     * @throws Exception on spring framework dao failure
     */
    public List<Annotation> getAnnotations(int annotatedObjectRGDId, String evidence, String termAcc, String xrefSource) throws Exception {

        return annotationDAO.getAnnotations(annotatedObjectRGDId, evidence, termAcc, xrefSource);
    }

    /**
     * delete annotation object given full_annot_key
     *
     * @param key full_annot_key
     * @return number of rows affected by the delete: 1 - successful delete, 0 - invalid key
     * @throws Exception on spring framework dao failure
     */
    public int deleteAnnotation(int key) throws Exception{
        return annotationDAO.deleteAnnotation(key);
    }

    /**
     * get an ontology term given term accession id;
     * return null if accession id is invalid
     * @param termAcc term accession id
     * @return Term object if given term found in database or null otherwise
     * @throws Exception if something wrong happens in spring framework
     */
    public Term getTermByAccId(String termAcc) throws Exception {

        return ontologyDAO.getTermWithStatsCached(termAcc);
    }

    /**
     * load from database a hash map of ontology ids to aspect
     * @return hashmap object
     */
    Map<String,String> getOntologyIdToAspectMap() throws Exception {

        Map<String,String> results = new HashMap<>();
        for( Ontology ont: ontologyDAO.getOntologies() ) {
            results.put(ont.getId(), ont.getAspect());
        }
        return results;
    }

    /**
     * get annotations in RGD without reference hard links;
     * reference hard links allow reference report pages to show the annotated data objects;
     * reference hard links should be automatically created by curation software;
     * an annotation has a reference hard-link if it has an entry in RGD_REF_RGD_ID table;
     * @param refType result is limited to annotations with given reference type, f.e. 'JOURNAL ARTICLE'
     * @return list of annotations with given reference type that have missing reference hard links
     * @throws Exception on spring framework dao failure
     */
    public List<Annotation> getAnnotationsWithMissingReferenceHardLinks(String refType) throws Exception {
        return annotationDAO.getAnnotationsWithMissingReferenceHardLinks(refType);
    }

    /**
     * insert reference association for given object
     * @param rgdId rgd id of object the reference will be associated with
     * @param refRgdId rgd id of the reference to be associated with
     * @throws Exception when something wrong happens in spring framework
     */
    public void insertReferenceAssociation(int rgdId, int refRgdId) throws Exception{
        associationDAO.insertReferenceeAssociation(rgdId, refRgdId);
    }
}
