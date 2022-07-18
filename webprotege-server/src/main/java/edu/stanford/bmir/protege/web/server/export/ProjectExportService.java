package edu.stanford.bmir.protege.web.server.export;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Striped;
import edu.stanford.bmir.protege.web.server.download.*;
import edu.stanford.bmir.protege.web.server.project.ProjectDetailsManager;
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
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

    @Inject
    public ProjectExportService(@Nonnull @ExportGeneratorExecutor ExecutorService exportGeneratorExecutor,
                                @Nonnull @FileTransferExecutor ExecutorService fileTransferExecutor,
                                @Nonnull ProjectDetailsManager projectDetailsManager,
                                @Nonnull ProjectDownloadCache projectDownloadCache,
                                @Nonnull HeadRevisionNumberFinder headRevisionNumberFinder,
                                @Nonnull CreateExportTaskFactory createExportTaskFactory) {
        this.exportGeneratorExecutor = checkNotNull(exportGeneratorExecutor);
        this.fileTransferExecutor = checkNotNull(fileTransferExecutor);
        this.projectDetailsManager = checkNotNull(projectDetailsManager);
        this.projectDownloadCache = checkNotNull(projectDownloadCache);
        this.headRevisionNumberFinder = checkNotNull(headRevisionNumberFinder);
        this.createExportTaskFactory = checkNotNull(createExportTaskFactory);
    }

    public void exportProject(@Nonnull UserId requester,
                              @Nonnull ProjectId projectId,
                              @Nonnull RevisionNumber revisionNumber,
                              @Nonnull DownloadFormat downloadFormat,
                              @Nonnull HttpServletResponse response,
                              @Nonnull String realPath) throws IOException {

        RevisionNumber realRevisionNumber;
        if(revisionNumber.isHead()) {
            realRevisionNumber = getHeadRevisionNumber(projectId, requester);
        }
        else {
            realRevisionNumber = revisionNumber;
        }

        Path downloadPath = projectDownloadCache.getCachedDownloadPath(projectId, realRevisionNumber, downloadFormat);

        createDownloadIfNecessary(requester,
                                  projectId,
                                  revisionNumber,
                                  downloadFormat,
                                  downloadPath, realPath);

        transferFileToClient(projectId,
                             requester,
                             revisionNumber,
                             downloadFormat,
                             downloadPath,
                             response);
    }

    private void createDownloadIfNecessary(@Nonnull UserId requester,
                                           @Nonnull ProjectId projectId,
                                           @Nonnull RevisionNumber revisionNumber,
                                           @Nonnull DownloadFormat downloadFormat,
                                           @Nonnull Path downloadPath,
                                           @Nonnull String realPath) {
        // This thing always returns the same lock for the same project.
        // This means that we won't create the *same* download more than once.  It
        // does mean that multiple *different* downloads could possibly be created at the same time
        Lock lock = lockStripes.get(projectId);
        try {
            lock.lock();
            if (Files.exists(downloadPath)) {
                logger.info("{} {} Download for the requested revision already exists.  Using cached download.",
                            projectId,
                            requester);
                return;
            }
            CreateExportTask task = createExportTaskFactory.create(projectId,
                                                                       requester,
                                                                       getProjectDisplayName(projectId),
                                                                       revisionNumber,
                                                                       downloadFormat,
                                                                       downloadPath);
            try {
                var futureOfCreateDownload = exportGeneratorExecutor.submit(task);
                logger.info("{} {} Submitted request to create download to queue", projectId, requester);
                var stopwatch = Stopwatch.createStarted();
                logger.info("{} {} Waiting for download to be created", projectId, requester);
                futureOfCreateDownload.get();
                logger.info("{} {} Created download after {} ms", projectId, requester, stopwatch.elapsed(MILLISECONDS));
            } catch(RejectedExecutionException e) {
                logger.info("{} {} Generate download request rejected", projectId, requester);
            } catch (InterruptedException e) {
                logger.info("{} {} The download of this project was interrupted.", projectId, requester);
            } catch (ExecutionException e) {
                logger.info("{} {} An execution exception occurred whilst creating the download.  Cause: {}",
                            projectId,
                            requester,
                            Optional.ofNullable(e.getCause()).map(Throwable::getMessage).orElse(""),
                            e.getCause());
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

    private void transferFileToClient(@Nonnull ProjectId projectId,
                                      @Nonnull UserId userId,
                                      @Nonnull RevisionNumber revisionNumber,
                                      @Nonnull DownloadFormat downloadFormat,
                                      @Nonnull Path downloadSource,
                                      @Nonnull HttpServletResponse response) {


        // test connection to neo4j
        testNeo4jConnection();

        String fileName = getClientSideFileName(projectId, revisionNumber, downloadFormat);
        FileTransferTask task = new FileTransferTask(projectId,
                                                     userId,
                                                     downloadSource,
                                                     fileName,
                                                     response);
        Future<?> transferFuture = fileTransferExecutor.submit(task);
        try {
            transferFuture.get();
        } catch (InterruptedException e) {
            logger.info("{} {} The download of this project was interrupted.", projectId, userId);
        } catch (ExecutionException e) {
            logger.info("{} {} An execution exception occurred whilst transferring the project.  Cause: {}",
                        projectId,
                        userId,
                        Optional.ofNullable(e.getCause()).map(Throwable::getMessage).orElse(""),
                        e.getCause());
        }
    }

    final static String NEO4JHOST = "neo4j";
    final static String WEBPROTEGEHOST = "webprotege";

    private void testNeo4jConnection() {
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

        final String s = session.writeTransaction(tx -> {
            Result result = tx.run("CALL n10s.rdf.import.fetch(\"http://"+WEBPROTEGEHOST+":8080/koala.ttl\",\"Turtle\");");
            return result.list().toString();
        });
        logger.info("Import koala.ttl() -> {}", s);

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

    private String getClientSideFileName(ProjectId projectId, RevisionNumber revision, DownloadFormat downloadFormat) {
        String revisionNumberSuffix;
        if (revision.isHead()) {
            revisionNumberSuffix = "";
        }
        else {
            revisionNumberSuffix = "-REVISION-" + Long.toString(revision.getValue());
        }
        String projectDisplayName = projectDetailsManager.getProjectDetails(projectId).getDisplayName();
        String fileName = projectDisplayName.replaceAll("\\s+", "-")
                + revisionNumberSuffix
                + "-ontologies."
                + downloadFormat.getExtension()
                + ".zip";
        return fileName.toLowerCase();
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

    private RevisionNumber getHeadRevisionNumber(@Nonnull ProjectId projectId, @Nonnull UserId userId) throws IOException {
        Stopwatch stopwatch = Stopwatch.createStarted();
        RevisionNumber headRevisionNumber = headRevisionNumberFinder.getHeadRevisionNumber(projectId);
        logger.info("{} {} Computed head revision number ({}) in {} ms",
                    projectId,
                    userId,
                    headRevisionNumber,
                    stopwatch.elapsed(MILLISECONDS));
        return headRevisionNumber;

    }

}
