package edu.stanford.bmir.protege.web.server.export;

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import edu.stanford.bmir.protege.web.server.download.DownloadFormat;
import edu.stanford.bmir.protege.web.server.project.PrefixDeclarationsStore;
import edu.stanford.bmir.protege.web.server.revision.HeadRevisionNumberFinder;
import edu.stanford.bmir.protege.web.server.revision.RevisionManager;
import edu.stanford.bmir.protege.web.shared.project.ProjectId;
import edu.stanford.bmir.protege.web.shared.revision.RevisionNumber;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.search.EntitySearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.ac.manchester.cs.owlapi.modularity.ModuleType;
import uk.ac.manchester.cs.owlapi.modularity.SyntacticLocalityModuleExtractor;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.io.*;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Author: Matthew Horridge<br>
 * Stanford University<br>
 * Bio-Medical Informatics Research Group<br>
 * Date: 06/06/2012
 */
public class ProjectExporter {

    private static final Logger logger = LoggerFactory.getLogger(ProjectExporter.class);

    @Nonnull
    private final RevisionNumber revision;

    @Nonnull
    private final DownloadFormat downloadFormat;

    @Nonnull
    private final String projectDisplayName;

    @Nonnull
    private final PrefixDeclarationsStore prefixDeclarationsStore;

    @Nonnull
    private final RevisionManager revisionManager;

    @Nonnull
    private final HeadRevisionNumberFinder headRevisionNumberFinder;

    @Nonnull
    private final ProjectId projectId;

    @Nonnull
    private final String realPath;

    /**
     * Creates a project downloader that downloads the specified revision of the specified project.
     *
     * @param revisionManager         The revision manager of project to be downloaded.  Not <code>null</code>.
     * @param revision                The revision of the project to be downloaded.
     * @param downloadFormat                  The format which the project should be downloaded in.
     * @param prefixDeclarationsStore The prefix declarations store that is used to retrieve customised prefixes
     */
    @AutoFactory
    @Inject
    public ProjectExporter(@Nonnull ProjectId projectId,
                           @Nonnull String projectDisplayName,
                           @Nonnull RevisionNumber revision,
                           @Nonnull DownloadFormat downloadFormat,
                           @Nonnull RevisionManager revisionManager,
                           @Nonnull HeadRevisionNumberFinder headRevisionNumberFinder,
                           @Nonnull String realPath,
                           @Provided @Nonnull PrefixDeclarationsStore prefixDeclarationsStore) {
        this.projectId = checkNotNull(projectId);
        this.revision = checkNotNull(revision);
        this.revisionManager = checkNotNull(revisionManager);
        this.headRevisionNumberFinder = checkNotNull(headRevisionNumberFinder);
        this.downloadFormat = checkNotNull(downloadFormat);
        this.projectDisplayName = checkNotNull(projectDisplayName);
        this.realPath = checkNotNull(realPath);
        this.prefixDeclarationsStore = checkNotNull(prefixDeclarationsStore);
    }

    public File exportOntology() throws IOException, OWLOntologyStorageException, OWLOntologyCreationException {
        RevisionNumber realRevisionNumber;
        if (this.revision.isHead()) {
            realRevisionNumber = getHeadRevisionNumber(projectId);
        }
        else {
            realRevisionNumber = this.revision;
        }

        String fileName = projectDisplayName + '_' + realRevisionNumber + '_' + System.currentTimeMillis() + '.' + downloadFormat.getExtension();
        String filePath = realPath + (realPath.endsWith(File.separator) ? "" : File.separator) + fileName;
        File file = new File(filePath);
        if (file.exists()) {
            boolean isPreExistingFileDeleted = file.delete();
            if (!isPreExistingFileDeleted)
                throw new IOException("File " + file.getName() + " already exists in " + file.getAbsolutePath() + " and could not be deleted before exporting the new file");
        }
        boolean isFileCreated = file.createNewFile();
        if (isFileCreated) {
            FileOutputStream fileOutputStream = new FileOutputStream(file);
            exportProjectRevision(revision, fileOutputStream, downloadFormat);
        } else
            throw new IOException("Could not create file " + fileName + " in path " + realPath);
        return file;
    }

    private RevisionNumber getHeadRevisionNumber(@Nonnull ProjectId projectId) throws IOException {
        return headRevisionNumberFinder.getHeadRevisionNumber(projectId);
    }

    private void exportProjectRevision(@Nonnull RevisionNumber revisionNumber,
                                       @Nonnull OutputStream outputStream,
                                       @Nonnull DownloadFormat format) throws IOException, OWLOntologyStorageException, OWLOntologyCreationException {
        OWLOntologyManager manager = revisionManager.getOntologyManagerForRevision(revisionNumber);
        saveFailureOntologyToStream(manager, format, outputStream);
    }

    private void saveOntologyToStream(@Nonnull OWLOntologyManager manager,
                                      @Nonnull DownloadFormat format,
                                      @Nonnull OutputStream outputStream) throws IOException, OWLOntologyStorageException, OWLOntologyCreationException {
        try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream)) {
            Set<OWLOntology> ontologies = manager.getOntologies();
            if (ontologies.size() == 1) {
                for (var ontology : ontologies) {
                    var documentFormat = format.getDocumentFormat();
                    if (documentFormat.isPrefixOWLOntologyFormat()) {
                        var prefixDocumentFormat = documentFormat.asPrefixOWLOntologyFormat();
                        Map<String, String> prefixes = prefixDeclarationsStore.find(projectId).getPrefixes();
                        prefixes.forEach(prefixDocumentFormat::setPrefix);
                    }
                    ontology.getOWLOntologyManager().saveOntology(ontology, documentFormat, bufferedOutputStream);


                }
            } else {
                throw new RuntimeException("Only one ontology supported");
            }
            bufferedOutputStream.flush();
        }
    }

    private void saveFailureOntologyToStream(@Nonnull OWLOntologyManager manager,
                                      @Nonnull DownloadFormat format,
                                      @Nonnull OutputStream outputStream) throws IOException, OWLOntologyStorageException, OWLOntologyCreationException {
        try (BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(outputStream)) {
            Set<OWLOntology> ontologies = manager.getOntologies();
            if (ontologies.size() == 1) {
                for (var ontology : ontologies) {
                    var documentFormat = format.getDocumentFormat();
                    if (documentFormat.isPrefixOWLOntologyFormat()) {
                        var prefixDocumentFormat = documentFormat.asPrefixOWLOntologyFormat();
                        Map<String, String> prefixes = prefixDeclarationsStore.find(projectId).getPrefixes();
                        prefixes.forEach(prefixDocumentFormat::setPrefix);
                    }
                    //ontology.getOWLOntologyManager().saveOntology(ontology, documentFormat, bufferedOutputStream);


                    // 20220929, test for exporting only module of a specific class
                    final IRI ontologyIRI = ontology.getOntologyID().getOntologyIRI().get();

                    // Building the entity's IRI (failure)
                    final String namespace = ontologyIRI.getNamespace();
                    final String remainder = ontologyIRI.getRemainder().get();
                    final String suffix = "#Failure";
                    final IRI entityIRI = IRI.create( namespace + remainder , suffix);

                    // this should get the one class using the entity's IRI
                    final Set<OWLEntity> signature = ontology.getEntitiesInSignature(entityIRI);

                    final OWLOntologyManager owlOntologyManager = ontology.getOWLOntologyManager();

                    //ontology.getSubClassAxiomsForSubClass(signature.stream().iterator().next())

                    // using the SyntacticLocalityModuleExtractor
                    final SyntacticLocalityModuleExtractor extractor = new SyntacticLocalityModuleExtractor(owlOntologyManager, ontology, ModuleType.STAR);
                    final OWLOntology tmpOntology = extractor.extractAsOntology(signature, IRI.create(ontologyIRI + "_tmp"));

                    final OWLOntology tmp1Ontology = createSubtreeOntology(signature, ontology, owlOntologyManager, IRI.create(ontologyIRI + "_tmp1"));
                    owlOntologyManager.saveOntology(tmp1Ontology, documentFormat, bufferedOutputStream);


                }
            } else {
                throw new RuntimeException("Only one ontology supported");
            }
            bufferedOutputStream.flush();
        }
    }

    private OWLOntology createSubtreeOntology(Set<OWLEntity> signature, OWLOntology ontology, OWLOntologyManager manager, IRI iri) throws OWLOntologyCreationException {
        OWLOntology newOnt = manager.createOntology(iri);

        Set<OWLSubClassOfAxiom> subClassOfAxioms = ontology.getAxioms(AxiomType.SUBCLASS_OF);
        Set<OWLAnnotationAssertionAxiom> assertionAxioms = ontology.getAxioms(AxiomType.ANNOTATION_ASSERTION);
        for (OWLEntity ax : signature) {
            assert ax != null;
            //OWLDataFactory df = manager.getOWLDataFactory();
            createSubTreeOntologyRek(ax.asOWLClass(), manager, newOnt, ontology, subClassOfAxioms, assertionAxioms);
        }

        return newOnt;
    }

    private void createSubTreeOntologyRek(OWLClass superClass, OWLOntologyManager manager, OWLOntology newOnt, OWLOntology oldOnt, Set<OWLSubClassOfAxiom> subClassOfAxioms, Set<OWLAnnotationAssertionAxiom> owlAnnotationAssertionAxioms) {
        if (superClass != null) {
            OWLDataFactory df = manager.getOWLDataFactory();
            IRI superClassIRI = superClass.getIRI();

            Collection<OWLAnnotation> annotations1 = EntitySearcher.getAnnotations(superClass, oldOnt);

            // find all annotations related to the superClass
            Set<OWLAnnotation> annotations = new HashSet<>();
            for (OWLAnnotationAssertionAxiom a: owlAnnotationAssertionAxioms) {

                OWLAnnotationSubject subject = a.getSubject();
                if (subject.isIRI()) {
                    IRI annotationIRI = (IRI) subject;
                    if (superClassIRI.equals(annotationIRI)) {
                        OWLAnnotation annotation = df.getOWLAnnotation(a.getProperty(), a.getValue());
                        annotations.add(annotation);
                    }
                }
            }

            // first, add the class to the new ontology
            OWLDeclarationAxiom owlDeclarationAxiom = df.getOWLDeclarationAxiom(superClass, annotations);
            //owlDeclarationAxioms.add(owlDeclarationAxiom);
            manager.addAxiom(newOnt, owlDeclarationAxiom);

            // now fina all direct subclasses and call recursively
            for (OWLSubClassOfAxiom a : subClassOfAxioms) {
                OWLClassExpression subClass = a.getSubClass();
                if (a.getSuperClass().equals(superClass) && subClass instanceof OWLClass) {
                    //manager.addAxiom(newOnt, manager.getOWLDataFactory().getOWLSubClassOfAxiom())
                    createSubTreeOntologyRek(subClass.asOWLClass(), manager, newOnt, oldOnt, subClassOfAxioms, owlAnnotationAssertionAxioms);
                    manager.addAxiom(newOnt, df.getOWLSubClassOfAxiom(subClass,superClass));
                }
            }
        }
    }

}
