package edu.stanford.bmir.protege.web.client.export;

import edu.stanford.bmir.protege.web.shared.export.ExportFormatExtension;

/**
 * Author: Matthew Horridge<br>
 * Stanford University<br>
 * Bio-Medical Informatics Research Group<br>
 * Date: 24/07/2013
 */
public interface ExportFormatExtensionHandler {

    void handleExport(ExportFormatExtension extension);
}
