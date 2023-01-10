package ch.uzh.ifi.seal.changedistiller.distilling;

/*
 * #%L ChangeDistiller %% Copyright (C) 2011 - 2013 Software Architecture and
 * Evolution Lab, Department of Informatics, UZH %% Licensed under the Apache
 * License, Version 2.0 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License. #L%
 */

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;

import ch.uzh.ifi.seal.changedistiller.ast.java.JavaCompilation;
import ch.uzh.ifi.seal.changedistiller.model.classifiers.java.JavaEntityType;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeEntity;
import ch.uzh.ifi.seal.changedistiller.model.entities.StructureEntityVersion;
import ch.uzh.ifi.seal.changedistiller.model.entities.Update;
import ch.uzh.ifi.seal.changedistiller.treedifferencing.Node;
import ch.uzh.ifi.seal.changedistiller.util.CompilationUtils;

// simple test cases. exhaustive test cases with classification check in
// separate tests suite
public class WhenDeclarationChangesAreExtracted extends WhenChangesAreExtracted {

    private final static String TEST_DATA = "src_change/";

    @Test
    public void unchangedMethodDeclarationShouldNotHaveAnyChanges () throws Exception {
        final JavaCompilation compilation = CompilationUtils.compileFile( TEST_DATA + "TestLeft.java" );
        final Node rootLeft = convertMethodDeclaration( "foo", compilation );
        final Node rootRight = convertMethodDeclaration( "foo", compilation );
        final StructureEntityVersion structureEntity = new StructureEntityVersion( JavaEntityType.METHOD, "foo", 0 );
        final Distiller distiller = getDistiller( structureEntity );
        distiller.extractClassifiedSourceCodeChanges( rootLeft, rootRight );
        assertThat( structureEntity.getSourceCodeChanges().isEmpty(), is( true ) );
    }

    @Test
    public void changedMethodShouldHaveChanges () throws Exception {
        final JavaCompilation compilationLeft = CompilationUtils.compileFile( TEST_DATA + "TestLeft.java" );
        final JavaCompilation compilationRight = CompilationUtils.compileFile( TEST_DATA + "TestRight.java" );
        final Node rootLeft = convertMethodDeclaration( "foo", compilationLeft );
        final Node rootRight = convertMethodDeclaration( "foo", compilationRight );
        final StructureEntityVersion structureEntity = new StructureEntityVersion( JavaEntityType.METHOD, "foo", 0 );
        final Distiller distiller = getDistiller( structureEntity );
        distiller.extractClassifiedSourceCodeChanges( rootLeft, rootRight );
        assertThat( structureEntity.getSourceCodeChanges().size(), is( 2 ) );
    }

    @Test
    public void changedFieldShouldHaveChanges () throws Exception {
        final JavaCompilation compilationLeft = CompilationUtils.compileFile( TEST_DATA + "TestLeft.java" );
        final JavaCompilation compilationRight = CompilationUtils.compileFile( TEST_DATA + "TestRight.java" );
        final Node rootLeft = convertFieldDeclaration( "arrayField", compilationLeft );
        final Node rootRight = convertFieldDeclaration( "arrayField", compilationRight );
        final StructureEntityVersion structureEntity = new StructureEntityVersion( JavaEntityType.FIELD, "arrayField",
                0 );
        final Distiller distiller = getDistiller( structureEntity );
        distiller.extractClassifiedSourceCodeChanges( rootLeft, rootRight );

        final List<SourceCodeChange> changes = structureEntity.getSourceCodeChanges();
        assertThat( changes.size(), is( 1 ) );

        final SourceCodeChange singleChange = changes.get( 0 );

        if ( singleChange instanceof Update ) {
            final Update update = (Update) singleChange;
            final SourceCodeEntity entity = update.getNewEntity();
            assertThat( (JavaEntityType) entity.getType(), is( JavaEntityType.ARRAY_TYPE ) );
        }
        else {
            fail( "Should be Update but was " + singleChange.getClass() );
        }
    }

}
