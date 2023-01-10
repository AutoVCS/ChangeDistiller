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

import java.util.Objects;

import ch.uzh.ifi.seal.changedistiller.model.classifiers.EntityType;

public class ChangeType {

    private final EntityType changedElement; // field, method, etc

    private final String     typeOfChange;   // insert, delete, update

    public ChangeType ( final EntityType changedElement, final String typeOfChange ) {
        this.changedElement = changedElement;
        this.typeOfChange = typeOfChange;
    }

    public EntityType getChangedElement () {
        return changedElement;
    }

    public String getTypeOfChange () {
        return typeOfChange;
    }

    @Override
    public String toString () {
        return String.format( "%s %s", typeOfChange, changedElement );
    }

    @Override
    public int hashCode () {
        return Objects.hash( changedElement, typeOfChange );
    }

    @Override
    public boolean equals ( final Object obj ) {
        if ( this == obj ) {
            return true;
        }
        if ( obj == null ) {
            return false;
        }
        if ( getClass() != obj.getClass() ) {
            return false;
        }
        final ChangeType other = (ChangeType) obj;
        return Objects.equals( changedElement, other.changedElement )
                && Objects.equals( typeOfChange, other.typeOfChange );
    }

}
