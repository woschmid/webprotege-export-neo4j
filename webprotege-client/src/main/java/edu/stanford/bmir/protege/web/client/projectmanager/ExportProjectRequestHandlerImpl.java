package edu.stanford.bmir.protege.web.client.projectmanager;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.RunAsyncCallback;
import edu.stanford.bmir.protege.web.client.download.DownloadSettingsDialog;
import edu.stanford.bmir.protege.web.client.export.ProjectRevisionExporter;
import edu.stanford.bmir.protege.web.shared.download.DownloadFormatExtension;
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
                DownloadSettingsDialog.showDialog(extension -> doDownload(projectId, extension));
            }
        });
    }

    private void doDownload(ProjectId projectId, DownloadFormatExtension extension) {
        RevisionNumber head = RevisionNumber.getHeadRevisionNumber();
        ProjectRevisionExporter downloader = new ProjectRevisionExporter(projectId, head, extension);
        downloader.export();
    }
}
