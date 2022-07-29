package edu.stanford.bmir.protege.web.client.export;

import com.google.gwt.user.client.ui.IsWidget;
import edu.stanford.bmir.protege.web.client.library.dlg.HasInitialFocusable;
import edu.stanford.bmir.protege.web.shared.export.ExportFormatExtension;

/**
 * Author: Matthew Horridge<br>
 * Stanford University<br>
 * Bio-Medical Informatics Research Group<br>
 * Date: 24/07/2013
 */
public interface ExportSettingsView extends IsWidget, HasInitialFocusable {

    ExportFormatExtension getExportFormatExtension();

    void setExportFormatExtension(ExportFormatExtension extension);
}
