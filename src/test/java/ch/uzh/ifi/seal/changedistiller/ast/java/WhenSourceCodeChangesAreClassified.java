package ch.uzh.ifi.seal.changedistiller.ast.java;

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

import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.lookup.ClassScope;
import org.eclipse.jdt.internal.compiler.lookup.MethodScope;
import org.junit.Test;

import ch.uzh.ifi.seal.changedistiller.distilling.Distiller;
import ch.uzh.ifi.seal.changedistiller.distilling.WhenChangesAreExtracted;
import ch.uzh.ifi.seal.changedistiller.model.classifiers.ChangeType;
import ch.uzh.ifi.seal.changedistiller.model.classifiers.SourceRange;
import ch.uzh.ifi.seal.changedistiller.model.classifiers.java.JavaEntityType;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeEntity;
import ch.uzh.ifi.seal.changedistiller.model.entities.StructureEntityVersion;
import ch.uzh.ifi.seal.changedistiller.treedifferencing.Node;
import ch.uzh.ifi.seal.changedistiller.util.CompilationUtils;

public class WhenSourceCodeChangesAreClassified extends WhenChangesAreExtracted {

    private static final String    METHOD_NAME = "method";

    private String                 fLeftSnippet;
    private String                 fRightSnippet;
    private StructureEntityVersion structureEntity;

    @Test
    public void addingAttributeModifiabilityShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "private final String fAddingAttributeModifiability;" );
        fRightSnippet = createSourceCode( "private String fAddingAttributeModifiability;" );
        extractFieldDeclarationChanges( "fAddingAttributeModifiability" );
        assertThat( getResultingChangeType(), is( ChangeType.ADDING_ATTRIBUTE_MODIFIABILITY ) );
    }

    @Test
    public void addingClassDerivabilityShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public final class AddingClassDerivability {}" );
        fRightSnippet = createSourceCode( "public class AddingClassDerivability {}" );
        extractClassDeclarationChanges( "AddingClassDerivability" );
        assertThat( getResultingChangeType(), is( ChangeType.ADDING_CLASS_DERIVABILITY ) );
    }

    @Test
    public void addingMethodOverrideabilityShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public final void addingMethodOverridability() {}" );
        fRightSnippet = createSourceCode( "public void addingMethodOverridability() {}" );
        extractMethodDeclarationChanges( "addingMethodOverridability" );
        assertThat( getResultingChangeType(), is( ChangeType.ADDING_METHOD_OVERRIDABILITY ) );
    }

    @Test
    public void alternativePartDeleteShouldBeDetected () throws Exception {
        fLeftSnippet = createMethodSourceCode(
                "if (alternativePartDelete == elseDelete) { alternativePartDelete(elseDelete); } else { }" );
        fRightSnippet = createMethodSourceCode(
                "if (alternativePartDelete == elseDelete) { alternativePartDelete(elseDelete); }" );
        extractMethodChanges( "method" );
        assertThat( getResultingChangeType(), is( ChangeType.ALTERNATIVE_PART_DELETE ) );
    }

    @Test
    public void alternativePartInsertShouldBeDetected () throws Exception {
        fLeftSnippet = createMethodSourceCode(
                "if (alternativePartInsert == elseInsert) { alternativePartInsert(elseInsert); }" );
        fRightSnippet = createMethodSourceCode(
                "if (alternativePartInsert == elseInsert) { alternativePartInsert(elseInsert); } else { }" );
        extractMethodChanges( "method" );
        assertThat( getResultingChangeType(), is( ChangeType.ALTERNATIVE_PART_INSERT ) );
    }

    @Test
    public void forInitInsertShouldBeDetected () throws Exception {
        fLeftSnippet = createMethodSourceCode( "for(; i < 10; i++) { System.out.println(i); }" );
        fRightSnippet = createMethodSourceCode( "for(int i = 0; i < 10; i++) { System.out.println(i); }" );
        extractMethodChanges( "method" );
        assertThat( getResultingChangeType(), is( ChangeType.STATEMENT_INSERT ) );
    }

    @Test
    public void forInitDeleteShouldBeDetected () throws Exception {
        fLeftSnippet = createMethodSourceCode( "for(int i = 0; i < 10; i++) { System.out.println(i); }" );
        fRightSnippet = createMethodSourceCode( "for( ; i < 10; i++) { System.out.println(i); }" );
        extractMethodChanges( "method" );
        assertThat( getResultingChangeType(), is( ChangeType.STATEMENT_DELETE ) );
    }

    @Test
    public void forIncrementInsertShouldBeDetected () throws Exception {
        fLeftSnippet = createMethodSourceCode( "for(int i = 0; i < 10;) { System.out.println(i); }" );
        fRightSnippet = createMethodSourceCode( "for(int i = 0; i < 10; i++) { System.out.println(i); }" );
        extractMethodChanges( "method" );
        assertThat( getResultingChangeType(), is( ChangeType.STATEMENT_INSERT ) );
    }

    @Test
    public void forIncrementDeleteShouldBeDetected () throws Exception {
        fLeftSnippet = createMethodSourceCode( "for(int i = 0; i < 10; i++) { System.out.println(i); }" );
        fRightSnippet = createMethodSourceCode( "for(int i = 0; i < 10;) { System.out.println(i); }" );
        extractMethodChanges( "method" );
        assertThat( getResultingChangeType(), is( ChangeType.STATEMENT_DELETE ) );
    }

    @Test
    public void statementInsertIntoSwitchShouldBeDetected () throws Exception {
        fLeftSnippet = createMethodSourceCode(
                "switch(var) { case 1: break; default: System.out.println(\"default\"); }" );
        fRightSnippet = createMethodSourceCode(
                "switch(var) { case 1: System.out.println(\"first case\"); break; default: System.out.println(\"default\"); }" );
        extractMethodChanges( "method" );
        assertThat( getResultingChangeType(), is( ChangeType.STATEMENT_INSERT ) );
    }

    @Test
    public void attributeTypeChangeShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "private Integer attributeTypeChange;" );
        fRightSnippet = createSourceCode( "private Object attributeTypeChange;" );
        extractFieldDeclarationChanges( "attributeTypeChange" );
        assertThat( getResultingChangeType(), is( ChangeType.ATTRIBUTE_TYPE_CHANGE ) );
    }

    @Test
    public void commentDeleteShouldBeDetected () throws Exception {
        fLeftSnippet = createMethodSourceCode( "// comment delete\ncomment.delete();" );
        fRightSnippet = createMethodSourceCode( "comment.delete();" );
        extractMethodChanges( "method" );
        assertThat( getResultingChangeType(), is( ChangeType.COMMENT_DELETE ) );
    }

    @Test
    public void commentInsertShouldBeDetected () throws Exception {
        fLeftSnippet = createMethodSourceCode( "comment.insert();" );
        fRightSnippet = createMethodSourceCode( "// comment insert\ncomment.insert();" );
        extractMethodChanges( "method" );
        assertThat( getResultingChangeType(), is( ChangeType.COMMENT_INSERT ) );
    }

    @Test
    public void commentMoveShouldBeDetected () throws Exception {
        fLeftSnippet = createMethodSourceCode(
                "if (commentMoveFrom) { /* comment move */ comment.moveFrom(); } if (commentMoveTo) { comment.moveTo(); }" );
        fRightSnippet = createMethodSourceCode(
                "if (commentMoveFrom) { comment.moveFrom(); } if (commentMoveTo) { /* comment move */ comment.moveTo(); }" );
        extractMethodChanges( "method" );
        assertThat( getResultingChangeType(), is( ChangeType.COMMENT_MOVE ) );
    }

    @Test
    public void commentUpdateShouldBeDetected () throws Exception {
        fLeftSnippet = createMethodSourceCode( "/* comment that will be updated */ comment.update();" );
        fRightSnippet = createMethodSourceCode( "/* comment that was updated */ comment.update();" );
        extractMethodChanges( "method" );
        assertThat( getResultingChangeType(), is( ChangeType.COMMENT_UPDATE ) );
    }

    @Test
    public void conditionExpressionChangeShouldBeDetected () throws Exception {
        fLeftSnippet = createMethodSourceCode( "if (conditioExpresionChang) {}" );
        fRightSnippet = createMethodSourceCode( "if (conditionExpressionChange) {}" );
        extractMethodChanges( "method" );
        assertThat( getResultingChangeType(), is( ChangeType.CONDITION_EXPRESSION_CHANGE ) );
    }

    @Test
    public void decreasingClassAccessiblityChangeFromPublicToProtectedShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public class DecreasingAccessibilityChangeFromPublicToProtected {}" );
        fRightSnippet = createSourceCode( "protected class DecreasingAccessibilityChangeFromPublicToProtected {}" );
        extractClassDeclarationChanges( "DecreasingAccessibilityChangeFromPublicToProtected" );
        assertThat( getResultingChangeType(), is( ChangeType.DECREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void decreasingClassAccessiblityChangeFromPublicToPackageShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public class DecreasingAccessibilityChangeFromPublicToPackage {}" );
        fRightSnippet = createSourceCode( "class DecreasingAccessibilityChangeFromPublicToPackage {}" );
        extractClassDeclarationChanges( "DecreasingAccessibilityChangeFromPublicToPackage" );
        assertThat( getResultingChangeType(), is( ChangeType.DECREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void decreasingClassAccessiblityChangeFromPublicToPrivateShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public class DecreasingAccessibilityChangeFromPublicToPrivate {}" );
        fRightSnippet = createSourceCode( "private class DecreasingAccessibilityChangeFromPublicToPrivate {}" );
        extractClassDeclarationChanges( "DecreasingAccessibilityChangeFromPublicToPrivate" );
        assertThat( getResultingChangeType(), is( ChangeType.DECREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void decreasingClassAccessiblityChangeFromProtectedToPackageShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "protected class DecreasingAccessibilityChangeFromProtectedToPackage {}" );
        fRightSnippet = createSourceCode( "class DecreasingAccessibilityChangeFromProtectedToPackage {}" );
        extractClassDeclarationChanges( "DecreasingAccessibilityChangeFromProtectedToPackage" );
        assertThat( getResultingChangeType(), is( ChangeType.DECREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void decreasingClassAccessiblityChangeFromProtectedToPrivateShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "protected class DecreasingAccessibilityChangeFromProtectedToPrivate {}" );
        fRightSnippet = createSourceCode( "private class DecreasingAccessibilityChangeFromProtectedToPrivate {}" );
        extractClassDeclarationChanges( "DecreasingAccessibilityChangeFromProtectedToPrivate" );
        assertThat( getResultingChangeType(), is( ChangeType.DECREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void decreasingClassAccessiblityChangeFromPackageToPrivateShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "class DecreasingAccessibilityChangeFromPackageToPrivate {}" );
        fRightSnippet = createSourceCode( "private class DecreasingAccessibilityChangeFromPackageToPrivate {}" );
        extractClassDeclarationChanges( "DecreasingAccessibilityChangeFromPackageToPrivate" );
        assertThat( getResultingChangeType(), is( ChangeType.DECREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void decreasingMethodAccessiblityChangeFromPublicToProtectedShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public void decreasingAccessibilityChangeFromPublicToProtected() {}" );
        fRightSnippet = createSourceCode( "protected void decreasingAccessibilityChangeFromPublicToProtected() {}" );
        extractMethodDeclarationChanges( "decreasingAccessibilityChangeFromPublicToProtected" );
        assertThat( getResultingChangeType(), is( ChangeType.DECREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void decreasingMethodAccessiblityChangeFromPublicToPackageShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public void decreasingAccessibilityChangeFromPublicToPackage() {}" );
        fRightSnippet = createSourceCode( "void decreasingAccessibilityChangeFromPublicToPackage() {}" );
        extractMethodDeclarationChanges( "decreasingAccessibilityChangeFromPublicToPackage" );
        assertThat( getResultingChangeType(), is( ChangeType.DECREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void decreasingMethodAccessiblityChangeFromPublicToPrivateShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public void decreasingAccessibilityChangeFromPublicToPrivate() {}" );
        fRightSnippet = createSourceCode( "private void decreasingAccessibilityChangeFromPublicToPrivate() {}" );
        extractMethodDeclarationChanges( "decreasingAccessibilityChangeFromPublicToPrivate" );
        assertThat( getResultingChangeType(), is( ChangeType.DECREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void decreasingMethodAccessiblityChangeFromProtectedToPackageShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "protected void decreasingAccessibilityChangeFromProtectedToPackage() {}" );
        fRightSnippet = createSourceCode( "void decreasingAccessibilityChangeFromProtectedToPackage() {}" );
        extractMethodDeclarationChanges( "decreasingAccessibilityChangeFromProtectedToPackage" );
        assertThat( getResultingChangeType(), is( ChangeType.DECREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void decreasingMethodAccessiblityChangeFromProtectedToPrivateShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "protected void decreasingAccessibilityChangeFromProtectedToPrivate() {}" );
        fRightSnippet = createSourceCode( "private void decreasingAccessibilityChangeFromProtectedToPrivate() {}" );
        extractMethodDeclarationChanges( "decreasingAccessibilityChangeFromProtectedToPrivate" );
        assertThat( getResultingChangeType(), is( ChangeType.DECREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void decreasingMethodAccessiblityChangeFromPackageToPrivateShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "void decreasingAccessibilityChangeFromPackageToPrivate() {}" );
        fRightSnippet = createSourceCode( "private void decreasingAccessibilityChangeFromPackageToPrivate() {}" );
        extractMethodDeclarationChanges( "decreasingAccessibilityChangeFromPackageToPrivate" );
        assertThat( getResultingChangeType(), is( ChangeType.DECREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void decreasingFieldAccessibilityChangeFromPublicToProtectedShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public Object fDecreasingAccessibilityChangeFromPublicToProtected;" );
        fRightSnippet = createSourceCode( "protected Object fDecreasingAccessibilityChangeFromPublicToProtected;" );
        extractFieldDeclarationChanges( "fDecreasingAccessibilityChangeFromPublicToProtected" );
        assertThat( getResultingChangeType(), is( ChangeType.DECREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void decreasingFieldAccessibilityChangeFromPublicToPackageShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public Object fDecreasingAccessibilityChangeFromPublicToPackage;" );
        fRightSnippet = createSourceCode( "Object fDecreasingAccessibilityChangeFromPublicToPackage;" );
        extractFieldDeclarationChanges( "fDecreasingAccessibilityChangeFromPublicToPackage" );
        assertThat( getResultingChangeType(), is( ChangeType.DECREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void decreasingFieldAccessibilityChangeFromPublicToPrivateShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public Object fDecreasingAccessibilityChangeFromPublicToPrivate;" );
        fRightSnippet = createSourceCode( "private Object fDecreasingAccessibilityChangeFromPublicToPrivate;" );
        extractFieldDeclarationChanges( "fDecreasingAccessibilityChangeFromPublicToPrivate" );
        assertThat( getResultingChangeType(), is( ChangeType.DECREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void decreasingFieldAccessibilityChangeFromProtectedToPackageShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "protected Object fDecreasingAccessibilityChangeFromProtectedToPackage;" );
        fRightSnippet = createSourceCode( "Object fDecreasingAccessibilityChangeFromProtectedToPackage;" );
        extractFieldDeclarationChanges( "fDecreasingAccessibilityChangeFromProtectedToPackage" );
        assertThat( getResultingChangeType(), is( ChangeType.DECREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void decreasingFieldAccessibilityChangeFromProtectedToPrivateShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "protected Object fDecreasingAccessibilityChangeFromProtectedToPrivate;" );
        fRightSnippet = createSourceCode( "private Object fDecreasingAccessibilityChangeFromProtectedToPrivate;" );
        extractFieldDeclarationChanges( "fDecreasingAccessibilityChangeFromProtectedToPrivate" );
        assertThat( getResultingChangeType(), is( ChangeType.DECREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void decreasingFieldAccessibilityChangeFromPackageToPrivateShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "Object fDecreasingAccessibilityChangeFromPackageToPrivate;" );
        fRightSnippet = createSourceCode( "private Object fDecreasingAccessibilityChangeFromPackageToPrivate;" );
        extractFieldDeclarationChanges( "fDecreasingAccessibilityChangeFromPackageToPrivate" );
        assertThat( getResultingChangeType(), is( ChangeType.DECREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void classDocDeleteShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "/**\n* Doc to delete\n*/\npublic class ClassToCheckDocDelete {}" );
        fRightSnippet = createSourceCode( "public class ClassToCheckDocDelete {}" );
        extractClassDeclarationChanges( "ClassToCheckDocDelete" );
        assertThat( getResultingChangeType(), is( ChangeType.DOC_DELETE ) );
    }

    @Test
    public void classDocInsertShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public class ClassToCheckDocInsert {}" );
        fRightSnippet = createSourceCode( "/**\n* Doc to insert\n*/\npublic class ClassToCheckDocInsert {}" );
        extractClassDeclarationChanges( "ClassToCheckDocInsert" );
        assertThat( getResultingChangeType(), is( ChangeType.DOC_INSERT ) );
    }

    @Test
    public void classDocUpdateShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "/**\n* Doc that will be updated\n*/\npublic class ClassToCheckDocUpdate {}" );
        fRightSnippet = createSourceCode( "/**\n* Doc that was updated\n*/\npublic class ClassToCheckDocUpdate {}" );
        extractClassDeclarationChanges( "ClassToCheckDocUpdate" );
        assertThat( getResultingChangeType(), is( ChangeType.DOC_UPDATE ) );
    }

    @Test
    public void methodDocDeleteShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "/**\n* Doc to delete\n*/\npublic void methodToCheckDocDelete() {}" );
        fRightSnippet = createSourceCode( "public void methodToCheckDocDelete() {}" );
        extractMethodDeclarationChanges( "methodToCheckDocDelete" );
        assertThat( getResultingChangeType(), is( ChangeType.DOC_DELETE ) );
    }

    @Test
    public void methodDocInsertShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public void methodToCheckDocInsert() {}" );
        fRightSnippet = createSourceCode( "/**\n* Doc to insert\n*/\npublic void methodToCheckDocInsert() {}" );
        extractMethodDeclarationChanges( "methodToCheckDocInsert" );
        assertThat( getResultingChangeType(), is( ChangeType.DOC_INSERT ) );
    }

    @Test
    public void methodDocUpdateShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode(
                "/**\n* Doc that will be updated\n*/\npublic void methodToCheckDocUpdate() {}" );
        fRightSnippet = createSourceCode( "/**\n* Doc that was updated\n*/\npublic void methodToCheckDocUpdate() {}" );
        extractMethodDeclarationChanges( "methodToCheckDocUpdate" );
        assertThat( getResultingChangeType(), is( ChangeType.DOC_UPDATE ) );
    }

    @Test
    public void fieldDocDeleteShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "/**\n* Doc to delete\n*/\nprivate Object fAttributeToCheckDocDelete;" );
        fRightSnippet = createSourceCode( "private Object fAttributeToCheckDocDelete;" );
        extractFieldDeclarationChanges( "fAttributeToCheckDocDelete" );
        assertThat( getResultingChangeType(), is( ChangeType.DOC_DELETE ) );
    }

    @Test
    public void fieldDocInsertShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "private Object fAttributeToCheckDocInsert;" );
        fRightSnippet = createSourceCode( "/**\n* Doc to insert\n*/\nprivate Object fAttributeToCheckDocInsert;" );
        extractFieldDeclarationChanges( "fAttributeToCheckDocInsert" );
        assertThat( getResultingChangeType(), is( ChangeType.DOC_INSERT ) );
    }

    @Test
    public void fieldDocUpdateShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode(
                "/**\n* Doc that will be updated\n*/\nprivate Object fAttributeToCheckDocUpdate;" );
        fRightSnippet = createSourceCode(
                "/**\n* Doc that was updated\n*/\nprivate Object fAttributeToCheckDocUpdate;" );
        extractFieldDeclarationChanges( "fAttributeToCheckDocUpdate" );
        assertThat( getResultingChangeType(), is( ChangeType.DOC_UPDATE ) );
    }

    @Test
    public void increasingClassAccessiblityChangeFromPrivateToProtectedShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "private class IncreasingAccessibilityChangeFromPrivateToProtected {}" );
        fRightSnippet = createSourceCode( "protected class IncreasingAccessibilityChangeFromPrivateToProtected {}" );
        extractClassDeclarationChanges( "IncreasingAccessibilityChangeFromPrivateToProtected" );
        assertThat( getResultingChangeType(), is( ChangeType.INCREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void increasingClassAccessiblityChangeFromPrivateToPackageShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "private class IncreasingAccessibilityChangeFromPrivateToPackage {}" );
        fRightSnippet = createSourceCode( "class IncreasingAccessibilityChangeFromPrivateToPackage {}" );
        extractClassDeclarationChanges( "IncreasingAccessibilityChangeFromPrivateToPackage" );
        assertThat( getResultingChangeType(), is( ChangeType.INCREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void increasingClassAccessiblityChangeFromPrivateToPublicShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "private class IncreasingAccessibilityChangeFromPrivateToPublic {}" );
        fRightSnippet = createSourceCode( "public class IncreasingAccessibilityChangeFromPrivateToPublic {}" );
        extractClassDeclarationChanges( "IncreasingAccessibilityChangeFromPrivateToPublic" );
        assertThat( getResultingChangeType(), is( ChangeType.INCREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void increasingClassAccessiblityChangeFromPackageToProtectedShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "class IncreasingAccessibilityChangeFromPackageToProtected {}" );
        fRightSnippet = createSourceCode( "protected class IncreasingAccessibilityChangeFromPackageToProtected {}" );
        extractClassDeclarationChanges( "IncreasingAccessibilityChangeFromPackageToProtected" );
        assertThat( getResultingChangeType(), is( ChangeType.INCREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void increasingClassAccessiblityChangeFromPackageToPublicShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "class IncreasingAccessibilityChangeFromPackageToPublic {}" );
        fRightSnippet = createSourceCode( "public class IncreasingAccessibilityChangeFromPackageToPublic {}" );
        extractClassDeclarationChanges( "IncreasingAccessibilityChangeFromPackageToPublic" );
        assertThat( getResultingChangeType(), is( ChangeType.INCREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void increasingClassAccessiblityChangeFromProtectedToPublicShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "protected class IncreasingAccessibilityChangeFromProtectedToProtected {}" );
        fRightSnippet = createSourceCode( "public class IncreasingAccessibilityChangeFromProtectedToProtected {}" );
        extractClassDeclarationChanges( "IncreasingAccessibilityChangeFromProtectedToProtected" );
        assertThat( getResultingChangeType(), is( ChangeType.INCREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void increasingFieldAccessibilityChangeFromPrivateToPackageShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "private Object fIncreasingAccessibilityChangeFromPrivateToPackage;" );
        fRightSnippet = createSourceCode( "Object fIncreasingAccessibilityChangeFromPrivateToPackage;" );
        extractFieldDeclarationChanges( "fIncreasingAccessibilityChangeFromPrivateToPackage" );
        assertThat( getResultingChangeType(), is( ChangeType.INCREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void increasingFieldAccessibilityChangeFromPrivateToProtectedShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "private Object fIncreasingAccessibilityChangeFromPrivateToProtected;" );
        fRightSnippet = createSourceCode( "protected Object fIncreasingAccessibilityChangeFromPrivateToProtected;" );
        extractFieldDeclarationChanges( "fIncreasingAccessibilityChangeFromPrivateToProtected" );
        assertThat( getResultingChangeType(), is( ChangeType.INCREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void increasingFieldAccessibilityChangeFromPrivateToPublicShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "private Object fIncreasingAccessibilityChangeFromPrivateToPublic;" );
        fRightSnippet = createSourceCode( "public Object fIncreasingAccessibilityChangeFromPrivateToPublic;" );
        extractFieldDeclarationChanges( "fIncreasingAccessibilityChangeFromPrivateToPublic" );
        assertThat( getResultingChangeType(), is( ChangeType.INCREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void increasingFieldAccessibilityChangeFromPackageToProtectedShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "Object fIncreasingAccessibilityChangeFromPackageToProtected;" );
        fRightSnippet = createSourceCode( "protected Object fIncreasingAccessibilityChangeFromPackageToProtected;" );
        extractFieldDeclarationChanges( "fIncreasingAccessibilityChangeFromPackageToProtected" );
        assertThat( getResultingChangeType(), is( ChangeType.INCREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void increasingFieldAccessibilityChangeFromPackageToPublicShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "Object fIncreasingAccessibilityChangeFromPackageToPublic;" );
        fRightSnippet = createSourceCode( "public Object fIncreasingAccessibilityChangeFromPackageToPublic;" );
        extractFieldDeclarationChanges( "fIncreasingAccessibilityChangeFromPackageToPublic" );
        assertThat( getResultingChangeType(), is( ChangeType.INCREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void increasingFieldAccessibilityChangeFromProtectedToPublicShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "Object fIncreasingAccessibilityChangeFromProtectedToPublic;" );
        fRightSnippet = createSourceCode( "public Object fIncreasingAccessibilityChangeFromProtectedToPublic;" );
        extractFieldDeclarationChanges( "fIncreasingAccessibilityChangeFromProtectedToPublic" );
        assertThat( getResultingChangeType(), is( ChangeType.INCREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void increasingMethodAccessiblityChangeFromPrivateToProtectedShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "private void increasingAccessibilityChangeFromPrivateToProtected() {}" );
        fRightSnippet = createSourceCode( "protected void increasingAccessibilityChangeFromPrivateToProtected() {}" );
        extractMethodDeclarationChanges( "increasingAccessibilityChangeFromPrivateToProtected" );
        assertThat( getResultingChangeType(), is( ChangeType.INCREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void increasingMethodAccessiblityChangeFromPrivateToPackageShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "private void increasingAccessibilityChangeFromPrivateToPackage() {}" );
        fRightSnippet = createSourceCode( "void increasingAccessibilityChangeFromPrivateToPackage() {}" );
        extractMethodDeclarationChanges( "increasingAccessibilityChangeFromPrivateToPackage" );
        assertThat( getResultingChangeType(), is( ChangeType.INCREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void increasingMethodAccessiblityChangeFromPrivateToPublicShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "private void increasingAccessibilityChangeFromPrivateToPublic() {}" );
        fRightSnippet = createSourceCode( "public void increasingAccessibilityChangeFromPrivateToPublic() {}" );
        extractMethodDeclarationChanges( "increasingAccessibilityChangeFromPrivateToPublic" );
        assertThat( getResultingChangeType(), is( ChangeType.INCREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void increasingMethodAccessiblityChangeFromPackageToProtectedShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "void increasingAccessibilityChangeFromPackageToProtected() {}" );
        fRightSnippet = createSourceCode( "protected void increasingAccessibilityChangeFromPackageToProtected() {}" );
        extractMethodDeclarationChanges( "increasingAccessibilityChangeFromPackageToProtected" );
        assertThat( getResultingChangeType(), is( ChangeType.INCREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void increasingMethodAccessiblityChangeFromPackageToPublicShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "void increasingAccessibilityChangeFromPackageToPublic() {}" );
        fRightSnippet = createSourceCode( "public void increasingAccessibilityChangeFromPackageToPublic() {}" );
        extractMethodDeclarationChanges( "increasingAccessibilityChangeFromPackageToPublic" );
        assertThat( getResultingChangeType(), is( ChangeType.INCREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void increasingMethodAccessiblityChangeFromPublicToPublicShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "protected void increasingAccessibilityChangeFromPublicToPublic() {}" );
        fRightSnippet = createSourceCode( "public void increasingAccessibilityChangeFromPublicToPublic() {}" );
        extractMethodDeclarationChanges( "increasingAccessibilityChangeFromPublicToPublic" );
        assertThat( getResultingChangeType(), is( ChangeType.INCREASING_ACCESSIBILITY_CHANGE ) );
    }

    @Test
    public void parameterDeleteShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public void methodToCheckParameterDelete(int aInt) {}" );
        fRightSnippet = createSourceCode( "public void methodToCheckParameterDelete() {}" );
        extractMethodDeclarationChanges( "methodToCheckParameterDelete" );
        assertThat( getResultingChangeType(), is( ChangeType.PARAMETER_DELETE ) );
    }

    @Test
    public void parameterInsertShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public void methodToCheckParameterInsert() {}" );
        fRightSnippet = createSourceCode( "public void methodToCheckParameterInsert(int aInt) {}" );
        extractMethodDeclarationChanges( "methodToCheckParameterInsert" );
        assertThat( getResultingChangeType(), is( ChangeType.PARAMETER_INSERT ) );
    }

    @Test
    public void parameterOrderingChangeShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode(
                "public void methodToCheckParameterOrderingChange(int first, int second, float aMovedParam) {}" );
        fRightSnippet = createSourceCode(
                "public void methodToCheckParameterOrderingChange(int first, float aMovedParam, int second) {}" );
        extractMethodDeclarationChanges( "methodToCheckParameterOrderingChange" );
        assertThat( getResultingChangeType(), is( ChangeType.PARAMETER_ORDERING_CHANGE ) );
    }

    @Test
    public void parameterRenamingShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public void methodToCheckParameterRenaming(Object aRenamePara) {}" );
        fRightSnippet = createSourceCode( "public void methodToCheckParameterRenaming(Object aRenamedParam) {}" );
        extractMethodDeclarationChanges( "methodToCheckParameterRenaming" );
        assertThat( getResultingChangeType(), is( ChangeType.PARAMETER_RENAMING ) );
    }

    @Test
    public void parameterTypeChangeShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode(
                "public void methodToCheckParameterTypeChange(Object first, int typeChange) {}" );
        fRightSnippet = createSourceCode(
                "public void methodToCheckParameterTypeChange(Object first, String typeChange) {}" );
        extractMethodDeclarationChanges( "methodToCheckParameterTypeChange" );
        assertThat( getResultingChangeType(), is( ChangeType.PARAMETER_TYPE_CHANGE ) );
    }

    @Test
    public void parentClassChangeShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public class ClassToCheckParentClassChange extends Object {}" );
        fRightSnippet = createSourceCode( "public class ClassToCheckParentClassChange extends String {}" );
        extractClassDeclarationChanges( "ClassToCheckParentClassChange" );
        assertThat( getResultingChangeType(), is( ChangeType.PARENT_CLASS_CHANGE ) );
    }

    @Test
    public void parentClassDeleteShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public class ClassToCheckParentClassDelete extends ParentToDelete {}" );
        fRightSnippet = createSourceCode( "public class ClassToCheckParentClassDelete {}" );
        extractClassDeclarationChanges( "ClassToCheckParentClassDelete" );
        assertThat( getResultingChangeType(), is( ChangeType.PARENT_CLASS_DELETE ) );
    }

    @Test
    public void parentClassInsertShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public class ClassToCheckParentClassInsert {}" );
        fRightSnippet = createSourceCode( "public class ClassToCheckParentClassInsert extends ParentToInsert {}" );
        extractClassDeclarationChanges( "ClassToCheckParentClassInsert" );
        assertThat( getResultingChangeType(), is( ChangeType.PARENT_CLASS_INSERT ) );
    }

    @Test
    public void parentInterfaceChangeShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode(
                "public class ClassToCheckParentInterfaceChange implements IIntefaceChang {}" );
        fRightSnippet = createSourceCode(
                "public class ClassToCheckParentInterfaceChange implements IInterfaceChange {}" );
        extractClassDeclarationChanges( "ClassToCheckParentInterfaceChange" );
        assertThat( getResultingChangeType(), is( ChangeType.PARENT_INTERFACE_CHANGE ) );
    }

    @Test
    public void parentInterfaceDeleteShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode(
                "public class ClassToCheckParentInterfaceDelete implements ParentInterfaceToDelete, IInterface {}" );
        fRightSnippet = createSourceCode( "public class ClassToCheckParentInterfaceDelete implements IInterface {}" );
        extractClassDeclarationChanges( "ClassToCheckParentInterfaceDelete" );
        assertThat( getResultingChangeType(), is( ChangeType.PARENT_INTERFACE_DELETE ) );
    }

    @Test
    public void parentInterfaceInsertShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public class ClassToCheckParentInterfaceInsert implements IInterface {}" );
        fRightSnippet = createSourceCode(
                "public class ClassToCheckParentInterfaceInsert implements ParentInterfaceToInsert, IInterface {}" );
        extractClassDeclarationChanges( "ClassToCheckParentInterfaceInsert" );
        assertThat( getResultingChangeType(), is( ChangeType.PARENT_INTERFACE_INSERT ) );
    }

    @Test
    public void removingAttributeModifiabilityShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "private String fRemovingAttributeModifiability;" );
        fRightSnippet = createSourceCode( "private final String fRemovingAttributeModifiability;" );
        extractFieldDeclarationChanges( "fRemovingAttributeModifiability" );
        assertThat( getResultingChangeType(), is( ChangeType.REMOVING_ATTRIBUTE_MODIFIABILITY ) );
    }

    @Test
    public void removingClassDerivabilityShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public class RemovingClassDerivability {}" );
        fRightSnippet = createSourceCode( "public final class RemovingClassDerivability {}" );
        extractClassDeclarationChanges( "RemovingClassDerivability" );
        assertThat( getResultingChangeType(), is( ChangeType.REMOVING_CLASS_DERIVABILITY ) );
    }

    @Test
    public void removingMethodOverrideabilityShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public void removingMethodOverridability() {}" );
        fRightSnippet = createSourceCode( "public final void removingMethodOverridability() {}" );
        extractMethodDeclarationChanges( "removingMethodOverridability" );
        assertThat( getResultingChangeType(), is( ChangeType.REMOVING_METHOD_OVERRIDABILITY ) );
    }

    @Test
    public void returnTypeChangeShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public Object methodToCheckReturnTypeChange() {}" );
        fRightSnippet = createSourceCode( "public String methodToCheckReturnTypeChange() {}" );
        extractMethodDeclarationChanges( "methodToCheckReturnTypeChange" );
        assertThat( getResultingChangeType(), is( ChangeType.RETURN_TYPE_CHANGE ) );
    }

    @Test
    public void returnTypeDeleteShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public int methodToCheckReturnTypeDelete() {}" );
        fRightSnippet = createSourceCode( "public void methodToCheckReturnTypeDelete() {}" );
        extractMethodDeclarationChanges( "methodToCheckReturnTypeDelete" );
        assertThat( getResultingChangeType(), is( ChangeType.RETURN_TYPE_DELETE ) );
    }

    @Test
    public void returnTypeInsertShouldBeDetected () throws Exception {
        fLeftSnippet = createSourceCode( "public void methodToCheckReturnTypeInsert() {}" );
        fRightSnippet = createSourceCode( "public int methodToCheckReturnTypeInsert() {}" );
        extractMethodDeclarationChanges( "methodToCheckReturnTypeInsert" );
        assertThat( getResultingChangeType(), is( ChangeType.RETURN_TYPE_INSERT ) );
    }

    @Test
    public void statementInsertShouldBeDetected () throws Exception {
        fLeftSnippet = createMethodSourceCode( "System.out.println();" );
        fRightSnippet = createMethodSourceCode( "System.out.println(); statement.insert();" );
        extractMethodChanges( "method" );
        assertThat( getResultingChangeType(), is( ChangeType.STATEMENT_INSERT ) );
    }

    @Test
    public void postfixExpressionInsertShouldBeDetected () throws Exception {
        fLeftSnippet = createMethodSourceCode( "System.out.println();" );
        fRightSnippet = createMethodSourceCode( "System.out.println(); this.insert++;" );
        extractMethodChanges( "method" );
        assertThat( getResultingChangeType(), is( ChangeType.STATEMENT_INSERT ) );
    }

    @Test
    public void postfixExpressionUpdateShouldBeDetected () throws Exception {
        fLeftSnippet = createMethodSourceCode( "System.out.println(); this.counter++;" );
        fRightSnippet = createMethodSourceCode( "System.out.println(); this.counter--;" );
        extractMethodChanges( "method" );
        assertThat( getResultingChangeType(), is( ChangeType.STATEMENT_UPDATE ) );
    }

    @Test
    public void prefixExpressionUpdateShouldBeDetected () throws Exception {
        fLeftSnippet = createMethodSourceCode( "System.out.println(); ++this.counter;" );
        fRightSnippet = createMethodSourceCode( "System.out.println(); --this.counter;" );
        extractMethodChanges( "method" );
        assertThat( getResultingChangeType(), is( ChangeType.STATEMENT_UPDATE ) );
    }

    @Test
    public void prefixExpressionInsertShouldBeDetected () throws Exception {
        fLeftSnippet = createMethodSourceCode( "System.out.println();" );
        fRightSnippet = createMethodSourceCode( "System.out.println(); ++this.insert;" );
        extractMethodChanges( "method" );
        assertThat( getResultingChangeType(), is( ChangeType.STATEMENT_INSERT ) );
    }

    @Test
    public void statementDeleteShouldBeDetected () throws Exception {
        fLeftSnippet = createMethodSourceCode( "System.out.println(); statement.delete();" );
        fRightSnippet = createMethodSourceCode( "System.out.println();" );
        extractMethodChanges( "method" );
        assertThat( getResultingChangeType(), is( ChangeType.STATEMENT_DELETE ) );
    }

    @Test
    public void postfixExpressionDeleteShouldBeDetected () throws Exception {
        fLeftSnippet = createMethodSourceCode( "System.out.println(); this.delete++;" );
        fRightSnippet = createMethodSourceCode( "System.out.println();" );
        extractMethodChanges( "method" );
        assertThat( getResultingChangeType(), is( ChangeType.STATEMENT_DELETE ) );
    }

    @Test
    public void prefixExpressionDeleteShouldBeDetected () throws Exception {
        fLeftSnippet = createMethodSourceCode( "System.out.println(); ++this.delete;" );
        fRightSnippet = createMethodSourceCode( "System.out.println();" );
        extractMethodChanges( "method" );
        assertThat( getResultingChangeType(), is( ChangeType.STATEMENT_DELETE ) );
    }

    @Test
    public void statementOrderingChangeShouldBeDetected () throws Exception {
        fLeftSnippet = createMethodSourceCode( "statement.ordering(); System.out.println(); aMethod.uberL33t();" );
        fRightSnippet = createMethodSourceCode( "System.out.println(); aMethod.uberL33t(); statement.ordering();" );
        extractMethodChanges( "method" );
        assertThat( getResultingChangeType(), is( ChangeType.STATEMENT_ORDERING_CHANGE ) );
    }

    @Test
    public void statementParentChangeShouldBeDetected () throws Exception {
        fLeftSnippet = createMethodSourceCode(
                "System.out.println(); statement.parent(); if (daNewParent == true) { aMethod.uberL33t(); foo.bar(); wow.kungen(); }" );
        fRightSnippet = createMethodSourceCode(
                "System.out.println(); if (daNewParent == true) { aMethod.uberL33t(); statement.parent(); foo.bar(); wow.kungen(); }" );
        extractMethodChanges( "method" );
        assertThat( getResultingChangeType(), is( ChangeType.STATEMENT_PARENT_CHANGE ) );
    }

    @Test
    public void statementUpdateShouldBeDetected () throws Exception {
        fLeftSnippet = createMethodSourceCode( "System.out.println(); statment.updae();" );
        fRightSnippet = createMethodSourceCode( "System.out.println(); statement.update();" );
        extractMethodChanges( "method" );
        assertThat( getResultingChangeType(), is( ChangeType.STATEMENT_UPDATE ) );
    }

    private ChangeType getResultingChangeType () {
        return structureEntity.getSourceCodeChanges().get( 0 ).getChangeType();
    }

    private String createMethodSourceCode ( final String methodBody ) {
        final StringBuilder methodSource = new StringBuilder();
        methodSource.append( "void " );
        methodSource.append( METHOD_NAME );
        methodSource.append( "() { " );
        methodSource.append( methodBody );
        methodSource.append( " }" );
        return createSourceCode( methodSource.toString() );
    }

    private String createSourceCode ( final String snippet ) {
        return "public class Foo { " + snippet + " }";
    }

    private Node convertMethodBody ( final String methodName, final String sourceCode ) {
        return convertMethodBody( methodName, CompilationUtils.compileSource( sourceCode ) );
    }

    private Node convertMethodDeclaration ( final String methodName, final String sourceCode ) {
        return convertMethodDeclaration( methodName, CompilationUtils.compileSource( sourceCode ) );
    }

    private Node convertFieldDeclaration ( final String fieldName, final String sourceCode ) {
        final JavaCompilation compilation = CompilationUtils.compileSource( sourceCode );
        final FieldDeclaration field = CompilationUtils.findField( compilation.getCompilationUnit(), fieldName );
        final Node root = new Node( JavaEntityType.FIELD, fieldName );
        root.setEntity( new SourceCodeEntity( fieldName, JavaEntityType.FIELD,
                new SourceRange( field.declarationSourceStart, field.declarationSourceEnd ) ) );
        sDeclarationConverter.initialize( root, compilation.getScanner() );
        field.traverse( sDeclarationConverter, (MethodScope) null );
        return root;
    }

    private Node convertClassDeclaration ( final String className, final String sourceCode ) {
        final JavaCompilation compilation = CompilationUtils.compileSource( sourceCode );
        final TypeDeclaration type = CompilationUtils.findType( compilation.getCompilationUnit(), className );
        final Node root = new Node( JavaEntityType.CLASS, className );
        root.setEntity( new SourceCodeEntity( className, JavaEntityType.CLASS,
                new SourceRange( type.declarationSourceStart, type.declarationSourceEnd ) ) );
        sDeclarationConverter.initialize( root, compilation.getScanner() );
        type.traverse( sDeclarationConverter, (ClassScope) null );
        return root;
    }

    private void extractClassDeclarationChanges ( final String className ) {
        final Node leftDeclaration = convertClassDeclaration( className, fLeftSnippet );
        final Node rootDeclaration = convertClassDeclaration( className, fRightSnippet );
        structureEntity = new StructureEntityVersion( JavaEntityType.CLASS, className, 0 );
        distill( leftDeclaration, rootDeclaration );
    }

    private void extractFieldDeclarationChanges ( final String fieldName ) {
        final Node leftDeclaration = convertFieldDeclaration( fieldName, fLeftSnippet );
        final Node rootDeclaration = convertFieldDeclaration( fieldName, fRightSnippet );
        structureEntity = new StructureEntityVersion( JavaEntityType.FIELD, fieldName, 0 );
        distill( leftDeclaration, rootDeclaration );
    }

    private void extractMethodDeclarationChanges ( final String methodName ) {
        final Node leftDeclaration = convertMethodDeclaration( methodName, fLeftSnippet );
        final Node rootDeclaration = convertMethodDeclaration( methodName, fRightSnippet );
        structureEntity = new StructureEntityVersion( JavaEntityType.METHOD, methodName, 0 );
        distill( leftDeclaration, rootDeclaration );
    }

    private void extractMethodChanges ( final String methodName ) {
        final Node leftMethod = convertMethodBody( methodName, fLeftSnippet );
        final Node rightMethod = convertMethodBody( methodName, fRightSnippet );
        structureEntity = new StructureEntityVersion( JavaEntityType.METHOD, methodName, 0 );
        distill( leftMethod, rightMethod );
    }

    private void distill ( final Node leftMethod, final Node rightMethod ) {
        final Distiller distiller = getDistiller( structureEntity );
        distiller.extractClassifiedSourceCodeChanges( leftMethod, rightMethod );
    }

}
