package ch.uzh.ifi.seal.changedistiller.distilling;

import java.util.ArrayList;

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

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.swing.tree.TreeNode;

import ch.uzh.ifi.seal.changedistiller.ast.ASTHelper;
import ch.uzh.ifi.seal.changedistiller.distilling.refactoring.RefactoringCandidate;
import ch.uzh.ifi.seal.changedistiller.distilling.refactoring.RefactoringCandidateContainer;
import ch.uzh.ifi.seal.changedistiller.distilling.refactoring.RefactoringCandidateProcessor;
import ch.uzh.ifi.seal.changedistiller.model.entities.ClassHistory;
import ch.uzh.ifi.seal.changedistiller.model.entities.Delete;
import ch.uzh.ifi.seal.changedistiller.model.entities.Insert;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeChange;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeEntity;
import ch.uzh.ifi.seal.changedistiller.model.entities.StructureEntityVersion;
import ch.uzh.ifi.seal.changedistiller.structuredifferencing.StructureDiffNode;
import ch.uzh.ifi.seal.changedistiller.structuredifferencing.StructureDifferencer.DiffType;
import ch.uzh.ifi.seal.changedistiller.structuredifferencing.StructureNode;
import ch.uzh.ifi.seal.changedistiller.structuredifferencing.java.JavaStructureNode;
import ch.uzh.ifi.seal.changedistiller.structuredifferencing.java.JavaStructureNode.Type;
import ch.uzh.ifi.seal.changedistiller.treedifferencing.Node;

/**
 * Extracts changes from a class {@link StructureDiffNode}.
 *
 * @author Beat Fluri
 * @author Giacomo Ghezzi
 */
public class ClassDistiller {

    private final StructureDiffNode             fClassDiffNode;
    private final ClassHistory                  fClassHistory;
    private final ASTHelper<StructureNode>      fLeftASTHelper;
    private final ASTHelper<StructureNode>      fRightASTHelper;
    private final RefactoringCandidateProcessor fRefactoringProcessor;
    private final DistillerFactory              fDistillerFactory;

    private StructureEntityVersion              fRootEntity;
    private SourceCodeEntity                    fParentEntity;
    private final List<SourceCodeChange>        fChanges;
    private final RefactoringCandidateContainer fRefactoringContainer;
    private String                              fVersion;

    /**
     * Creates a new class distiller.
     *
     * @param classNode
     *            of which the changes should be extracted
     * @param classHistory
     *            to which the changes should be attached
     * @param leftASTHelper
     *            aids getting info from the left AST
     * @param rightASTHelper
     *            aids getting info from the right AST
     * @param refactoringProcessor
     *            to process potential refactorings
     * @param distillerFactory
     *            to create distillers
     */
    public ClassDistiller ( final StructureDiffNode classNode, final ClassHistory classHistory,
            final ASTHelper<StructureNode> leftASTHelper, final ASTHelper<StructureNode> rightASTHelper,
            final RefactoringCandidateProcessor refactoringProcessor, final DistillerFactory distillerFactory ) {
        fClassDiffNode = classNode;
        fClassHistory = classHistory;
        fLeftASTHelper = leftASTHelper;
        fRightASTHelper = rightASTHelper;
        fRefactoringProcessor = refactoringProcessor;
        fDistillerFactory = distillerFactory;
        fChanges = new LinkedList<SourceCodeChange>();
        fRefactoringContainer = new RefactoringCandidateContainer();
    }

    /**
     * Creates a new class distiller.
     *
     * @param classNode
     *            of which the changes should be extracted
     * @param classHistory
     *            to which the changes should be attached
     * @param leftASTHelper
     *            aids getting info from the left AST
     * @param rightASTHelper
     *            aids getting info from the right AST
     * @param refactoringProcessor
     *            to process potential refactorings
     * @param distillerFactory
     *            to create distillers
     * @param version
     *            the number or ID of the version associated to the changes
     *            being distilled
     */
    public ClassDistiller ( final StructureDiffNode classNode, final ClassHistory classHistory,
            final ASTHelper<StructureNode> leftASTHelper, final ASTHelper<StructureNode> rightASTHelper,
            final RefactoringCandidateProcessor refactoringProcessor, final DistillerFactory distillerFactory,
            final String version ) {
        fClassDiffNode = classNode;
        fClassHistory = classHistory;
        fLeftASTHelper = leftASTHelper;
        fRightASTHelper = rightASTHelper;
        fRefactoringProcessor = refactoringProcessor;
        fDistillerFactory = distillerFactory;
        fChanges = new LinkedList<SourceCodeChange>();
        fRefactoringContainer = new RefactoringCandidateContainer();
        fVersion = version;
    }

    /**
     * Extract the {@link SourceCodeChange}s of the {@link StructureDiffNode}
     * with which the class distiller was initialized.
     */
    public void extractChanges () {
        fParentEntity = fLeftASTHelper.createSourceCodeEntity( fClassDiffNode.getLeft() );
        if ( fVersion != null ) {
            fRootEntity = fLeftASTHelper.createStructureEntityVersion( fClassDiffNode.getLeft(), fVersion );
        }
        else {
            fRootEntity = fLeftASTHelper.createStructureEntityVersion( fClassDiffNode.getLeft() );
        }
        processDeclarationChanges( fClassDiffNode, fRootEntity );
        fChanges.addAll( fRootEntity.getSourceCodeChanges() );
        processChildren();

        if ( fVersion != null ) {
            fRefactoringProcessor.processRefactoringCandidates( fClassHistory, fLeftASTHelper, fRightASTHelper,
                    fRefactoringContainer, fVersion );
        }
        else {
            fRefactoringProcessor.processRefactoringCandidates( fClassHistory, fLeftASTHelper, fRightASTHelper,
                    fRefactoringContainer );
        }
        fChanges.addAll( fRefactoringProcessor.getSourceCodeChanges() );
        cleanupInnerClassHistories();
    }

    private void cleanupInnerClassHistories () {
        for ( final Iterator<ClassHistory> it = fClassHistory.getInnerClassHistories().values().iterator(); it
                .hasNext(); ) {
            final ClassHistory ch = it.next();
            if ( !ch.hasChanges() ) {
                it.remove();
            }
        }
    }

    private void processChildren () {
        for ( final StructureDiffNode child : fClassDiffNode.getChildren() ) {
            processChildDiffNode( child );
        }
    }

    private void processChildDiffNode ( final StructureDiffNode diffNode ) {
        if ( diffNode.isClassOrInterfaceDiffNode() ) {
            if ( diffNode.isAddition() || diffNode.isDeletion() ) {
                processChanges( diffNode );
            }
            else {
                processClassDiffNode( diffNode );
            }
        }
        else if ( diffNode.isMethodOrConstructorDiffNode() || diffNode.isFieldDiffNode() ) {
            processChanges( diffNode );

        }
    }

    private void processClassDiffNode ( final StructureDiffNode diffNode ) {
        ClassDistiller classDistiller;
        if ( fVersion != null ) {
            final ClassHistory classHistory = fClassHistory.createInnerClassHistory(
                    fLeftASTHelper.createStructureEntityVersion( diffNode.getLeft(), fVersion ) );
            classDistiller = new ClassDistiller( diffNode, classHistory, fLeftASTHelper, fRightASTHelper,
                    fRefactoringProcessor, fDistillerFactory, fVersion );
        }
        else {
            final ClassHistory classHistory = fClassHistory
                    .createInnerClassHistory( fLeftASTHelper.createStructureEntityVersion( diffNode.getLeft() ) );
            classDistiller = new ClassDistiller( diffNode, classHistory, fLeftASTHelper, fRightASTHelper,
                    fRefactoringProcessor, fDistillerFactory );
        }
        classDistiller.extractChanges();
        fChanges.addAll( classDistiller.getSourceCodeChanges() );
    }

    public List<SourceCodeChange> getSourceCodeChanges () {
        return fChanges;
    }

    private void processChanges ( final StructureDiffNode diffNode ) {
        /**
         * Figure out if this is a change that we want to skip:
         *
         * <pre>
         * -get * -set * -hashCode() - equals()
         * </pre>
         */
        JavaStructureNode change = null;
        Boolean addition = null;

        if ( diffNode.isAddition() ) {
            change = (JavaStructureNode) diffNode.getRight();
            addition = true;
        }
        else if ( diffNode.isDeletion() ) {
            change = (JavaStructureNode) diffNode.getLeft();
            addition = false;
        }

        if ( null != change && change.isMethodOrConstructor() ) {
            final String methodName = change.getName();

            final String trimmedName = methodName.substring( 0, methodName.indexOf( "(" ) );

            /* These are easy -- skip equals/hashcode */
            if ( trimmedName.equals( "equals" ) || trimmedName.equals( "hashCode" ) ) {
                System.out.println( "Found HashCode or Equals -- SKIPPING!" );
                return;
            }

            /* Find getters */
            /* Getter methods match on name, and presence of no parameters */
            /* And a single return statement */
            final Pattern getterRegex = Pattern.compile( "get[a-zA-Z0-9]*\\(\\)" );
            if ( getterRegex.matcher( methodName ).matches() ) {

                final Node newNode = ( null == addition ) ? null
                        : addition ? createNodeAdd( diffNode ) : createNodeRemove( diffNode );
                if ( null != newNode ) {
                    final Iterator<TreeNode> it = newNode.postorderEnumeration().asIterator();

                    final List<ch.uzh.ifi.seal.changedistiller.treedifferencing.Node> nodes = new ArrayList<ch.uzh.ifi.seal.changedistiller.treedifferencing.Node>();
                    it.forEachRemaining( node -> nodes.add( (Node) node ) );

                    /*
                     * 2, because the method declaration is included here too.
                     * Simple getter methods will have just a single field, the
                     * return statement if it has anything else, it's
                     * interesting enough we don't want to skip it
                     */
                    if ( 2 == nodes.size() && nodes.get( 0 ).getEntity().getLabel().equals( "RETURN_STATEMENT" ) ) {
                        System.out.println( "Found getter method -- SKIPPING!" );
                        return;
                    }

                }

            }

            /* Find setters */
            /* Setter methods are easy: name match, one parameter */
            final Pattern setterRegex = Pattern.compile( "set[a-zA-Z0-9]*\\([^,]*\\)" );
            if ( setterRegex.matcher( methodName ).matches() ) {

                final Node newNode = ( null == addition ) ? null
                        : addition ? createNodeAdd( diffNode ) : createNodeRemove( diffNode );
                if ( null != newNode ) {
                    final Iterator<TreeNode> it = newNode.postorderEnumeration().asIterator();

                    final List<ch.uzh.ifi.seal.changedistiller.treedifferencing.Node> nodes = new ArrayList<ch.uzh.ifi.seal.changedistiller.treedifferencing.Node>();
                    it.forEachRemaining( node -> nodes.add( (Node) node ) );

                    /*
                     * 2, because the method declaration is included here too.
                     * Simple setter methods will have just a single field, the
                     * assignment statement if it has anything else, it's
                     * interesting enough we don't want to skip it
                     */
                    if ( 2 == nodes.size() && nodes.get( 0 ).getEntity().getLabel().equals( "ASSIGNMENT" ) ) {
                        System.out.println( "Found setter method -- SKIPPING!" );
                        return;
                    }

                }

                /*
                 * System.out.println( "Found setter method -- SKIPPING!" );
                 * return;
                 */
            }
        }

        // removals
        {
            final Node newNode = createNodeRemove( diffNode );

            if ( null != newNode ) {
                for ( final Iterator<TreeNode> it = newNode.postorderEnumeration().asIterator(); it.hasNext(); ) {
                    final TreeNode node = it.next();

                    final SourceCodeEntity parentOfDeletedLine = fLeftASTHelper
                            .createSourceCodeEntity( diffNode.getLeft() );

                    final ch.uzh.ifi.seal.changedistiller.treedifferencing.Node iNode = (ch.uzh.ifi.seal.changedistiller.treedifferencing.Node) node;

                    final SourceCodeEntity sce = iNode.getEntity();

                    /*
                     * The method deletion is handled already, don't report it
                     * twice
                     */
                    if ( !sce.getLabel().equals( "METHOD" ) ) {
                        final Delete delete = new Delete( fRootEntity, sce, parentOfDeletedLine );
                        fChanges.add( delete );
                    }

                }
            }
        }

        // additions
        {
            final Node newNode = createNodeAdd( diffNode );
            if ( null != newNode ) {
                for ( final Iterator<TreeNode> it = newNode.postorderEnumeration().asIterator(); it.hasNext(); ) {
                    final TreeNode node = it.next();

                    final ch.uzh.ifi.seal.changedistiller.treedifferencing.Node iNode = (ch.uzh.ifi.seal.changedistiller.treedifferencing.Node) node;

                    final SourceCodeEntity sce = iNode.getEntity();

                    /*
                     * Methods in the outer scope are already handled and don't
                     * need to be handled again.
                     */
                    if ( !sce.getLabel().equals( "METHOD" ) ) {

                        final SourceCodeEntity parentOfAddedLine = fRightASTHelper
                                .createSourceCodeEntity( diffNode.getRight() );
                        final Insert insert = new Insert( fRootEntity, sce, parentOfAddedLine );
                        fChanges.add( insert );

                    }

                }

            }
        }

        processAnnotations( diffNode );

        if ( diffNode.isAddition() ) {
            final Insert insert = new Insert( fRootEntity,
                    fRightASTHelper.createSourceCodeEntity( diffNode.getRight() ), fParentEntity );
            fRefactoringContainer.addCandidate( new RefactoringCandidate( insert, diffNode ) );
        }
        else if ( diffNode.isDeletion() ) {
            final Delete delete = new Delete( fRootEntity, fLeftASTHelper.createSourceCodeEntity( diffNode.getLeft() ),
                    fParentEntity );
            fRefactoringContainer.addCandidate( new RefactoringCandidate( delete, diffNode ) );
        }
        else if ( diffNode.isChanged() ) {
            processFineGrainedChanges( diffNode );
        }
    }

    private void processAnnotations ( final StructureDiffNode diffNode ) {

        /* Find all annotations on the left child */
        final List<StructureNode> annotationsOnLeft = null == diffNode.getLeft() ? new ArrayList<>()
                : diffNode.getLeft().getChildren().stream()
                        .filter( e -> ( (JavaStructureNode) e ).getType().equals( Type.ANNOTATION ) )
                        .collect( Collectors.toList() );

        /* Find all annotations on the right child */
        final List<StructureNode> annotationsOnRight = null == diffNode.getRight() ? new ArrayList<StructureNode>()
                : diffNode.getRight().getChildren().stream()
                        .filter( e -> ( (JavaStructureNode) e ).getType().equals( Type.ANNOTATION ) )
                        .collect( Collectors.toList() );

        /*
         * Added annotations are ones present on the right that are not present
         * on the left
         */
        final List<StructureNode> addedAnnotations = annotationsOnRight.stream().filter( e -> {
            final String annotationName = e.toString();

            return !annotationsOnLeft.stream().map( oldAnnotation -> oldAnnotation.toString() )
                    .anyMatch( oldAnnotationName -> oldAnnotationName.equals( annotationName ) );

        } ).collect( Collectors.toList() );

        /*
         * Removed annotations are ones present on the left that are not present
         * on the right
         */
        final List<StructureNode> removedAnnotations = annotationsOnLeft.stream().filter( e -> {
            final String annotationName = e.toString();

            return !annotationsOnRight.stream().map( oldAnnotation -> oldAnnotation.toString() )
                    .anyMatch( oldAnnotationName -> oldAnnotationName.equals( annotationName ) );

        } ).collect( Collectors.toList() );

        /* For each annotation added or removed, add it to our changes list */
        for ( final StructureNode addedAnnotation : addedAnnotations ) {
            final SourceCodeEntity parentOfAddedLine = fRightASTHelper.createSourceCodeEntity( diffNode.getRight() );
            final SourceCodeEntity annotation = fRightASTHelper.createSourceCodeEntity( addedAnnotation );
            final Insert insert = new Insert( fRootEntity, annotation, parentOfAddedLine );
            fChanges.add( insert );
        }

        for ( final StructureNode removedAnnotation : removedAnnotations ) {
            final SourceCodeEntity parentOfAddedLine = fLeftASTHelper.createSourceCodeEntity( diffNode.getLeft() );
            final SourceCodeEntity annotation = fLeftASTHelper.createSourceCodeEntity( removedAnnotation );
            final Delete delete = new Delete( fRootEntity, annotation, parentOfAddedLine );
            fChanges.add( delete );
        }

    }

    private void processFineGrainedChanges ( final StructureDiffNode diffNode ) {
        StructureEntityVersion entity;
        if ( fVersion != null ) {
            entity = fRightASTHelper.createStructureEntityVersion( diffNode.getRight(), fVersion );
        }
        else {
            entity = fRightASTHelper.createStructureEntityVersion( diffNode.getRight() );
        }
        if ( diffNode.isMethodOrConstructorDiffNode() ) {
            entity = createMethodStructureEntity( diffNode );
        }
        else if ( diffNode.isFieldDiffNode() ) {
            entity = createFieldStructureEntity( diffNode );
        }
        else if ( diffNode.isClassOrInterfaceDiffNode() ) {
            entity = createInnerClassStructureEntity( diffNode );
        }
        processBodyChanges( diffNode, entity );
        processDeclarationChanges( diffNode, entity );
        if ( !entity.getSourceCodeChanges().isEmpty() ) {
            fChanges.addAll( entity.getSourceCodeChanges() );
        }
        else {
            if ( diffNode.isMethodOrConstructorDiffNode() ) {
                fClassHistory.deleteMethod( entity );
            }
            else if ( diffNode.isFieldDiffNode() ) {
                fClassHistory.deleteAttribute( entity );
            }
        }
    }

    private StructureEntityVersion createInnerClassStructureEntity ( final StructureDiffNode diffNode ) {
        if ( fVersion != null ) {
            return fRightASTHelper.createInnerClassInClassHistory( fClassHistory, diffNode.getRight(), fVersion );
        }
        else {
            return fRightASTHelper.createInnerClassInClassHistory( fClassHistory, diffNode.getRight() );
        }
    }

    private StructureEntityVersion createFieldStructureEntity ( final StructureDiffNode diffNode ) {
        if ( fVersion != null ) {
            return fRightASTHelper.createFieldInClassHistory( fClassHistory, diffNode.getRight(), fVersion );
        }
        else {
            return fRightASTHelper.createFieldInClassHistory( fClassHistory, diffNode.getRight() );
        }
    }

    private StructureEntityVersion createMethodStructureEntity ( final StructureDiffNode diffNode ) {
        if ( fVersion != null ) {
            return fRightASTHelper.createMethodInClassHistory( fClassHistory, diffNode.getRight(), fVersion );
        }
        else {
            return fRightASTHelper.createMethodInClassHistory( fClassHistory, diffNode.getRight() );
        }
    }

    private void processDeclarationChanges ( final StructureDiffNode diffNode,
            final StructureEntityVersion rootEntity ) {
        extractChanges( fLeftASTHelper.createDeclarationTree( diffNode.getLeft() ),
                fRightASTHelper.createDeclarationTree( diffNode.getRight() ), rootEntity );
    }

    private void processBodyChanges ( final StructureDiffNode diffNode, final StructureEntityVersion rootEntity ) {
        extractChanges( fLeftASTHelper.createMethodBodyTree( diffNode.getLeft() ),
                fRightASTHelper.createMethodBodyTree( diffNode.getRight() ), rootEntity );
    }

    /**
     * Recursively handles changes from adding a new Method or (inner) Class
     * declaration.
     *
     * If the DiffNode represents a new method, a Node representing the method
     * declaration and its contents is returned. If the DiffNode represents a
     * new (inner) Class, then nothing is returned and instead the class is
     * recursively traversed to identify new methods or fields inside of it.
     *
     * @param diffNode
     *            Node containing changes of what has been added (new Method, or
     *            Class)
     * @return Node created from the Method, if the DiffNode represents a new
     *         method.
     */
    private Node createNodeAdd ( final StructureDiffNode diffNode ) {

        if ( null != diffNode.getLeft() && null != diffNode.getRight() ) {
            return null; // not needed
        }

        if ( null != diffNode.getRight() ) {
            final Node method = fRightASTHelper.createMethodBodyTree( diffNode.getRight() );

            if ( null != method ) {
                return method;
            }

            /*
             * If not a new method, then this (should) be a new class. For each
             * child of the class, create a Diff representing adding that
             * element and make a recursive call to analyse it
             */
            for ( final StructureNode child : diffNode.getRight().getChildren() ) {
                final StructureDiffNode childDiff = new StructureDiffNode( null, child );
                childDiff.setDiffType( DiffType.ADDITION );
                processChanges( childDiff );
            }

        }

        return null;
    }

    /**
     * Same as `createNodeAdd`, except this represents things that have been
     * deleted.
     *
     * @param diffNode
     * @return
     */
    private Node createNodeRemove ( final StructureDiffNode diffNode ) {
        if ( null != diffNode.getLeft() && null != diffNode.getRight() ) {
            return null; // not needed
        }

        if ( null != diffNode.getLeft() ) {
            final Node method = fLeftASTHelper.createMethodBodyTree( diffNode.getLeft() );

            if ( null != method ) {
                return method;
            }

            for ( final StructureNode child : diffNode.getLeft().getChildren() ) {
                final StructureDiffNode childDiff = new StructureDiffNode( child, null );
                childDiff.setDiffType( DiffType.DELETION );
                processChanges( childDiff );

            }

        }

        return null;
    }

    private void extractChanges ( final Node left, final Node right, final StructureEntityVersion rootEntity ) {
        final Distiller distiller = fDistillerFactory.create( rootEntity );
        distiller.extractClassifiedSourceCodeChanges( left, right );
    }

}
