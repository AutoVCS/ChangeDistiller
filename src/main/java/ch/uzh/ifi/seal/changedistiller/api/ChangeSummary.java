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

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import ch.uzh.ifi.seal.changedistiller.model.classifiers.EntityType;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;

/**
 * Summary of changes made to a file
 *
 * @author Kai Presler-Marshall
 *
 */
public class ChangeSummary {

    private final String                                  fileName;

    private final List<SourceCodeChange>                  detailedChanges;

    private final Map<ChangeType, List<SourceCodeChange>> binnedChanges;

    static private final Integer                          CLASS_WEIGHT         = 100;

    static private final Integer                          METHOD_WEIGHT        = 25;

    static private final Integer                          FIELD_WEIGHT         = 10;

    static private final Integer                          DOCUMENTATION_WEIGHT = 1;

    static private final Integer                          OTHER_WEIGHT         = 3;

    public ChangeSummary ( final String fileName, final List<SourceCodeChange> changes ) {
        this.detailedChanges = changes;
        this.fileName = fileName;
        this.binnedChanges = ChangeExtractor.aggregateChanges( detailedChanges );

    }

    public List<SourceCodeChange> getAllChanges () {
        return detailedChanges;
    }

    public Map<ChangeType, List<SourceCodeChange>> getBinnedChanges () {
        return binnedChanges;
    }

    public Map<ChangeType, Integer> getBinnedChangesCounts () {
        return binnedChanges.entrySet().stream()
                .collect( Collectors.toMap( v -> v.getKey(), v -> v.getValue().size() ) );
    }

    public Integer getScore () {

        Integer score = 0;
        for ( final Entry<ChangeType, Integer> entry : getBinnedChangesCounts().entrySet() ) {

            final ChangeType change = entry.getKey();

            final Integer count = entry.getValue();

            final EntityType changedEntity = change.getChangedElement();

            if ( changedEntity.isClass() ) {
                score += ( CLASS_WEIGHT * count );
            }
            else if ( changedEntity.isMethod() ) {
                score += ( METHOD_WEIGHT * count );
            }
            else if ( changedEntity.isField() ) {
                score += ( FIELD_WEIGHT * count );
            }
            else if ( changedEntity.isComment() ) {
                score += ( DOCUMENTATION_WEIGHT * count );
            }
            else {
                score += ( OTHER_WEIGHT * count );
            }

        }
        return score;

    }

    public String getFileName () {
        return this.fileName;
    }

}
