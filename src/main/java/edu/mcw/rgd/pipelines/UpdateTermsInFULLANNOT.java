package edu.mcw.rgd.pipelines;
import edu.mcw.rgd.datamodel.ontology.Annotation;
import edu.mcw.rgd.datamodel.ontologyx.Term;
import edu.mcw.rgd.process.Utils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.core.io.FileSystemResource;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.*;
import java.util.Date;

/**
 * Program to update TERMs in the FULL_ANNOT table which have the same TERM_ACC
 * with the TERM_ACC field in the ONT_TERMS table.
 * <p>
 * It also fixes term aspect in the FULL_ANNOT table for the annotations
 * having incorrect aspect assigned.
 * <p>
 * Also adds missing entries in RGD_REF_RGD_ID table for JOURNAL ARTICLE annotations.
 */
public class UpdateTermsInFULLANNOT {

    private String version;
    private int lastModifiedBy;
    private UpdateTermsInFullAnnotDao dao = new UpdateTermsInFullAnnotDao();
    Logger log = LogManager.getLogger("fullAnnot");
    Logger logSummary = LogManager.getLogger("summary");
    Logger logRef = LogManager.getLogger("insertRefRgdId");
    Logger logAspect = LogManager.getLogger("aspectFixes");
    private boolean fixMissingHardLinks;

    public static void main(String[] args) throws Exception {

        DefaultListableBeanFactory bf = new DefaultListableBeanFactory();
        new XmlBeanDefinitionReader(bf).loadBeanDefinitions(new FileSystemResource("properties/AppConfigure.xml"));
        UpdateTermsInFULLANNOT manager = (UpdateTermsInFULLANNOT) (bf.getBean("manager"));

        try {
            manager.run();
        } catch(Exception e) {
            // print stack trace to error stream
            ByteArrayOutputStream bs = new ByteArrayOutputStream();
            e.printStackTrace(new PrintStream(bs));
            manager.logSummary.error(bs.toString());
            throw e;
        }
    }

    public void run() throws Exception {

        long time0 = System.currentTimeMillis();

        logSummary.info(getVersion());
        logSummary.info(dao.getConnectionInfo());
        logSummary.info("");

        fixTermAspect();
        fixTermNames();
        fixMissingRefHardLinks();

        logSummary.info("=== OK === elapsed "+Utils.formatElapsedTime(time0, System.currentTimeMillis()));
        logSummary.info("");
    }

    void fixTermNames() throws Exception {
        Set<String> updatedTermNames = new TreeSet<>();

        int rowsUpdated = 0;
        int conflictAnnotsDeleted = 0;
        for(Annotation annot: dao.getAnnotationsWithStaleTermNames() ) {

            // get the correct term name
            Term term = dao.getTermByAccId(annot.getTermAcc());
            if( term==null ) {
                logSummary.error("ERROR: Failed to read term "+annot.getTermAcc());
                continue;
            }

            conflictAnnotsDeleted += checkForConflicts(annot);

            // log the changes
            log.debug("UPDATE FAKey= "+annot.getKey()+" FATermAcc= " + annot.getTermAcc()+" FATerm= " + annot.getTerm()+" OntTerm= " + term.getTerm());
            updatedTermNames.add("  "+annot.getTermAcc()+": ["+annot.getTerm()+"] ==> ["+term.getTerm()+"]");

            // update the term name in annotation object
            annot.setLastModifiedDate(new Date());
            annot.setLastModifiedBy(getLastModifiedBy());
            annot.setTerm(term.getTerm());
            if( dao.updateAnnotation(annot)!=0 ) {
                rowsUpdated++;
            }
        }

        logSummary.info("Annotations with term updates in FULL_ANNOT = " + rowsUpdated);
        if( conflictAnnotsDeleted!=0 )
            logSummary.info("Duplicate Annotations Deleted = " + conflictAnnotsDeleted);

        // dump changed terms
        logSummary.info("Updated term names = "+updatedTermNames.size());
        for( String updatedTermName: updatedTermNames ) {
            logSummary.info(updatedTermName);
        }
    }

    int checkForConflicts(Annotation annot) throws Exception {

        // make sure that the object to be changed does not already contain a duplicate annotation
        List<Annotation> annots = dao.getAnnotations(annot.getAnnotatedObjectRgdId(), annot.getEvidence(), annot.getTermAcc(), annot.getXrefSource());

        int conflictsResolved = 0;
        for( Annotation annot2: annots ) {

            // skip the annotation being updated
            if( annot2.getKey().equals(annot.getKey()) )
                continue;

            // skip annotations having different ref_rgd_id, with_info and qualifier
            if( !Utils.stringsAreEqual(annot.getQualifier(), annot2.getQualifier()) )
                continue;
            if( !Utils.stringsAreEqual(annot.getWithInfo(), annot2.getWithInfo()) )
                continue;
            if( !Utils.intsAreEqual(annot.getRefRgdId(), annot2.getRefRgdId()) )
                continue;

            // if we got here, we have a conflict, that should be deleted
            //
            // buf first transfer notes to annotation being updated
            if( annot2.getNotes()!=null ) {
                if( annot.getNotes()==null ) {
                    annot.setNotes(annot2.getNotes());
                }else if( !annot.getNotes().contains(annot2.getNotes()) ) {
                    if( annot.getTermAcc().startsWith("CHEBI") )
                        // do not append notes for CHEBI annotations to avoid having duplicate notes
                        annot.setNotes(annot2.getNotes());
                    else
                        annot.setNotes(annot.getNotes()+"|"+annot2.getNotes());
                }
            }
            log.warn("CONFLICT DELETE "+annot2.dump("|"));
            dao.deleteAnnotation(annot2.getKey());
            conflictsResolved++;
        }
        return conflictsResolved;
    }

    void fixTermAspect() throws Exception {

        Map<String,String> ontologyIdToAspectMap = dao.getOntologyIdToAspectMap();

        Set<String> updatedAspects = new TreeSet<>();
        String msg;

        int rowsUpdated = 0;
        for(Annotation annot: dao.getAnnotationsWithStaleAspect() ) {

            // get the correct term name
            Term term = dao.getTermByAccId(annot.getTermAcc());
            if( term==null ) {
                logSummary.warn("ERROR: Failed to read term "+annot.getTermAcc());
                continue;
            }

            String correctAspect = ontologyIdToAspectMap.get(term.getOntologyId());
            if( !correctAspect.equals(annot.getAspect()) ) {

                // log the changes
                msg = "  RGD:"+annot.getAnnotatedObjectRgdId() + " ["+annot.getObjectSymbol()+"]  " +
                        annot.getTermAcc() + " ["+annot.getTerm()+"]: [" + annot.getAspect() + "] ==> [" + correctAspect + "]";
                logAspect.info(msg);
                updatedAspects.add(msg);

                // update the term name in annotation object
                annot.setLastModifiedDate(new Date());
                annot.setLastModifiedBy(getLastModifiedBy());
                annot.setAspect(correctAspect);
                if (dao.updateAnnotation(annot) != 0) {
                    rowsUpdated++;
                }
            }
        }

        logSummary.info("Annotations with aspect updates in FULL_ANNOT = " + rowsUpdated);

        // dump changed aspects
        for( String updatedAspect: updatedAspects ) {
            logSummary.info(updatedAspect);
        }
    }

    /**
     * recreate missing reference hardlinks for RGD annotations with 'JOURNAL ARTICLE' references
     * <p>
     * Note: an annotation has a reference hard-link if it has an entry in RGD_REF_RGD_ID table;
     *         if it does not, reference report page wont be able to display the annotated data objects!
     *
     * @throws Exception when something really bad in spring framework occurs
     */
    void fixMissingRefHardLinks() throws Exception {

        String msg;

        // select all JOURNAL ARTICLE annotations that do not have reference hard-links
        List<Annotation> annots = dao.getAnnotationsWithMissingReferenceHardLinks("JOURNAL ARTICLE");
        // dump them the log file
        for( Annotation annot: annots ) {
            msg = "REF_RGD_ID=" + annot.getRefRgdId() + ", RGD_ID=" + annot.getAnnotatedObjectRgdId();
            if( getFixMissingHardLinks() ) {
                logRef.debug(msg);
            } else {
                logRef.debug("DRYRUN "+msg);
            }
        }
        int rowsAffected = annots.size();

        if( getFixMissingHardLinks() ) {
            // insert into RGD_REF_RGD_ID the missing reference hard-links
            for( Annotation annot: annots ) {
                dao.insertReferenceAssociation(annot.getAnnotatedObjectRgdId(), annot.getRefRgdId());
            }
            msg = "Reference hard links added to RGD_REF_RGD_ID: " + rowsAffected;
        } else {
            msg = "Reference hard links that could be added to RGD_REF_RGD_ID: " + rowsAffected;
        }
        logSummary.info(msg);
        logRef.info(msg);
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public void setLastModifiedBy(int lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }

    public int getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setFixMissingHardLinks(boolean fixMissingHardLinks) {
        this.fixMissingHardLinks = fixMissingHardLinks;
    }

    public boolean getFixMissingHardLinks() {
        return fixMissingHardLinks;
    }
}
