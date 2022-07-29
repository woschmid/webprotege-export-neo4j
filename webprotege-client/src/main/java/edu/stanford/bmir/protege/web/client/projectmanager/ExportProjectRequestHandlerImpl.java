package edu.stanford.bmir.protege.web.client.projectmanager;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.RunAsyncCallback;
import edu.stanford.bmir.protege.web.client.export.ExportSettingsDialog;
import edu.stanford.bmir.protege.web.client.export.ProjectRevisionExporter;
import edu.stanford.bmir.protege.web.shared.export.ExportFormatExtension;
import edu.stanford.bmir.protege.web.shared.project.ProjectId;
import edu.stanford.bmir.protege.web.shared.revision.RevisionNumber;

import javax.inject.Inject;

public class ExportProjectRequestHandlerImpl implements ExportProjectRequestHandler {

    @Inject
    public ExportProjectRequestHandlerImpl() {
    }

    @Override
    public void handleProjectExportRequest(ProjectId projectId) {
        GWT.runAsync(new RunAsyncCallback() {
            @Override
            public void onFailure(Throwable reason) {
            }

            @Override
            public void onSuccess() {
                ExportSettingsDialog.showDialog(extension -> doExport(projectId, extension));
            }
        });
    }

    private void doExport(ProjectId projectId, ExportFormatExtension extension) {
        RevisionNumber head = RevisionNumber.getHeadRevisionNumber();
        ProjectRevisionExporter exporter = new ProjectRevisionExporter(projectId, head, extension);
        exporter.export();
    }
}
