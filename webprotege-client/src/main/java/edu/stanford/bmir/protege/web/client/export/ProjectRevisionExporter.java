package edu.stanford.bmir.protege.web.client.export;

import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.URL;
import com.google.gwt.user.client.Window;
import edu.stanford.bmir.protege.web.shared.export.ExportFormatExtension;
import edu.stanford.bmir.protege.web.shared.project.ProjectId;
import edu.stanford.bmir.protege.web.shared.revision.RevisionNumber;

import static com.google.common.base.Preconditions.checkNotNull;
import static edu.stanford.bmir.protege.web.shared.download.ProjectDownloadConstants.*;

/**
 * Author: Matthew Horridge<br>
 * Stanford University<br>
 * Bio-Medical Informatics Research Group<br>
 * Date: 10/10/2013
 * <p>
 *     Downloads a project (possibly a specific revision) by opening a new browser window
 * </p>
 */
public class ProjectRevisionExporter {

    private final ProjectId projectId;

    private final RevisionNumber revisionNumber;

    private final ExportFormatExtension formatExtension;

    /**
     * Constructs a ProjectRevisionDownloader for the specified project, revision and project format.
     * @param projectId The project id.  Not {@code null}.
     * @param revisionNumber The revision to download.  Not {@code null}.
     * @param downloadFormatExtension The format that the project should be downloaded in.  Not {@code null}.
     * @throws  NullPointerException if any parameters are {@code null}.
     */
    public ProjectRevisionExporter(ProjectId projectId, RevisionNumber revisionNumber, ExportFormatExtension downloadFormatExtension) {
        this.projectId = checkNotNull(projectId);
        this.revisionNumber = checkNotNull(revisionNumber);
        this.formatExtension = checkNotNull(downloadFormatExtension);
    }

    /**
     * Causes a new browser window to be opened which will download the specified project revision in the specified
     * format.
     */
    public void export() {
        String encodedProjectName = URL.encode(projectId.getId());
        String baseURL = GWT.getHostPageBaseURL();
        String downloadURL = baseURL + "export?"
                + PROJECT + "=" + encodedProjectName  +
                "&" + REVISION + "=" + revisionNumber.getValue() +
                "&" + FORMAT + "=" + formatExtension.getExtension();
        Window.open(downloadURL, "Export ontology", "");
    }

}
