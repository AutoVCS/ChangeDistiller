package ch.uzh.ifi.seal.changedistiller.api;

/*
 * #%L ChangeDistiller %% Copyright (C) 2011 - 2021 Software Architecture and
 * Evolution Lab, Department of Informatics, UZH %% Licensed under the Apache
 * License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License. #L%
 */

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ch.uzh.ifi.seal.changedistiller.ChangeDistiller;
import ch.uzh.ifi.seal.changedistiller.ChangeDistiller.Language;
import ch.uzh.ifi.seal.changedistiller.distilling.FileDistiller;
import ch.uzh.ifi.seal.changedistiller.model.classifiers.EntityType;
import ch.uzh.ifi.seal.changedistiller.model.classifiers.java.JavaEntityType;
import ch.uzh.ifi.seal.changedistiller.model.entities.Delete;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;

/**
 * Entrypoint to ChangeDistiller. Takes an old version and a new version of a
 * file, and reports aggregated counts of what was added/changed between them.
 *
 * @author Kai Presler-Marshall
 *
 */
public class ChangeExtractor {

    static public ChangeSummary extractChanges ( final String oldFile, final String newFile ) {

        final File left = new File( oldFile );
        final File right = new File( newFile );

        final FileDistiller distiller = ChangeDistiller.createFileDistiller( Language.JAVA );
        try {
            distiller.extractClassifiedSourceCodeChanges( left, right, true );
        }
        catch ( final Exception e ) {
            System.err.println( "Warning: error while change distilling. " + e.getMessage() );
            e.printStackTrace();
            return null;
        }

        final List<SourceCodeChange> changes = distiller.getSourceCodeChanges();
        changes.sort( ( a, b ) -> a.getClass().getSimpleName().compareTo( b.getClass().getSimpleName() ) );

        final String separator = "/";

        final String trimmedFilename = newFile
                .substring( newFile.indexOf( separator, newFile.indexOf( separator ) + 1 ) + 1 );

        return new ChangeSummary( trimmedFilename, changes );

    }

    static public Map<ChangeType, Integer> extractChangesCounts ( final String oldFile, final String newFile ) {
        return extractChanges( oldFile, newFile ).getBinnedChangesCounts();

    }

    public static final Map<ChangeType, List<SourceCodeChange>> aggregateChanges (
            final List<SourceCodeChange> toAggregate ) {

        final Map<ChangeType, List<SourceCodeChange>> mappedChanges = new HashMap<ChangeType, List<SourceCodeChange>>();

        for ( final SourceCodeChange change : toAggregate ) {

            if ( change instanceof Delete ) {
                continue;
            }

            EntityType changedEntity = change.getChangedEntity().getType();

            if ( changedEntity.isComment() ) {
                changedEntity = JavaEntityType.DOCUMENTATION;
            }

            else if ( ! ( changedEntity.isMethod() || changedEntity.isClass() || changedEntity.isField() ) ) {
                changedEntity = JavaEntityType.OTHER;
            }

            final String typeOfChange = change.getClass().getSimpleName();

            final ChangeType type = new ChangeType( changedEntity, typeOfChange );

            if ( !mappedChanges.containsKey( type ) ) {
                mappedChanges.put( type, new ArrayList<SourceCodeChange>() );
            }

            mappedChanges.get( type ).add( change );

        }

        return mappedChanges;

    }

}
