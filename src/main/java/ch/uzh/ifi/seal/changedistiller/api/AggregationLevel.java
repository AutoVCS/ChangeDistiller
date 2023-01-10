package ch.uzh.ifi.seal.changedistiller.api;

/*
 * #%L
 * ChangeDistiller
 * %%
 * Copyright (C) 2011 - 2021 Software Architecture and Evolution Lab, Department of Informatics, UZH
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


/**
 * Options for how finely to aggregate results
 *
 * @author Kai Presler-Marshall
 *
 */
public enum AggregationLevel {

    /**
     * Perform no aggregation; report every change made
     */
    NONE,

    /**
     * Group changes, but do not sum them up
     */
    LOW,

    /**
     * For each class, summarise contributions with a count of (classes,
     * methods, documentation, everything else)
     */
    DEFAULT,

    /**
     * Present a single score for each file, representing a weighted sum of
     * everything that was changed
     */
    SINGLE_SCORE;

}
