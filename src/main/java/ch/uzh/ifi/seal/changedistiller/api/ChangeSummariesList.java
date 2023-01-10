package ch.uzh.ifi.seal.changedistiller.api;

import java.util.ArrayList;

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

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;

public class ChangeSummariesList implements Iterable<ChangeSummary> {

    private final List<ChangeSummary>                     changes;

    private final Integer                                 contributionsScore;

    private Double                                        contributionsScorePercent;

    private final Map<ChangeType, List<SourceCodeChange>> binnedChanges;

    public ChangeSummariesList ( final List<ChangeSummary> changes ) {

        final Map<String, List<SourceCodeChange>> changesPerFile = new LinkedHashMap<>();

        changes.forEach( change -> {
            final String fileName = change.getFileName();
            if ( changesPerFile.containsKey( fileName ) ) {
                changesPerFile.get( fileName ).addAll( change.getAllChanges() );
            }
            else {
                changesPerFile.put( fileName, new ArrayList<SourceCodeChange>( change.getAllChanges() ) );
            }
        } );

        final List<ChangeSummary> consolidatedChanges = new ArrayList<ChangeSummary>();
        changesPerFile.forEach( ( file, changesForFile ) -> {
            consolidatedChanges.add( new ChangeSummary( file, changesForFile ) );
        } );

        this.changes = consolidatedChanges;
        this.contributionsScore = consolidatedChanges.stream().map( e -> e.getScore() ).reduce( 0, ( a, b ) -> a + b );

        this.binnedChanges = ChangeExtractor.aggregateChanges( consolidatedChanges.stream()
                .flatMap( e -> e.getAllChanges().stream() ).collect( Collectors.toList() ) );
    }

    @Override
    public Iterator<ChangeSummary> iterator () {
        return changes.iterator();
    }

    public List<ChangeSummary> getChanges () {
        return changes;
    }

    public Integer getContributionsScore () {
        return contributionsScore;
    }

    public Map<ChangeType, List<SourceCodeChange>> getBinnedChanges () {
        return binnedChanges;
    }

    public Map<ChangeType, Integer> getBinnedChangesCounts () {
        return binnedChanges.entrySet().stream()
                .collect( Collectors.toMap( v -> v.getKey(), v -> v.getValue().size() ) );
    }

    public Double getContributionsScorePercent () {
        return contributionsScorePercent;
    }

    public void setContributionsScorePercent ( final Double contributionsScorePercent ) {
        this.contributionsScorePercent = contributionsScorePercent;
    }

}
