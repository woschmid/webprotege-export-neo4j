package edu.stanford.bmir.protege.web.server.export;

import edu.stanford.bmir.protege.web.server.access.AccessManager;
import edu.stanford.bmir.protege.web.server.access.ProjectResource;
import edu.stanford.bmir.protege.web.server.access.Subject;
import edu.stanford.bmir.protege.web.server.download.DownloadFormat;
import edu.stanford.bmir.protege.web.server.download.FileDownloadParameters;
import edu.stanford.bmir.protege.web.server.download.ProjectDownloader;
import edu.stanford.bmir.protege.web.server.session.WebProtegeSession;
import edu.stanford.bmir.protege.web.server.session.WebProtegeSessionImpl;
import edu.stanford.bmir.protege.web.shared.access.BuiltInAction;
import edu.stanford.bmir.protege.web.shared.inject.ApplicationSingleton;
import edu.stanford.bmir.protege.web.shared.project.ProjectId;
import edu.stanford.bmir.protege.web.shared.revision.RevisionNumber;
import edu.stanford.bmir.protege.web.shared.user.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;

import static edu.stanford.bmir.protege.web.server.logging.RequestFormatter.formatAddr;

/**
 * Author: Matthew Horridge<br>
 * Stanford University<br>
 * Bio-Medical Informatics Research Group<br>
 * Date: 06/06/2012
 * <p>
 * A servlet which allows ontologies to be downloaded from WebProtege.  See {@link ProjectDownloader} for
 * the piece of machinery that actually does the processing of request parameters and the downloading.
 * </p>
 */
@ApplicationSingleton
public class ProjectExportServlet extends HttpServlet {

    private static final Logger logger = LoggerFactory.getLogger(ProjectExportServlet.class);

    @Nonnull
    private final AccessManager accessManager;

    @Nonnull
    private final ProjectExportService projectExportService;

    @Inject
    public ProjectExportServlet(@Nonnull AccessManager accessManager,
                                @Nonnull ProjectExportService projectExportService) {
        this.accessManager = accessManager;
        this.projectExportService = projectExportService;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        final WebProtegeSession webProtegeSession = new WebProtegeSessionImpl(req.getSession());

        String servletPath = req.getServletPath();
        logger.info("ServletPath: {}", servletPath);

        final ServletContext servletContext = this.getServletConfig().getServletContext();

        String realPath = servletContext.getRealPath("/");
        logger.info("RealPath: {}", realPath);

        String contextPath = servletContext.getContextPath();
        logger.info("ContextPath: {}", contextPath);

        String filePath = realPath + File.separator + "test" + System.currentTimeMillis() + ".xml" ;
        File f = new File(filePath);
        f.createNewFile();

        logger.info("Title.txt Path: {}", f.getAbsolutePath());

        UserId userId = webProtegeSession.getUserInSession();
        FileDownloadParameters downloadParameters = new FileDownloadParameters(req);
        if(!downloadParameters.isProjectDownload()) {
            logger.info("Bad project download request from {} at {}.  Request URI: {}  Query String: {}",
                        webProtegeSession.getUserInSession(),
                        formatAddr(req),
                        req.getRequestURI(),
                        req.getQueryString());
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        logger.info("Received download request from {} at {} for project {}",
                    userId,
                    formatAddr(req),
                    downloadParameters.getProjectId());

        if (!accessManager.hasPermission(Subject.forUser(userId),
                                         new ProjectResource(downloadParameters.getProjectId()),
                                         BuiltInAction.DOWNLOAD_PROJECT)) {
            logger.info("Denied download request as user does not have permission to download this project.");
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
        }
        else if (downloadParameters.isProjectDownload()) {
            startProjectExport(resp, userId, downloadParameters, realPath);
        }
    }

    private void startProjectExport(HttpServletResponse resp,
                                    UserId userId,
                                    FileDownloadParameters downloadParameters, String realPath) throws IOException {
        ProjectId projectId = downloadParameters.getProjectId();
        RevisionNumber revisionNumber = downloadParameters.getRequestedRevision();
        DownloadFormat format = downloadParameters.getFormat();
        projectExportService.exportProject(userId, projectId, revisionNumber, format, resp, realPath);
    }

    @Override
    public void destroy() {
        super.destroy();
        projectExportService.shutDown();
    }
}
