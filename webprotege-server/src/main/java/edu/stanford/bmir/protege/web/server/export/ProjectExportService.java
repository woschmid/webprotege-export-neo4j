package edu.stanford.bmir.protege.web.server.export;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Striped;
import edu.stanford.bmir.protege.web.server.download.DownloadFormat;
import edu.stanford.bmir.protege.web.server.download.FileTransferExecutor;
import edu.stanford.bmir.protege.web.server.download.ProjectDownloadCache;
import edu.stanford.bmir.protege.web.server.project.ProjectDetailsManager;
import edu.stanford.bmir.protege.web.server.project.ProjectManager;
import edu.stanford.bmir.protege.web.server.revision.HeadRevisionNumberFinder;
import edu.stanford.bmir.protege.web.shared.inject.ApplicationSingleton;
import edu.stanford.bmir.protege.web.shared.project.ProjectId;
import edu.stanford.bmir.protege.web.shared.revision.RevisionNumber;
import edu.stanford.bmir.protege.web.shared.user.UserId;
import org.neo4j.driver.*;
import org.neo4j.driver.internal.value.NullValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.locks.Lock;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * Matthew Horridge
 * Stanford Center for Biomedical Informatics Research
 * 14 Apr 2017
 */
@ApplicationSingleton
public class ProjectExportService {

    private static final Logger logger = LoggerFactory.getLogger(ProjectExportService.class);

    @Nonnull
    private final ExecutorService exportGeneratorExecutor;

    @Nonnull
    private final ExecutorService fileTransferExecutor;

    @Nonnull
    private final ProjectDetailsManager projectDetailsManager;

    @Nonnull
    private final ProjectDownloadCache projectDownloadCache;

    @Nonnull
    private final HeadRevisionNumberFinder headRevisionNumberFinder;

    private final Striped<Lock> lockStripes = Striped.lazyWeakLock(10);

    @Nonnull
    private final CreateExportTaskFactory createExportTaskFactory;

    @Nonnull
    private final ProjectExporterFactory projectExporterFactory;

    @Nonnull
    private final ProjectManager projectManager;

    @Inject
    public ProjectExportService(@Nonnull @ExportGeneratorExecutor ExecutorService exportGeneratorExecutor,
                                @Nonnull @FileTransferExecutor ExecutorService fileTransferExecutor,
                                @Nonnull ProjectDetailsManager projectDetailsManager,
                                @Nonnull ProjectManager projectManager,
                                @Nonnull ProjectDownloadCache projectDownloadCache,
                                @Nonnull HeadRevisionNumberFinder headRevisionNumberFinder,
                                @Nonnull CreateExportTaskFactory createExportTaskFactory,
                                @Nonnull ProjectExporterFactory projectExporterFactory) {
        this.exportGeneratorExecutor = checkNotNull(exportGeneratorExecutor);
        this.fileTransferExecutor = checkNotNull(fileTransferExecutor);
        this.projectDetailsManager = checkNotNull(projectDetailsManager);
        this.projectDownloadCache = checkNotNull(projectDownloadCache);
        this.headRevisionNumberFinder = checkNotNull(headRevisionNumberFinder);
        this.createExportTaskFactory = checkNotNull(createExportTaskFactory);
        this.projectExporterFactory = checkNotNull(projectExporterFactory);
        this.projectManager = checkNotNull(projectManager);
    }

    public void exportProject(@Nonnull UserId requester,
                              @Nonnull ProjectId projectId,
                              @Nonnull RevisionNumber revisionNumber,
                              @Nonnull DownloadFormat downloadFormat,
                              @Nonnull HttpServletResponse response,
                              @Nonnull String realPath) throws IOException {

        File file = exportOntology(requester,
                projectId,
                revisionNumber,
                downloadFormat,
                realPath);

        // test connection to neo4j
        exportToNeo4J(file.getName(), downloadFormat);

        file.delete();
    }

    private File exportOntology(@Nonnull UserId requester,
                                           @Nonnull ProjectId projectId,
                                           @Nonnull RevisionNumber revisionNumber,
                                           @Nonnull DownloadFormat downloadFormat,
                                           @Nonnull String realPath) {
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
                    projectManager.getRevisionManager(projectId));

            try {
                String fileName = projectDisplayName + '.' + downloadFormat.getExtension();
                String filePath = realPath + File.separator + fileName;
                File file = new File(filePath);
                file.createNewFile();
                exporter.exportProject(new FileOutputStream(file));
                return file;
            } catch (FileNotFoundException e) {
                logger.info("The file {} in {} was not found of this project was interrupted.", projectId, requester);
                throw new RuntimeException(e);
            } catch (IOException e) {
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

    final static String NEO4JHOST = "localhost"; //"neo4j";
    final static String WEBPROTEGEHOST = "localhost"; //"webprotege";

    private void exportToNeo4J(String filename, DownloadFormat downloadFormat) {
        String uri = "bolt://"+NEO4JHOST+":7687";
        Driver driver = GraphDatabase.driver( uri, AuthTokens.basic( "neo4j", "test" ) );
        Session session = driver.session();

        if (! doesUniquenessConstraintExist(session)) {
            final String s = session.writeTransaction(tx -> {
                Result result = tx.run("CREATE CONSTRAINT n10s_unique_uri ON (r:Resource) ASSERT r.uri IS UNIQUE");
                return result.list().toString();
            });
            logger.info("doesUniquenessConstraintExist() -> {}", s);
        }

        if (! doesGraphConfigExist(session)) {
            final String s = session.writeTransaction(tx -> {
                Result result = tx.run("CALL n10s.graphconfig.init()");
                return result.list().toString();
            });
            logger.info("doesGraphConfigExist() -> {}", s);
        }

        final String serializationFormat = downloadFormat.getDownloadFormatExtension().getDisplayName();
        final String s = session.writeTransaction(tx -> {
            Result result = tx.run("CALL n10s.rdf.import.fetch(\"http://"+WEBPROTEGEHOST+":8080/" + filename + "\",\"" + serializationFormat + "\");");
            return result.list().toString();
        });
        logger.info("Importing {} -> {}", filename, s);

        driver.close();
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
