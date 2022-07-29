package edu.stanford.bmir.protege.web.client.export;

import com.google.gwt.user.client.ui.Widget;
import edu.stanford.bmir.protege.web.client.library.dlg.*;
import edu.stanford.bmir.protege.web.shared.export.ExportFormatExtension;

import javax.annotation.Nonnull;

/**
 * Author: Matthew Horridge<br>
 * Stanford University<br>
 * Bio-Medical Informatics Research Group<br>
 * Date: 24/07/2013
 */
public class ExportSettingsDialog {

    private static ExportFormatExtension lastExtension = ExportFormatExtension.owl;

    public static void showDialog(final ExportFormatExtensionHandler handler) {
        final ExportSettingsView view = new ExportSettingsViewImpl();
        view.setExportFormatExtension(lastExtension);
        WebProtegeOKCancelDialogController<ExportFormatExtension> controller = new WebProtegeOKCancelDialogController<ExportFormatExtension>("Export project") {
            @Override
            public Widget getWidget() {
                return view.asWidget();
            }

            @Nonnull
            @Override
            public java.util.Optional<HasRequestFocus> getInitialFocusable() {
                return view.getInitialFocusable();
            }

            @Override
            public ExportFormatExtension getData() {
                return view.getExportFormatExtension();
            }
        };
        controller.setDialogButtonHandler(DialogButton.OK, new WebProtegeDialogButtonHandler<ExportFormatExtension>() {
            @Override
            public void handleHide(ExportFormatExtension data, WebProtegeDialogCloser closer) {
                closer.hide();
                lastExtension = data;
                handler.handleExport(data);
            }
        });
        WebProtegeDialog<ExportFormatExtension> dlg = new WebProtegeDialog<ExportFormatExtension>(controller);
        dlg.show();
    }
}
