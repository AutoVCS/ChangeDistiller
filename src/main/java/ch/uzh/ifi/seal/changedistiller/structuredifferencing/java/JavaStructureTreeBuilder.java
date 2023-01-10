package ch.uzh.ifi.seal.changedistiller.structuredifferencing.java;

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

import org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.AbstractMethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.ConstructorDeclaration;
import org.eclipse.jdt.internal.compiler.ast.FieldDeclaration;
import org.eclipse.jdt.internal.compiler.ast.MarkerAnnotation;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.NormalAnnotation;
import org.eclipse.jdt.internal.compiler.ast.SingleMemberAnnotation;
import org.eclipse.jdt.internal.compiler.ast.SingleNameReference;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.lookup.BlockScope;
import org.eclipse.jdt.internal.compiler.lookup.ClassScope;
import org.eclipse.jdt.internal.compiler.lookup.CompilationUnitScope;
import org.eclipse.jdt.internal.compiler.lookup.MethodScope;

import ch.uzh.ifi.seal.changedistiller.structuredifferencing.java.JavaStructureNode.Type;

/**
 * Creates a tree of {@link JavaStructureNode}s.
 *
 * @author Beat Fluri
 */
public class JavaStructureTreeBuilder extends ASTVisitor {

    private final Stack<JavaStructureNode> fNodeStack;
    private final Stack<char[]>            fQualifiers;

    /**
     * Creates a new Java structure tree builder.
     *
     * @param root
     *            of the structure tree
     */
    public JavaStructureTreeBuilder ( final JavaStructureNode root ) {
        fNodeStack = new Stack<JavaStructureNode>();
        fNodeStack.push( root );
        fQualifiers = new Stack<char[]>();
    }

    @Override
    public boolean visit ( final CompilationUnitDeclaration compilationUnitDeclaration,
            final CompilationUnitScope scope ) {
        if ( compilationUnitDeclaration.currentPackage != null ) {
            for ( final char[] qualifier : compilationUnitDeclaration.currentPackage.tokens ) {
                fQualifiers.push( qualifier );
            }
        }
        return true;
    }

    @Override
    public boolean visit ( final FieldDeclaration fieldDeclaration, final MethodScope scope ) {
        final StringBuffer name = new StringBuffer();
        name.append( fieldDeclaration.name );
        name.append( " : " );
        if ( fieldDeclaration.type == null
                && fNodeStack.peek().getType().compareTo( JavaStructureNode.Type.ENUM ) == 0 ) {
            name.append( fNodeStack.peek().getName() );
        }
        else {
            fieldDeclaration.type.print( 0, name );
        }

        push( Type.FIELD, name.toString(), fieldDeclaration );
        return true;
    }

    @Override
    public boolean visit ( final MarkerAnnotation annotation, final ClassScope scope ) {
        push( Type.ANNOTATION, String.format( "%s.%s", fNodeStack.peek().getName(), annotation.toString() ),
                annotation );
        return true;
    }

    @Override
    public boolean visit ( final MarkerAnnotation annotation, final BlockScope scope ) {
        push( Type.ANNOTATION, String.format( "%s.%s", fNodeStack.peek().getName(), annotation.toString() ),
                annotation );
        return true;
    }

    @Override
    public boolean visit ( final SingleMemberAnnotation annotation, final BlockScope scope ) {
        push( Type.ANNOTATION, String.format( "%s.%s", fNodeStack.peek().getName(), annotation.toString() ),
                annotation );
        return true;
    }

    /**
     * @param annotation
     * @param scope
     */
    @Override
    public boolean visit ( final SingleMemberAnnotation annotation, final ClassScope scope ) {
        push( Type.ANNOTATION, String.format( "%s.%s", fNodeStack.peek().getName(), annotation.toString() ),
                annotation );
        return true;
    }

    @Override
    public boolean visit ( final SingleNameReference annot, final BlockScope scope ) {
        return true;
    }

    @Override
    public boolean visit ( final SingleNameReference singleNameReference, final ClassScope scope ) {
        return true;
    }

    @Override
    public void endVisit ( final SingleMemberAnnotation annotation, final BlockScope scope ) {
        pop();
    }

    /**
     * @param annotation
     * @param scope
     */
    @Override
    public void endVisit ( final SingleMemberAnnotation annotation, final ClassScope scope ) {
        pop();
    }

    @Override
    public void endVisit ( final SingleNameReference singleNameReference, final BlockScope scope ) {
    }

    @Override
    public void endVisit ( final SingleNameReference singleNameReference, final ClassScope scope ) {
        pop();
    }

    @Override
    public void endVisit ( final MarkerAnnotation annotation, final ClassScope scope ) {
        pop();

    }

    @Override
    public void endVisit ( final MarkerAnnotation annotation, final BlockScope scope ) {
        pop();
    }

    @Override
    public void endVisit ( final FieldDeclaration fieldDeclaration, final MethodScope scope ) {
        pop();
    }

    @Override
    public boolean visit ( final ConstructorDeclaration constructorDeclaration, final ClassScope scope ) {
        push( Type.CONSTRUCTOR, getMethodSignature( constructorDeclaration ), constructorDeclaration );
        return false;
    }

    @Override
    public void endVisit ( final ConstructorDeclaration constructorDeclaration, final ClassScope scope ) {
        pop();
    }

    @Override
    public boolean visit ( final MethodDeclaration methodDeclaration, final ClassScope scope ) {
        push( Type.METHOD, getMethodSignature( methodDeclaration ), methodDeclaration );
        return true;
    }

    @Override
    public void endVisit ( final MethodDeclaration methodDeclaration, final ClassScope scope ) {
        pop();
    }

    @Override
    public boolean visit ( final NormalAnnotation annotation, final BlockScope scope ) {

        push( Type.ANNOTATION, String.format( "%s.%s", fNodeStack.peek().getName(), annotation.toString() ),
                annotation );
        return true;
    }

    @Override
    public boolean visit ( final NormalAnnotation annotation, final ClassScope scope ) {
        push( Type.ANNOTATION, String.format( "%s.%s", fNodeStack.peek().getName(), annotation.toString() ),
                annotation );
        return true;
    }

    @Override
    public void endVisit ( final NormalAnnotation annotation, final BlockScope scope ) {
        pop();
    }

    @Override
    public void endVisit ( final NormalAnnotation annotation, final ClassScope scope ) {
        pop();
    }

    @Override
    public boolean visit ( final TypeDeclaration localTypeDeclaration, final BlockScope scope ) {
        return visit( localTypeDeclaration, (CompilationUnitScope) null );
    }

    @Override
    public void endVisit ( final TypeDeclaration localTypeDeclaration, final BlockScope scope ) {
        endVisit( localTypeDeclaration, (CompilationUnitScope) null );
    }

    @Override
    public boolean visit ( final TypeDeclaration memberTypeDeclaration, final ClassScope scope ) {
        return visit( memberTypeDeclaration, (CompilationUnitScope) null );
    }

    @Override
    public void endVisit ( final TypeDeclaration memberTypeDeclaration, final ClassScope scope ) {
        endVisit( memberTypeDeclaration, (CompilationUnitScope) null );
    }

    @Override
    public boolean visit ( final TypeDeclaration typeDeclaration, final CompilationUnitScope scope ) {
        final int kind = TypeDeclaration.kind( typeDeclaration.modifiers );
        Type type = null;
        switch ( kind ) {
            case TypeDeclaration.INTERFACE_DECL:
                type = Type.INTERFACE;
                break;
            case TypeDeclaration.CLASS_DECL:
                type = Type.CLASS;
                break;
            case TypeDeclaration.ANNOTATION_TYPE_DECL:
                type = Type.ANNOTATION;
                break;
            case TypeDeclaration.ENUM_DECL:
                type = Type.ENUM;
                break;
            case TypeDeclaration.RECORD_DECL:
                type = Type.RECORD;
                break;
            default:
                assert ( false );
        }
        push( type, String.valueOf( typeDeclaration.name ), typeDeclaration );
        fQualifiers.push( typeDeclaration.name );
        return true;
    }

    @Override
    public void endVisit ( final TypeDeclaration typeDeclaration, final CompilationUnitScope scope ) {
        pop();
        fQualifiers.pop();
    }

    private String getMethodSignature ( final AbstractMethodDeclaration methodDeclaration ) {
        final StringBuffer signature = new StringBuffer();
        signature.append( methodDeclaration.selector );
        signature.append( '(' );
        if ( methodDeclaration.arguments != null ) {
            for ( int i = 0; i < methodDeclaration.arguments.length; i++ ) {
                if ( i > 0 ) {
                    signature.append( ',' );
                }
                methodDeclaration.arguments[i].type.print( 0, signature );
            }
        }
        signature.append( ')' );
        return signature.toString();
    }

    private void push ( final Type type, final String name, final ASTNode astNode ) {
        final JavaStructureNode node = new JavaStructureNode( type, getQualifier(), name, astNode );
        fNodeStack.peek().addChild( node );
        fNodeStack.push( node );
    }

    private String getQualifier () {
        if ( !fQualifiers.isEmpty() ) {
            final StringBuilder qualifier = new StringBuilder();
            for ( int i = 0; i < fQualifiers.size(); i++ ) {
                qualifier.append( fQualifiers.get( i ) );
                if ( i < fQualifiers.size() - 1 ) {
                    qualifier.append( '.' );
                }
            }
            return qualifier.toString();
        }
        return null;
    }

    private void pop () {
        fNodeStack.pop();
    }

}
