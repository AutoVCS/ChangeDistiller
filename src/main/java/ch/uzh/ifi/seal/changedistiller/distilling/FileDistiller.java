package ch.uzh.ifi.seal.changedistiller.distilling;

import java.io.BufferedWriter;

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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;

import com.google.inject.Inject;

import ch.uzh.ifi.seal.changedistiller.ast.ASTHelper;
import ch.uzh.ifi.seal.changedistiller.ast.ASTHelperFactory;
import ch.uzh.ifi.seal.changedistiller.ast.java.JavaASTHelper;
import ch.uzh.ifi.seal.changedistiller.distilling.refactoring.RefactoringCandidateProcessor;
import ch.uzh.ifi.seal.changedistiller.model.classifiers.java.JavaEntityType;
import ch.uzh.ifi.seal.changedistiller.model.entities.ClassHistory;
import ch.uzh.ifi.seal.changedistiller.model.entities.Delete;
import ch.uzh.ifi.seal.changedistiller.model.entities.Insert;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeEntity;
import ch.uzh.ifi.seal.changedistiller.structuredifferencing.StructureDiffNode;
import ch.uzh.ifi.seal.changedistiller.structuredifferencing.StructureDifferencer;
import ch.uzh.ifi.seal.changedistiller.structuredifferencing.StructureNode;

/**
 * Distills {@link SourceCodeChange}s between two {@link File}.
 *
 * @author Beat Fluri
 * @author Giacomo Ghezzi
 */
public class FileDistiller {

    private final DistillerFactory              fDistillerFactory;
    private final ASTHelperFactory              fASTHelperFactory;
    private final RefactoringCandidateProcessor fRefactoringProcessor;

    private List<SourceCodeChange>              fChanges;
    private ASTHelper<StructureNode>            fLeftASTHelper;
    private ASTHelper<StructureNode>            fRightASTHelper;
    private ClassHistory                        fClassHistory;
    private String                              fVersion;

    @Inject
    FileDistiller ( final DistillerFactory distillerFactory, final ASTHelperFactory factory,
            final RefactoringCandidateProcessor refactoringProcessor ) {
        fDistillerFactory = distillerFactory;
        fASTHelperFactory = factory;
        fRefactoringProcessor = refactoringProcessor;
    }

    /**
     * Extracts classified {@link SourceCodeChange}s between two {@link File}s.
     *
     * @param left
     *            file to extract changes
     * @param right
     *            file to extract changes
     */
    public void extractClassifiedSourceCodeChanges ( final File left, final File right ) {
        extractClassifiedSourceCodeChanges( left, "default", right, "default", false );
    }

    /**
     * Extracts classified {@link SourceCodeChange}s between two {@link File}s.
     *
     * @param left
     *            file to extract changes
     * @param leftVersion
     *            version of the language in the left file
     * @param right
     *            file to extract changes
     * @param leftVersion
     *            version of the language in the right file
     */
    @SuppressWarnings ( "unchecked" )
    public void extractClassifiedSourceCodeChanges ( final File left, final String leftVersion, final File right,
            final String rightVersion, final boolean createLeftIfNotExists ) {
        fRightASTHelper = fASTHelperFactory.create( right, rightVersion );

        /*
         * If the old file is empty, or doesn't contain a class(/module/etc) by
         * the same name, there are no differences reported. So, catch an empty
         * file, and create it with a shim of what should be there
         */

        // If we create a new file, delete it when we're done
        boolean created = false;

        // represents the class we're faking
        Insert insert = null;

        try {
            fLeftASTHelper = fASTHelperFactory.create( left, leftVersion );
        }
        catch ( final Exception e ) {

            if ( e.getCause() instanceof FileNotFoundException || e.getCause() instanceof NoSuchFileException ) {
                final CompilationUnitDeclaration cud = ( (JavaASTHelper) (Object) fRightASTHelper ).getCompilation()
                        .getCompilationUnit();

                final StringBuffer out = new StringBuffer();

                final String packageName = null == cud.currentPackage ? "(default package)"
                        : cud.currentPackage.toString();

                final String className = String.valueOf( cud.types[0].name );

                cud.types[0].printHeader( 0, out );

                final String newClassContents = String.format( "%s { }", out.toString() );

                try {
                    left.getParentFile().mkdirs();
                    left.createNewFile();
                    final BufferedWriter output = new BufferedWriter( new FileWriter( left ) );
                    output.write( newClassContents );
                    output.close();
                    created = true;
                    Thread.sleep( 100 );

                }
                catch ( final IOException | InterruptedException e1 ) {
                    e1.printStackTrace();
                    throw new IllegalArgumentException( e1 );
                }

                final SourceCodeEntity sce = new SourceCodeEntity( String.format( "%s.%s", packageName, className ),
                        JavaEntityType.CLASS, null );

                insert = new Insert( null, sce, null );

                fLeftASTHelper = fASTHelperFactory.create( left, leftVersion );
            }
            else {
                throw e;
            }

        }

        // delete new class, if we made one
        if ( created ) {
            left.delete();
        }

        extractDifferences();

        // if we faked a class, add the Insert for it
        if ( null != insert ) {
            fChanges.add( insert );
        }
    }

    private void extractDifferences () {
        final StructureDifferencer structureDifferencer = new StructureDifferencer();
        structureDifferencer.extractDifferences( fLeftASTHelper.createStructureTree(),
                fRightASTHelper.createStructureTree() );
        final StructureDiffNode structureDiff = structureDifferencer.getDifferences();
        if ( structureDiff != null ) {
            fChanges = new LinkedList<SourceCodeChange>();
            // first node is (usually) the compilation unit
            processRootChildren( structureDiff );
        }
        else {
            fChanges = new LinkedList<SourceCodeChange>();
        }
    }

    public void extractClassifiedSourceCodeChanges ( final File left, final File right, final String version ) {
        fVersion = version;
        this.extractClassifiedSourceCodeChanges( left, right );
    }

    private void processRootChildren ( final StructureDiffNode diffNode ) {
        for ( final StructureDiffNode child : diffNode.getChildren() ) {
            if ( child.isClassOrInterfaceDiffNode() && mayHaveChanges( child.getLeft(), child.getRight() ) ) {
                if ( fClassHistory == null ) {
                    if ( fVersion != null ) {
                        fClassHistory = new ClassHistory(
                                fRightASTHelper.createStructureEntityVersion( child.getRight(), fVersion ) );
                    }
                    else {
                        fClassHistory = new ClassHistory(
                                fRightASTHelper.createStructureEntityVersion( child.getRight() ) );
                    }
                }
                processClassDiffNode( child );
            }
        }
    }

    private void processClassDiffNode ( final StructureDiffNode child ) {
        ClassDistiller classDistiller;
        if ( fVersion != null ) {
            classDistiller = new ClassDistiller( child, fClassHistory, fLeftASTHelper, fRightASTHelper,
                    fRefactoringProcessor, fDistillerFactory, fVersion );
        }
        else {
            classDistiller = new ClassDistiller( child, fClassHistory, fLeftASTHelper, fRightASTHelper,
                    fRefactoringProcessor, fDistillerFactory );
        }
        classDistiller.extractChanges();
        fChanges.addAll( classDistiller.getSourceCodeChanges() );
    }

    private boolean mayHaveChanges ( final StructureNode left, final StructureNode right ) {
        return ( left != null ) && ( right != null );
    }

    public List<SourceCodeChange> getSourceCodeChanges () {
        final List<String> addedEntities = new ArrayList<String>();

        final List<String> deletedEntities = new ArrayList<String>();

        for ( final SourceCodeChange change : fChanges ) {
            if ( change instanceof Insert ) {
                addedEntities.add( change.getChangedEntity().getUniqueName() );
            }
            else if ( change instanceof Delete ) {
                deletedEntities.add( change.getChangedEntity().getUniqueName() );
            }
        }

        for ( final Iterator<SourceCodeChange> it = fChanges.iterator(); it.hasNext(); ) {
            final SourceCodeChange change = it.next();

            final SourceCodeEntity changedEntity = change.getChangedEntity();

            if ( addedEntities.contains( changedEntity.getUniqueName() )
                    && deletedEntities.contains( changedEntity.getUniqueName() ) ) {
                it.remove();
            }
        }

        return fChanges;
    }

    public ClassHistory getClassHistory () {
        return fClassHistory;
    }

    public void extractClassifiedSourceCodeChanges ( final File left, final File right,
            final boolean createLeftIfNotExists ) {
        extractClassifiedSourceCodeChanges( left, "default", right, "default", createLeftIfNotExists );

    }

}
