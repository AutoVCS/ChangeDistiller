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

import java.util.Stack;

import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.AbstractVariableDeclaration;
import org.eclipse.jdt.internal.compiler.ast.Argument;
import org.eclipse.jdt.internal.compiler.ast.ArrayTypeReference;
import org.eclipse.jdt.internal.compiler.ast.Block;
import org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration;
import org.eclipse.jdt.internal.compiler.ast.Javadoc;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.ParameterizedQualifiedTypeReference;
import org.eclipse.jdt.internal.compiler.ast.ParameterizedSingleTypeReference;
import org.eclipse.jdt.internal.compiler.ast.QualifiedTypeReference;
import org.eclipse.jdt.internal.compiler.ast.SingleTypeReference;
import org.eclipse.jdt.internal.compiler.ast.Statement;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.ast.TypeParameter;
import org.eclipse.jdt.internal.compiler.ast.TypeReference;
import org.eclipse.jdt.internal.compiler.ast.Wildcard;
import org.eclipse.jdt.internal.compiler.lookup.BlockScope;
import org.eclipse.jdt.internal.compiler.lookup.ClassScope;
import org.eclipse.jdt.internal.compiler.lookup.CompilationUnitScope;
import org.eclipse.jdt.internal.compiler.lookup.MethodScope;
import org.eclipse.jdt.internal.compiler.parser.Scanner;
import org.eclipse.jdt.internal.compiler.parser.TerminalTokens;

import com.google.inject.Inject;

import ch.uzh.ifi.seal.changedistiller.ast.ASTNodeTypeConverter;
import ch.uzh.ifi.seal.changedistiller.model.classifiers.EntityType;
import ch.uzh.ifi.seal.changedistiller.model.classifiers.SourceRange;
import ch.uzh.ifi.seal.changedistiller.model.classifiers.java.JavaEntityType;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeEntity;
import ch.uzh.ifi.seal.changedistiller.treedifferencing.Node;

/**
 * Visitor to generate an intermediate tree (general, rooted, labeled, valued
 * tree) out of a field, class, or method declaration.
 *
 * @author Beat Fluri
 *
 */
public class JavaDeclarationConverter extends ASTVisitor {

    private static final String        COLON_SPACE = ": ";
    private boolean                    fEmptyJavaDoc;
    private final Stack<Node>          fNodeStack;
    private boolean                    fInMethodDeclaration;
    private String                     fSource;
    private final ASTNodeTypeConverter fASTHelper;
    private Scanner                    fScanner;

    @Inject
    JavaDeclarationConverter ( final ASTNodeTypeConverter astHelper ) {
        fASTHelper = astHelper;
        fNodeStack = new Stack<Node>();
    }

    /**
     * Initializes the declaration converter.
     *
     * @param root
     *            of the resulting declaration tree
     * @param scanner
     *            of the source file that is traversed
     */
    public void initialize ( final Node root, final Scanner scanner ) {
        fScanner = scanner;
        fSource = String.valueOf( scanner.source );
        fNodeStack.clear();
        fNodeStack.push( root );
    }

    @Override
    public boolean visit ( final Argument argument, final ClassScope scope ) {
        return visit( argument, (BlockScope) null );
    }

    @Override
    public void endVisit ( final Argument argument, final ClassScope scope ) {
        endVisit( argument, (BlockScope) null );
    }

    @Override
    public boolean visit ( final Argument node, final BlockScope scope ) {
        final boolean isNotParam = getCurrentParent().getLabel() != JavaEntityType.PARAMETERS;
        pushValuedNode( node, String.valueOf( node.name ) );
        if ( isNotParam ) {
            visitModifiers( node.modifiers );
        }
        node.type.traverse( this, scope );
        return false;
    }

    @Override
    public void endVisit ( final Argument node, final BlockScope scope ) {
        pop();
    }

    @Override
    public boolean visit ( final Block block, final BlockScope scope ) {
        // skip block as it is not interesting
        return true;
    }

    @Override
    public void endVisit ( final Block block, final BlockScope scope ) {
        // do nothing pop is not needed (see visit(Block, BlockScope))
    }

    @Override
    public boolean visit ( final FieldDeclaration fieldDeclaration, final MethodScope scope ) {
        if ( fieldDeclaration.javadoc != null ) {
            fieldDeclaration.javadoc.traverse( this, scope );
        }
        visitFieldDeclarationModifiers( fieldDeclaration );
        fieldDeclaration.type.traverse( this, scope );
        visitExpression( fieldDeclaration.initialization );

        // if ( fieldDeclaration.annotations != null ) {
        // for ( final Annotation annotation : fieldDeclaration.annotations ) {
        // annotation.traverse( this, scope );
        // }
        // }
        return true;
    }

    @Override
    public void endVisit ( final FieldDeclaration fieldDeclaration, final MethodScope scope ) {
        pop();
    }

    private void visitExpression ( final Expression expression ) {
        if ( expression != null ) {
            push( fASTHelper.convertNode( expression ), expression.toString(), expression.sourceStart(),
                    expression.sourceEnd() );
            pop();
        }
    }

    private void visitFieldDeclarationModifiers ( final FieldDeclaration fieldDeclaration ) {
        fScanner.resetTo( fieldDeclaration.declarationSourceStart, fieldDeclaration.sourceStart() );
        visitModifiers( fieldDeclaration.modifiers );
    }

    private void visitMethodDeclarationModifiers ( final AbstractMethodDeclaration methodDeclaration ) {
        fScanner.resetTo( methodDeclaration.declarationSourceStart, methodDeclaration.sourceStart() );
        visitModifiers( methodDeclaration.modifiers );
    }

    private void visitTypeDeclarationModifiers ( final TypeDeclaration typeDeclaration ) {
        fScanner.resetTo( typeDeclaration.declarationSourceStart, typeDeclaration.sourceStart() );
        visitModifiers( typeDeclaration.modifiers );
    }

    // logic partly taken from org.eclipse.jdt.core.dom.ASTConverter
    private void visitModifiers ( final int modifierMask ) {
        push( JavaEntityType.MODIFIERS, "", -1, -1 );
        if ( modifierMask != 0 ) {
            final Node modifiers = fNodeStack.peek();
            fScanner.tokenizeWhiteSpace = false;
            try {
                int token;
                while ( ( token = fScanner.getNextToken() ) != TerminalTokens.TokenNameEOF ) {
                    switch ( token ) {
                        case TerminalTokens.TokenNameabstract:
                        case TerminalTokens.TokenNamepublic:
                        case TerminalTokens.TokenNameprotected:
                        case TerminalTokens.TokenNameprivate:
                        case TerminalTokens.TokenNamefinal:
                        case TerminalTokens.TokenNamestatic:
                        case TerminalTokens.TokenNamevolatile:
                        case TerminalTokens.TokenNamestrictfp:
                        case TerminalTokens.TokenNamenative:
                        case TerminalTokens.TokenNamesynchronized:
                        case TerminalTokens.TokenNametransient:
                            push( JavaEntityType.MODIFIER, fScanner.getCurrentTokenString(),
                                    fScanner.getCurrentTokenStartPosition(), fScanner.getCurrentTokenEndPosition() );
                            pop();
                            break;
                        default:
                            break;
                    }
                }
                // CHECKSTYLE:OFF
            }
            catch ( final InvalidInputException e ) {
                // CHECKSTYLE:ON
                // ignore
            }
            setSourceRange( modifiers );
        }
        pop();
    }

    private void setSourceRange ( final Node modifiers ) {
        final SourceCodeEntity firstModifier = ( (Node) modifiers.getFirstLeaf() ).getEntity();
        final SourceCodeEntity lastModifier = ( (Node) modifiers.getLastLeaf() ).getEntity();
        modifiers.getEntity().setStartPosition( firstModifier.getStartPosition() );
        modifiers.getEntity().setEndPosition( lastModifier.getEndPosition() );
    }

    @Override
    public boolean visit ( final Javadoc javadoc, final ClassScope scope ) {
        return visit( javadoc, (BlockScope) null );
    }

    @Override
    public void endVisit ( final Javadoc javadoc, final ClassScope scope ) {
        endVisit( javadoc, (BlockScope) null );
    }

    @Override
    public boolean visit ( final Javadoc javadoc, final BlockScope scope ) {
        String string = null;
        string = getSource( javadoc );
        if ( isJavadocEmpty( string ) ) {
            fEmptyJavaDoc = true;
        }
        else {
            pushValuedNode( javadoc, string );
        }
        return false;
    }

    @Override
    public void endVisit ( final Javadoc javadoc, final BlockScope scope ) {
        if ( !fEmptyJavaDoc ) {
            pop();
        }
        fEmptyJavaDoc = false;
    }

    private boolean isJavadocEmpty ( final String doc ) {
        final String[] splittedDoc = doc.split( "/\\*+\\s*" );
        final StringBuilder tmp = new StringBuilder();
        for ( final String s : splittedDoc ) {
            tmp.append( s );
        }

        String result = tmp.toString();

        try {
            result = result.split( "\\s*\\*/" )[0];
        }
        catch ( final ArrayIndexOutOfBoundsException e ) {
            result = result.replace( '/', ' ' );
        }

        result = result.replace( '*', ' ' ).trim();

        return result.equals( "" );
    }

    @Override
    public boolean visit ( final MethodDeclaration methodDeclaration, final ClassScope scope ) {
        visitAbstractMethodDeclaration( methodDeclaration, scope );
        // ignore body, since only declaration is interesting
        return false;
    }

    /**
     * Visits an expression.
     *
     * @param expression
     *            to visit
     * @param scope
     *            in which the expression resides
     * @return <code>true</code> if the children of the expression should be
     *         visited, <code>false</code> otherwise.
     */
    public boolean visitExpression ( final Statement expression, final ClassScope scope ) {
        // all expression processed in this method are statements
        // - use printStatement to get the ';' at the end of the expression
        // - extend the length of the statement by 1 to add ';'
        push( fASTHelper.convertNode( expression ), expression.toString() + ';', expression.sourceStart(),
                expression.sourceEnd() + 1 );
        return false;
    }

    @Override
    public void endVisit ( final MethodDeclaration methodDeclaration, final ClassScope scope ) {
        pop();
    }

    @Override
    public boolean visit ( final ConstructorDeclaration constructorDeclaration, final ClassScope scope ) {
        visitAbstractMethodDeclaration( constructorDeclaration, scope );
        // ignore body, since only declaration is interesting
        return false;
    }

    @Override
    public void endVisit ( final ConstructorDeclaration constructorDeclaration, final ClassScope scope ) {
        pop();
    }

    private void visitAbstractMethodDeclaration ( final AbstractMethodDeclaration methodDeclaration,
            final ClassScope scope ) {
        if ( methodDeclaration.javadoc != null ) {
            methodDeclaration.javadoc.traverse( this, scope );
        }
        fInMethodDeclaration = true;
        visitMethodDeclarationModifiers( methodDeclaration );
        visitReturnType( methodDeclaration, scope );
        visitAbstractVariableDeclarations( JavaEntityType.TYPE_PARAMETERS, methodDeclaration.typeParameters() );
        visitAbstractVariableDeclarations( JavaEntityType.PARAMETERS, methodDeclaration.arguments );
        fInMethodDeclaration = false;
        visitList( JavaEntityType.THROW, methodDeclaration.thrownExceptions );
    }

    private void visitReturnType ( final AbstractMethodDeclaration abstractMethodDeclaration, final ClassScope scope ) {
        if ( abstractMethodDeclaration instanceof MethodDeclaration ) {
            final MethodDeclaration methodDeclaration = (MethodDeclaration) abstractMethodDeclaration;
            if ( methodDeclaration.returnType != null ) {
                methodDeclaration.returnType.traverse( this, scope );
            }
        }
    }

    @Override
    public boolean visit ( final ParameterizedSingleTypeReference parameterizedSingleTypeReference,
            final ClassScope scope ) {
        return visit( parameterizedSingleTypeReference, (BlockScope) null );
    }

    @Override
    public void endVisit ( final ParameterizedSingleTypeReference type, final ClassScope scope ) {
        endVisit( type, (BlockScope) null );
    }

    @Override
    public boolean visit ( final ParameterizedSingleTypeReference type, final BlockScope scope ) {
        final int start = type.sourceStart();
        final int end = findSourceEndTypeReference( type, type.typeArguments );
        pushValuedNode( type, prefixWithNameOfParrentIfInMethodDeclaration() + getSource( start, end ) );
        fNodeStack.peek().getEntity().setEndPosition( end );
        return false;
    }

    private String getSource ( final ASTNode node ) {
        return getSource( node.sourceStart(), node.sourceEnd() );
    }

    private String getSource ( final int start, final int end ) {
        return fSource.substring( start, end + 1 );
    }

    private String prefixWithNameOfParrentIfInMethodDeclaration () {
        return fInMethodDeclaration ? getCurrentParent().getValue() + COLON_SPACE : "";
    }

    @Override
    public void endVisit ( final ParameterizedSingleTypeReference type, final BlockScope scope ) {
        pop();
    }

    @Override
    public boolean visit ( final ParameterizedQualifiedTypeReference type, final ClassScope scope ) {
        return visit( type, (BlockScope) null );
    }

    @Override
    public void endVisit ( final ParameterizedQualifiedTypeReference type, final ClassScope scope ) {
        endVisit( type, (BlockScope) null );
    }

    @Override
    public boolean visit ( final ParameterizedQualifiedTypeReference type, final BlockScope scope ) {
        pushValuedNode( type, getSource( type ) );
        adjustEndPositionOfParameterizedType( type );
        return false;
    }

    private void adjustEndPositionOfParameterizedType ( final ParameterizedQualifiedTypeReference type ) {
        if ( hasTypeParameter( type ) ) {
            visitList( JavaEntityType.TYPE_PARAMETERS, type.typeArguments[type.typeArguments.length - 1] );
            fNodeStack.peek().getEntity()
                    .setEndPosition( getLastChildOfCurrentNode().getEntity().getEndPosition() + 1 );
        }
    }

    private boolean hasTypeParameter ( final ParameterizedQualifiedTypeReference type ) {
        return type.typeArguments[type.typeArguments.length - 1] != null;
    }

    @Override
    public void endVisit ( final ParameterizedQualifiedTypeReference type, final BlockScope scope ) {
        pop();
    }

    @Override
    public boolean visit ( final QualifiedTypeReference type, final ClassScope scope ) {
        return visit( type, (BlockScope) null );
    }

    @Override
    public void endVisit ( final QualifiedTypeReference type, final ClassScope scope ) {
        endVisit( type, (BlockScope) null );
    }

    @Override
    public boolean visit ( final QualifiedTypeReference type, final BlockScope scope ) {
        pushValuedNode( type, prefixWithNameOfParrentIfInMethodDeclaration() + type.toString() );
        return false;
    }

    @Override
    public void endVisit ( final QualifiedTypeReference type, final BlockScope scope ) {
        pop();
    }

    @Override
    public boolean visit ( final SingleTypeReference type, final ClassScope scope ) {
        return visit( type, (BlockScope) null );
    }

    @Override
    public void endVisit ( final SingleTypeReference type, final ClassScope scope ) {
        endVisit( type, (BlockScope) null );
    }

    @Override
    public boolean visit ( final ArrayTypeReference arrayType, final ClassScope scope ) {
        return visit( arrayType, (BlockScope) null );
    }

    @Override
    public void endVisit ( final ArrayTypeReference arrayType, final ClassScope scope ) {
        endVisit( arrayType, (BlockScope) null );
    }

    @Override
    public boolean visit ( final SingleTypeReference type, final BlockScope scope ) {
        pushValuedNode( type, prefixWithNameOfParrentIfInMethodDeclaration() + String.valueOf( type.token ) );
        return false;
    }

    @Override
    public void endVisit ( final SingleTypeReference type, final BlockScope scope ) {
        pop();
    }

    @Override
    public boolean visit ( final ArrayTypeReference arrayType, final BlockScope scope ) {
        pushValuedNode( arrayType, prefixWithNameOfParrentIfInMethodDeclaration() + String.valueOf( arrayType.token ) );
        return false;
    }

    @Override
    public void endVisit ( final ArrayTypeReference arrayType, final BlockScope scope ) {
        pop();
    }

    @Override
    public boolean visit ( final TypeDeclaration typeDeclaration, final ClassScope scope ) {
        return visit( typeDeclaration, (BlockScope) null );
    }

    @Override
    public void endVisit ( final TypeDeclaration typeDeclaration, final ClassScope scope ) {
        endVisit( typeDeclaration, (BlockScope) null );
    }

    @Override
    public boolean visit ( final TypeDeclaration typeDeclaration, final CompilationUnitScope scope ) {
        return visit( typeDeclaration, (BlockScope) null );
    }

    @Override
    public void endVisit ( final TypeDeclaration typeDeclaration, final CompilationUnitScope scope ) {
        endVisit( typeDeclaration, (BlockScope) null );
    }

    @Override
    public boolean visit ( final TypeDeclaration typeDeclaration, final BlockScope scope ) {
        if ( typeDeclaration.javadoc != null ) {
            typeDeclaration.javadoc.traverse( this, scope );
        }
        visitTypeDeclarationModifiers( typeDeclaration );
        visitAbstractVariableDeclarations( JavaEntityType.TYPE_PARAMETERS, typeDeclaration.typeParameters );
        if ( typeDeclaration.superclass != null ) {
            typeDeclaration.superclass.traverse( this, scope );
        }

        if ( typeDeclaration.permittedTypes != null ) {
            for ( final TypeReference reference : typeDeclaration.permittedTypes ) {
                reference.traverse( this, scope );
            }
        }

        visitList( JavaEntityType.SUPER_INTERFACE_TYPES, typeDeclaration.superInterfaces );
        return false;
    }

    @Override
    public void endVisit ( final TypeDeclaration typeDeclaration, final BlockScope scope ) {
        pop();
    }

    @Override
    public boolean visit ( final TypeParameter typeParameter, final ClassScope scope ) {
        return visit( typeParameter, (BlockScope) null );
    }

    @Override
    public void endVisit ( final TypeParameter typeParameter, final ClassScope scope ) {
        endVisit( typeParameter, (BlockScope) null );
    }

    @Override
    public boolean visit ( final TypeParameter typeParameter, final BlockScope scope ) {
        push( fASTHelper.convertNode( typeParameter ),
                getSource( typeParameter.sourceStart(), typeParameter.declarationSourceEnd ),
                typeParameter.sourceStart(), typeParameter.declarationSourceEnd );
        return false;
    }

    @Override
    public void endVisit ( final TypeParameter typeParameter, final BlockScope scope ) {
        pop();
    }

    @Override
    public boolean visit ( final Wildcard type, final ClassScope scope ) {
        return visit( type, (BlockScope) null );
    }

    @Override
    public void endVisit ( final Wildcard type, final ClassScope scope ) {
        endVisit( type, (BlockScope) null );
    }

    @Override
    public boolean visit ( final Wildcard type, final BlockScope scope ) {
        String bound = "";
        switch ( type.kind ) {
            case Wildcard.EXTENDS:
                bound = "extends";
                break;
            case Wildcard.SUPER:
                bound = "super";
                break;
            default:
        }
        pushValuedNode( type, bound );
        return true;
    }

    @Override
    public void endVisit ( final Wildcard type, final BlockScope scope ) {
        pop();
    }

    private void visitList ( final ASTNode[] list ) {
        for ( final ASTNode node : list ) {
            node.traverse( this, null );
        }
    }

    private void visitAbstractVariableDeclarations ( final JavaEntityType parentLabel,
            final AbstractVariableDeclaration[] declarations ) {
        int start = -1;
        int end = -1;
        push( parentLabel, "", start, end );
        if ( isNotEmpty( declarations ) ) {
            start = declarations[0].declarationSourceStart;
            end = declarations[declarations.length - 1].declarationSourceEnd;
            visitList( declarations );
        }
        adjustSourceRangeOfCurrentNode( start, end );
        pop();
    }

    private void adjustSourceRangeOfCurrentNode ( final int start, final int end ) {
        fNodeStack.peek().getEntity().setStartPosition( start );
        fNodeStack.peek().getEntity().setEndPosition( end );
    }

    private void visitList ( final JavaEntityType parentLabel, final ASTNode[] nodes ) {
        int start = -1;
        int end = -1;
        push( parentLabel, "", start, end );
        if ( isNotEmpty( nodes ) ) {
            start = nodes[0].sourceStart();
            visitList( nodes );
            end = getLastChildOfCurrentNode().getEntity().getEndPosition();
        }
        adjustSourceRangeOfCurrentNode( start, end );
        pop();
    }

    private boolean isNotEmpty ( final ASTNode[] nodes ) {
        return ( nodes != null ) && ( nodes.length > 0 );
    }

    // recursive method that finds the end position of a type reference with
    // type parameters, e.g.,
    // Foo<T>.List<Bar<T>>
    private int findSourceEndTypeReference ( final TypeReference type, final TypeReference[] typeParameters ) {
        int end = type.sourceEnd();
        if ( isNotEmpty( typeParameters ) ) {
            final TypeReference lastNode = typeParameters[typeParameters.length - 1];
            if ( lastNode instanceof ParameterizedQualifiedTypeReference ) {
                final TypeReference[][] typeArguments = ( (ParameterizedQualifiedTypeReference) lastNode ).typeArguments;
                end = findSourceEndTypeReference( lastNode, typeArguments[typeArguments.length - 1] );
            }
            else if ( lastNode instanceof ParameterizedSingleTypeReference ) {
                final TypeReference[] typeArguments = ( (ParameterizedSingleTypeReference) lastNode ).typeArguments;
                end = findSourceEndTypeReference( lastNode, typeArguments );
            }
            else {
                end = typeParameters[typeParameters.length - 1].sourceEnd();
            }
            if ( end == -1 ) {
                end = lastNode.sourceEnd();
            }
            end++; // increment end position to the the last '>'
        }
        return end;
    }

    private Node getLastChildOfCurrentNode () {
        return (Node) fNodeStack.peek().getLastChild();
    }

    private void pushValuedNode ( final ASTNode node, final String value ) {
        push( fASTHelper.convertNode( node ), value, node.sourceStart(), node.sourceEnd() );
    }

    private void push ( final EntityType label, final String value, final int start, final int end ) {
        final Node n = new Node( label, value.trim() );
        n.setEntity( new SourceCodeEntity( value.trim(), label, new SourceRange( start, end ) ) );
        getCurrentParent().add( n );
        fNodeStack.push( n );
    }

    private void pop () {
        fNodeStack.pop();
    }

    private Node getCurrentParent () {
        return fNodeStack.peek();
    }

}
