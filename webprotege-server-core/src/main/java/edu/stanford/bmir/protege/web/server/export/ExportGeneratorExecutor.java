package edu.stanford.bmir.protege.web.server.export;

import javax.inject.Qualifier;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Matthew Horridge
 * Stanford Center for Biomedical Informatics Research
 * 14 Apr 2017
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
public @interface ExportGeneratorExecutor {

}
