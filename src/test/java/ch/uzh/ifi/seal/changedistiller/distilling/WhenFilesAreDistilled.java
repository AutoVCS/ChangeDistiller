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
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import ch.uzh.ifi.seal.changedistiller.ChangeDistiller;
import ch.uzh.ifi.seal.changedistiller.ChangeDistiller.Language;
import ch.uzh.ifi.seal.changedistiller.model.entities.ClassHistory;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;
import ch.uzh.ifi.seal.changedistiller.util.CompilationUtils;

public class WhenFilesAreDistilled {

    private static final String  TEST_DATA = "src_change/";
    private static FileDistiller distiller;

    @BeforeClass
    public static void initialize () {
        distiller = ChangeDistiller.createFileDistiller( Language.JAVA );
    }

    @Test
    public void unchangedFilesShouldNotProduceSourceCodeChanges () throws Exception {
        final File left = CompilationUtils.getFile( TEST_DATA + "TestLeft.java" );
        final File right = CompilationUtils.getFile( TEST_DATA + "TestLeft.java" );
        distiller.extractClassifiedSourceCodeChanges( left, right );
        final List<SourceCodeChange> changes = distiller.getSourceCodeChanges();
        assertThat( changes, is( not( nullValue() ) ) );
        assertThat( changes.size(), is( 0 ) );
    }

    @Test
    public void changedFilesShouldProduceSourceCodeChanges () throws Exception {
        final File left = CompilationUtils.getFile( TEST_DATA + "TestLeft.java" );
        final File right = CompilationUtils.getFile( TEST_DATA + "TestRight.java" );
        distiller.extractClassifiedSourceCodeChanges( left, right );
        assertThat( distiller.getSourceCodeChanges().size(), is( 23 ) );
    }

    @Test
    public void changedFilesShouldProduceClassHistories () throws Exception {
        final File left = CompilationUtils.getFile( TEST_DATA + "TestLeft.java" );
        final File right = CompilationUtils.getFile( TEST_DATA + "TestRight.java" );
        distiller.extractClassifiedSourceCodeChanges( left, right );
        ClassHistory classHistory = distiller.getClassHistory();
        assertThat( classHistory.getAttributeHistories().size(), is( 4 ) );
        assertThat( classHistory.getMethodHistories().size(), is( 2 ) );
        assertThat( classHistory.getInnerClassHistories().size(), is( 1 ) );
        classHistory = classHistory.getInnerClassHistories().values().iterator().next();
        assertThat( classHistory.getUniqueName(), is( "test.Test.Bar" ) );
        assertThat( classHistory.getMethodHistories().size(), is( 1 ) );
        final String k = classHistory.getMethodHistories().keySet().iterator().next();
        assertThat( classHistory.getMethodHistories().get( k ).getUniqueName(), is( "test.Test.Bar.newMethod()" ) );
    }

    @Test
    public void annotationsShouldBeDiscovered () throws Exception {

        final File left = CompilationUtils.getFile( TEST_DATA + "Annotations.java-old" );
        final File right = CompilationUtils.getFile( TEST_DATA + "Annotations.java-new" );

        distiller.extractClassifiedSourceCodeChanges( left, right );
        final List<SourceCodeChange> changes = distiller.getSourceCodeChanges();

        assertThat( changes.size(), is( 11 ) );

        /* Make sure an annotation on a method is found */
        assertThat( changes.get( 1 ).toString(), is(
                "Insert: edu.ncsu.csc216.wolf_scheduler.course.CourseTest.testMethodAnnotation().@Test(expected = Exception.class)" ) );

        /* And one on a field */
        assertThat( changes.get( 2 ).toString(), is(
                "Insert: edu.ncsu.csc216.wolf_scheduler.course.CourseTest.hasAnAnnotationNow : Integer.@NotNull" ) );

        /* And one on a class */
        assertThat( changes.get( 5 ).toString(),
                is( "Insert: edu.ncsu.csc216.wolf_scheduler.course.CourseTest.InnerClass.InnerClass.@Entity" ) );

    }

    @Test
    public void contentsOfNewMethodsShouldBeDiscovered () throws Exception {
        final File left = CompilationUtils.getFile( TEST_DATA + "NewMethodExample.java-old" );
        final File right = CompilationUtils.getFile( TEST_DATA + "NewMethodExample.java-new" );

        distiller.extractClassifiedSourceCodeChanges( left, right );
        final List<SourceCodeChange> changes = distiller.getSourceCodeChanges();

        assertThat( changes.size(), is( 9 ) );

        /*
         * Changed return statement should be seen as a change, and not as a
         * delete + recreate
         */
        assertThat( changes.get( 0 ).toString(), is( "Update: return 4;" ) );

        /* Contents of the new method should all be present, in order */
        assertThat( changes.get( 1 ).toString(), is( "Insert: System.out.println(\"\");" ) );
        assertThat( changes.get( 2 ).toString(), is( "Insert: int x = (a + 50);" ) );
        assertThat( changes.get( 3 ).toString(), is( "Insert: return \"\";" ) );

        /*
         * Content in old (deleted) method should be marked for removal as well
         */
        assertThat( changes.get( 5 ).toString(), is( "Delete: Object test = new LinkedList();" ) );
        assertThat( changes.get( 6 ).toString(), is( "Delete: return false;" ) );

        /*
         * Inserted and removed methods should be shown too
         */
        assertThat( changes.get( 7 ).toString(),
                is( "Insert: edu.ncsu.csc216.wolf_scheduler.course.CourseTest.myStringMethod()" ) );
        assertThat( changes.get( 8 ).toString(),
                is( "Delete: edu.ncsu.csc216.wolf_scheduler.course.CourseTest.methodToDelete()" ) );

    }

    @Test
    public void irrelevantMethodsShouldBeSkipped () {
        /*
         * Test to make sure that equals(), hashCode(), get*, and set* are
         * skipped as they are not interesting
         */

        final File left = CompilationUtils.getFile( TEST_DATA + "IrrelevantMethods.java-old" );
        final File right = CompilationUtils.getFile( TEST_DATA + "IrrelevantMethods.java-new" );

        distiller.extractClassifiedSourceCodeChanges( left, right );
        final List<SourceCodeChange> changes = distiller.getSourceCodeChanges();

        assertThat( changes.size(), is( 1 ) );

        assertThat( changes.get( 0 ).toString(),
                is( "Insert: edu.ncsu.csc216.wolf_scheduler.course.CourseTest.aNewString : String" ) );
    }

    @Test
    public void testChangesInInnerClassesShouldBeDiscovered () {

        final File left = CompilationUtils.getFile( TEST_DATA + "InnerClass.java-old" );
        final File right = CompilationUtils.getFile( TEST_DATA + "InnerClass.java-new" );

        distiller.extractClassifiedSourceCodeChanges( left, right );
        final List<SourceCodeChange> changes = distiller.getSourceCodeChanges();

        assertThat( changes.size(), is( 6 ) );

        assertThat( changes.get( 5 ).toString(),
                is( "Insert: edu.ncsu.csc216.wolf_scheduler.course.CourseTest.AnInnerClass" ) );

        assertThat( changes.get( 4 ).toString(), is(
                "Insert: edu.ncsu.csc216.wolf_scheduler.course.CourseTest.AnInnerClass.innerClassField : Object" ) );

        assertThat( changes.get( 2 ).toString(),
                is( "Insert: edu.ncsu.csc216.wolf_scheduler.course.CourseTest.AnInnerClass.AnInnerClass()" ) );

        assertThat( changes.get( 1 ).toString(), is( "Insert: new UnsupportedOperationException();" ) );

        /* Setter method and its contents are skipped */

    }

    @Test
    public void testRecordsShouldBeDiscovered () {
        /*
         * Records are treated basically the same as a class, but with some
         * slight differences in their overall declaration type and constructor
         * type
         */

        final File left = CompilationUtils.getFile( TEST_DATA + "Record.java-old" );
        final File right = CompilationUtils.getFile( TEST_DATA + "Record.java-new" );

        distiller.extractClassifiedSourceCodeChanges( left, right );
        final List<SourceCodeChange> changes = distiller.getSourceCodeChanges();

        assertThat( changes.size(), is( 7 ) );

        /* Methods pieces inside the record */
        assertThat( changes.get( 0 ).toString(), is( "Insert: super();" ) );
        assertThat( changes.get( 2 ).toString(),
                is( "Insert: edu.ncsu.csc216.wolf_scheduler.course.CourseTest.HelloWorld.HelloWorld(String,int)" ) );

        /* Parameters for record */
        assertThat( changes.get( 4 ).toString(),
                is( "Insert: edu.ncsu.csc216.wolf_scheduler.course.CourseTest.HelloWorld.message : String" ) );
        assertThat( changes.get( 5 ).toString(),
                is( "Insert: edu.ncsu.csc216.wolf_scheduler.course.CourseTest.HelloWorld.anotherComponent : int" ) );

        /* Record declaration itself */
        assertThat( changes.get( 6 ).toString(),
                is( "Insert: edu.ncsu.csc216.wolf_scheduler.course.CourseTest.HelloWorld" ) );

    }

    @Test
    public void testDeletedMethodChangesAreDiscovered () {
        /*
         * If a method is deleted, we should see the deleted method & all of the
         * contents of it
         */

        final File left = CompilationUtils.getFile( TEST_DATA + "DeleteMethod.java-old" );
        final File right = CompilationUtils.getFile( TEST_DATA + "DeleteMethod.java-new" );

        distiller.extractClassifiedSourceCodeChanges( left, right );

        final List<SourceCodeChange> changes = distiller.getSourceCodeChanges();

        assertThat( changes.size(), is( 4 ) );

        /* Method contents should be deleted */
        assertThat( changes.get( 0 ).toString(), is( "Delete: new LinkedList();" ) );
        assertThat( changes.get( 1 ).toString(), is( "Delete: Object test = new LinkedList();" ) );
        assertThat( changes.get( 2 ).toString(), is( "Delete: return false;" ) );

        /* Method itself should be deleted */
        assertThat( changes.get( 3 ).toString(),
                is( "Delete: edu.ncsu.csc216.wolf_scheduler.course.CourseTest.methodToDelete()" ) );

    }

    @Test
    public void testDeletedClassChangesAreDiscovered () {
        final File left = CompilationUtils.getFile( TEST_DATA + "DeleteClass.java-old" );
        final File right = CompilationUtils.getFile( TEST_DATA + "DeleteClass.java-new" );

        distiller.extractClassifiedSourceCodeChanges( left, right );

        final List<SourceCodeChange> changes = distiller.getSourceCodeChanges();

        assertThat( changes.size(), is( 10 ) );

        assertThat( changes.get( 0 ).toString(), is( "Delete: super();" ) );
        assertThat( changes.get( 1 ).toString(), is( "Delete: return \"Ayy lmao\";" ) );
        assertThat( changes.get( 2 ).toString(), is( "Delete: new LinkedList();" ) );
        assertThat( changes.get( 3 ).toString(), is( "Delete: Object test = new LinkedList();" ) );
        assertThat( changes.get( 4 ).toString(), is( "Delete: return false;" ) );
        assertThat( changes.get( 5 ).toString(),
                is( "Delete: edu.ncsu.csc216.wolf_scheduler.course.CourseTest.InnerClass.InnerClass()" ) );
        assertThat( changes.get( 6 ).toString(),
                is( "Delete: edu.ncsu.csc216.wolf_scheduler.course.CourseTest.InnerClass.methodInClass()" ) );
        assertThat( changes.get( 7 ).toString(),
                is( "Delete: edu.ncsu.csc216.wolf_scheduler.course.CourseTest.methodToDelete()" ) );
        assertThat( changes.get( 8 ).toString(),
                is( "Delete: edu.ncsu.csc216.wolf_scheduler.course.CourseTest.InnerClass.fieldInClass : String" ) );
        assertThat( changes.get( 9 ).toString(),
                is( "Delete: edu.ncsu.csc216.wolf_scheduler.course.CourseTest.InnerClass" ) );

    }

    @Test
    public void testSealedClassRecognised () {
        final File left = CompilationUtils.getFile( TEST_DATA + "SealedClass.java-old" );
        final File right = CompilationUtils.getFile( TEST_DATA + "SealedClass.java-new" );

        distiller.extractClassifiedSourceCodeChanges( left, right );

        final List<SourceCodeChange> changes = distiller.getSourceCodeChanges();

        assertThat( changes.size(), is( 2 ) );

        assertThat( changes.get( 0 ).toString(),
                is( "Insert: edu.ncsu.csc216.wolf_scheduler.course.CourseTest.myVariableString : String" ) );
        assertThat( changes.get( 1 ).toString(),
                is( "Insert: edu.ncsu.csc216.wolf_scheduler.course.CourseTest.anotherNewVariable : Object" ) );
    }

    @Test
    public void testSealedClassPermitsRecognised () {
        /* Add new fields, also change permits declaration */

        final File left = CompilationUtils.getFile( TEST_DATA + "SealedClass2.java-old" );
        final File right = CompilationUtils.getFile( TEST_DATA + "SealedClass2.java-new" );

        distiller.extractClassifiedSourceCodeChanges( left, right );

        final List<SourceCodeChange> changes = distiller.getSourceCodeChanges();

        assertThat( changes.size(), is( 3 ) );

        assertThat( changes.get( 0 ).toString(), is( "Insert: NotCoolCourseTest" ) );
        assertThat( changes.get( 1 ).toString(),
                is( "Insert: edu.ncsu.csc216.wolf_scheduler.course.CourseTest.myVariableString : String" ) );
        assertThat( changes.get( 2 ).toString(),
                is( "Insert: edu.ncsu.csc216.wolf_scheduler.course.CourseTest.anotherNewVariable : Object" ) );

    }

    @Test
    public void testPatternMatchingRecognised () {
        final File left = CompilationUtils.getFile( TEST_DATA + "PatternMatching.java-old" );
        final File right = CompilationUtils.getFile( TEST_DATA + "PatternMatching.java-new" );

        distiller.extractClassifiedSourceCodeChanges( left, right );

        final List<SourceCodeChange> changes = distiller.getSourceCodeChanges();

        /*
         * The output is actually very difficult to work with here because of
         * the tree structure that results from if(a) ... else if (b) ... else
         * if (c) ... else ... because of the way each subsequent node is seen
         * as a child of the one before it. So we'll just look at the new
         * statement added.
         */

        assertThat( changes.get( 2 ).toString(), is( "Insert: return ((t.A() + t.B()) + t.C());" ) );

    }
}
