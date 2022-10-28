package edu.stanford.bmir.protege.web.server.export;

import com.google.common.util.concurrent.Striped;
import edu.stanford.bmir.protege.web.server.download.DownloadFormat;
import edu.stanford.bmir.protege.web.server.download.FileTransferExecutor;
//import edu.stanford.bmir.protege.web.server.download.ProjectDownloadCache;
import edu.stanford.bmir.protege.web.server.project.ProjectDetailsManager;
import edu.stanford.bmir.protege.web.server.project.ProjectManager;
import edu.stanford.bmir.protege.web.server.revision.HeadRevisionNumberFinder;
import edu.stanford.bmir.protege.web.shared.inject.ApplicationSingleton;
import edu.stanford.bmir.protege.web.shared.project.ProjectId;
import edu.stanford.bmir.protege.web.shared.revision.RevisionNumber;
import edu.stanford.bmir.protege.web.shared.user.UserId;
import org.neo4j.driver.*;
import org.neo4j.driver.Record;
import org.neo4j.driver.internal.value.NullValue;
import org.neo4j.driver.util.Pair;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import javax.validation.UnexpectedTypeException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Matthew Horridge
 * Stanford Center for Biomedical Informatics Research
 * 14 Apr 2017
 */
@ApplicationSingleton
public class ProjectExportService {

    /** The host of neo4j (either localhost or a docker name) */
    final static String NEO4JHOST = "neo4j"; //*/"localhost";

    /** The port number of the neo4j host */
    final static int NEO4JPORT = 7687;

    final static String NEO4JUSER = "neo4j";

    final static String NEO4JPASS = "test";

    /** The host of webprotege (either localhost or a docker name) */
    final static String WEBPROTEGEHOST = "webprotege"; //*/"localhost";

    final static int WEBPROTEGEPORT = 8080;

    final static String IMPORTTYPE = "onto"; //*/"rdf"

    private static final Logger logger = LoggerFactory.getLogger(ProjectExportService.class);

    @Nonnull
    private final ExecutorService exportGeneratorExecutor;

    @Nonnull
    private final ExecutorService fileTransferExecutor;

    @Nonnull
    private final ProjectDetailsManager projectDetailsManager;

    //@Nonnull
    //private final ProjectDownloadCache projectDownloadCache;

    @Nonnull
    private final HeadRevisionNumberFinder headRevisionNumberFinder;

    private final Striped<Lock> lockStripes = Striped.lazyWeakLock(10);

    @Nonnull
    private final ProjectExporterFactory projectExporterFactory;

    @Nonnull
    private final ProjectManager projectManager;

    @Inject
    public ProjectExportService(@Nonnull @ExportGeneratorExecutor ExecutorService exportGeneratorExecutor,
                                @Nonnull @FileTransferExecutor ExecutorService fileTransferExecutor,
                                @Nonnull ProjectDetailsManager projectDetailsManager,
                                @Nonnull ProjectManager projectManager,
                                //@Nonnull ProjectDownloadCache projectDownloadCache,
                                @Nonnull HeadRevisionNumberFinder headRevisionNumberFinder,
                                @Nonnull ProjectExporterFactory projectExporterFactory) {
        this.exportGeneratorExecutor = checkNotNull(exportGeneratorExecutor);
        this.fileTransferExecutor = checkNotNull(fileTransferExecutor);
        this.projectDetailsManager = checkNotNull(projectDetailsManager);
        //this.projectDownloadCache = checkNotNull(projectDownloadCache);
        this.headRevisionNumberFinder = checkNotNull(headRevisionNumberFinder);
        this.projectExporterFactory = checkNotNull(projectExporterFactory);
        this.projectManager = checkNotNull(projectManager);
    }

    public void exportProject(@Nonnull UserId requester,
                              @Nonnull ProjectId projectId,
                              @Nonnull RevisionNumber revisionNumber,
                              @Nonnull DownloadFormat downloadFormat,
                              @Nonnull HttpServletResponse response,
                              @Nonnull String realPath) throws IOException {

        // create an export file on webprotege host
        final File file = exportOntology(requester, projectId, revisionNumber, downloadFormat, realPath);

        // import file into neo4j via cypher
        final String responseMsg = importOntologyIntoNeo4J(file.getName(), downloadFormat);

        boolean isFileDeleted = file.delete();
        if (!isFileDeleted) logger.warn("File " + file.getName() + " could not be deleted!");

        sendSuccessMessage(response, responseMsg);
    }

    private void sendSuccessMessage(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_CREATED);
        PrintWriter writer = response.getWriter();
        writer.print(msg);
    }

    private File exportOntology(@Nonnull UserId requester,
                                           @Nonnull ProjectId projectId,
                                           @Nonnull RevisionNumber revisionNumber,
                                           @Nonnull DownloadFormat downloadFormat,
                                           @Nonnull String realPath) throws RuntimeException {
        // This thing always returns the same lock for the same project.
        // This means that we won't create the *same* download more than once.  It
        // does mean that multiple *different* downloads could possibly be created at the same time
        Lock lock = lockStripes.get(projectId);
        try {
            lock.lock();

            final String projectDisplayName = getProjectDisplayName(projectId);

            ProjectExporter exporter = projectExporterFactory.create(projectId,
                    projectDisplayName,
                    revisionNumber,
                    downloadFormat,
                    projectManager.getRevisionManager(projectId),
                    headRevisionNumberFinder,
                    realPath);

            try {
                return exporter.exportOntology();
            } catch (FileNotFoundException e) {
                logger.info("The file {} in {} was not found of this project was interrupted.", projectId, requester);
                throw new RuntimeException(e);
            } catch (IOException | OWLOntologyStorageException | OWLOntologyCreationException e) {
                throw new RuntimeException(e);
            }
        }
        finally {
            lock.unlock();
        }
    }

    private String getProjectDisplayName(@Nonnull ProjectId projectId) {
        return projectDetailsManager.getProjectDetails(projectId)
                                    .getDisplayName();
    }

    private String importOntologyIntoNeo4J(String filename, DownloadFormat downloadFormat) {
        String uri = "bolt://"+NEO4JHOST+":"+NEO4JPORT;
        Driver driver = GraphDatabase.driver( uri, AuthTokens.basic( NEO4JUSER, NEO4JPASS ) );
        StringBuilder sb = new StringBuilder("<html>\n");
        addHeader(sb);

        sb.append("<body>\n");
        try {
            Session session = driver.session();

            // first delete everything
            final String transactionResult01 = session.writeTransaction(tx -> {
                Result result = tx.run("MATCH (n) DETACH DELETE n");
                final List<Record> list = result.list();
                return list.toString();
            });
            logger.info("MATCH (n) DETACH DELETE n -> {}", transactionResult01);
            sb.append("<p>").append("Deleting existing graph ... ").append(transactionResult01).append("<p/>");

            // Pre-requisite: Create uniqueness constraint
            if (!doesUniquenessConstraintExist(session)) {
                final String transactionResult02 = session.writeTransaction(tx -> {
                    Result result = tx.run("CREATE CONSTRAINT n10s_unique_uri ON (r:Resource) ASSERT r.uri IS UNIQUE");
                    return listToString(result.list());
                });
                logger.info("doesUniquenessConstraintExist() -> {}", transactionResult02);
                sb.append("<p>").append("Creating uniqueness constraint ... ").append(transactionResult02).append("<p/>");
            }

            // Setting the configuration of the graph
            if (!doesGraphConfigExist(session)) {
                final String transactionResult03 = session.writeTransaction(tx -> {
                    Result result = tx.run("CALL n10s.graphconfig.init()");
                    return listToString(result.list());
                });
                logger.info("doesGraphConfigExist() -> {}", transactionResult03);
                sb.append("<p>").append("Setting the configuration of the graph: ").append(transactionResult03).append("<p/>");
            }

            // Importing the ontology from the exported file
            final String serializationFormat = downloadFormat.getDownloadFormatExtension().getDisplayName();
            final String transactionResult04 = session.writeTransaction(tx -> {
                final String query = "CALL n10s." + IMPORTTYPE + ".import.fetch(\"http://" + WEBPROTEGEHOST + ":" + WEBPROTEGEPORT + "/" + filename + "\",\"" + serializationFormat + "\");";
                Result result = tx.run(query);
                final List<Record> list = result.list();
                if (list.size() == 1) {
                    Record record = list.get(0);
                    Value terminationStatus = record.get("terminationStatus");
                    if (terminationStatus.isNull())
                        throw new UnexpectedTypeException("terminationStatus not available");
                    String terminationStatusValue = terminationStatus.asString();
                    if ("KO".equalsIgnoreCase(terminationStatusValue)) {
                        Value extraInfo = record.get("extraInfo");
                        if (extraInfo.isNull())
                            throw new UnexpectedTypeException("extraInfo unknown");
                        String extraInfoValue = extraInfo.asString();
                        return "Import failed because " + extraInfoValue;
                    } else if ("OK".equalsIgnoreCase(terminationStatusValue)) {
                        return transformResultToString(record);
                    } else {
                        throw new UnexpectedTypeException("terminationStatus is of unexpected value " + terminationStatusValue);
                    }
                } else {
                    throw new UnexpectedTypeException("Result of import has " + list.size() + " elements");
                }
            });
            logger.info("Importing ontology {} using serialization format {} -> {}", filename, serializationFormat, transactionResult04);
            sb.append("<p>").append("Importing ontology ").append(filename).append(" ... ").append(transactionResult04).append("</p>");

        } finally {
            driver.close();
            sb.append("</body></html>");
        }
        return sb.toString();
    }

    private String listToString(final List<Record> list) {
        if (list.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("<ul>");
        for (Record r : list) {
            sb.append("<li>");
            for (Pair<String, Value> field : r.fields())
                sb.append(field.value()).append(':');
            sb.append("</li>");
        }
        sb.append("</ul>");
        return sb.toString();
    }

    private void addHeader(StringBuilder sb) {
        sb.append("<head>\n").
                append("    <meta http-equiv=\"Content-Type\" content=\"text/html; charset=UTF-8\">\n").
                append("    <title>WebProt&#233;g&#233; Report for Export to Neo4J</title>\n").
                append("    <link rel=\"shortcut icon\" type=\"image/x-icon\" href=\"favicon.png\"/>\n").
                append("    <link rel=\"stylesheet\" href=\"css/WebProtege.css\" type=\"text/css\">\n").
                append("</head>\n");
    }

    private String transformResultToString(final Record r) {
        return "<ul>" + "<li>terminationStatus:" + r.get("terminationStatus") + "</li>" +
                "<li>triplesLoaded:" + r.get("triplesLoaded") + "</li>" +
                "<li>triplesParsed:" + r.get("triplesParsed") + "</li>" +
                "</ul>";
    }

    private boolean doesGraphConfigExist(Session session) {
        return session.writeTransaction(tx -> {
            Result result = tx.run("CALL n10s.graphconfig.show()");
            List<Record> l = result.list();
            return !l.isEmpty();
        });
    }

    /**
     * Checks if the uniqueness constraint which is necessary for NeoSemantics is already declared or not.
     * @param session A session.
     * @return <code>true</code> if constraint already exists, otherwise <code>false</code>.
     */
    private boolean doesUniquenessConstraintExist(Session session) {
        return session.writeTransaction(tx -> {
            Result result = tx.run("CALL db.constraints()");

            List<Record> l = result.list();
            if (!l.isEmpty()) {
                for (Record record : l) {
                    final Value description = record.get("description");
                    if (!NullValue.NULL.equals(description))
                        if (description.asString().equalsIgnoreCase("CONSTRAINT ON ( resource:Resource ) ASSERT (resource.uri) IS UNIQUE"))
                            return true;
                }
                return false;
            } else {
                return false;
            }
        });
    }

    /**
     * Shuts down this {@link ProjectExportService}.
     */
    public void shutDown() {
        logger.info("Shutting down Project Download Service");
        exportGeneratorExecutor.shutdown();
        fileTransferExecutor.shutdown();
        logger.info("Project Download Service has been shut down");
    }

}
