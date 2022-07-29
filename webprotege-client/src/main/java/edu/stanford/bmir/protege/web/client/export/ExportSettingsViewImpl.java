package edu.stanford.bmir.protege.web.client.export;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.ListBox;
import edu.stanford.bmir.protege.web.client.library.dlg.HasRequestFocus;
import edu.stanford.bmir.protege.web.shared.export.ExportFormatExtension;

/**
 * Author: Matthew Horridge<br>
 * Stanford University<br>
 * Bio-Medical Informatics Research Group<br>
 * Date: 24/07/2013
 */
public class ExportSettingsViewImpl extends Composite implements ExportSettingsView {

    interface ExportSettingsViewImplUiBinder extends UiBinder<HTMLPanel, ExportSettingsViewImpl> {

    }

    private static ExportSettingsViewImplUiBinder ourUiBinder = GWT.create(ExportSettingsViewImplUiBinder.class);

    @UiField
    protected ListBox formatListBox;

    public ExportSettingsViewImpl() {
        HTMLPanel rootElement = ourUiBinder.createAndBindUi(this);
        initWidget(rootElement);
        populateListBox();
    }


    private void populateListBox() {
        for(ExportFormatExtension extension : ExportFormatExtension.values()) {
            formatListBox.addItem(extension.getDisplayName());
        }
    }

    @Override
    public ExportFormatExtension getExportFormatExtension() {
        int selIndex = formatListBox.getSelectedIndex();
        if(selIndex == 0) {
            return ExportFormatExtension.owl;
        }
        else {
            return ExportFormatExtension.values()[selIndex];
        }
    }

    @Override
    public void setExportFormatExtension(ExportFormatExtension extension) {
        int selIndex = extension.ordinal();
        formatListBox.setSelectedIndex(selIndex);
    }

    @Override
    public java.util.Optional<HasRequestFocus> getInitialFocusable() {
        return java.util.Optional.empty();
    }
}