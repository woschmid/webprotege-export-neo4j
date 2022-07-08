package edu.stanford.bmir.protege.web.client.projectmanager;

import edu.stanford.bmir.protege.web.shared.project.ProjectId;

public interface ExportProjectRequestHandler {

    void handleProjectExportRequest(ProjectId projectId);
}
