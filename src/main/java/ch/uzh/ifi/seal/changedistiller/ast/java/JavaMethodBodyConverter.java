package ch.uzh.ifi.seal.changedistiller.ast.java;

import java.util.Arrays;

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

import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Stack;

import org.eclipse.jdt.core.compiler.InvalidInputException;
import org.eclipse.jdt.internal.compiler.ASTVisitor;
import org.eclipse.jdt.internal.compiler.ast.ASTNode;
import org.eclipse.jdt.internal.compiler.ast.AllocationExpression;
import org.eclipse.jdt.internal.compiler.ast.AssertStatement;
import org.eclipse.jdt.internal.compiler.ast.Assignment;
import org.eclipse.jdt.internal.compiler.ast.Block;
import org.eclipse.jdt.internal.compiler.ast.BreakStatement;
import org.eclipse.jdt.internal.compiler.ast.CaseStatement;
import org.eclipse.jdt.internal.compiler.ast.CompilationUnitDeclaration;
import org.eclipse.jdt.internal.compiler.ast.CompoundAssignment;
import org.eclipse.jdt.internal.compiler.ast.ContinueStatement;
import org.eclipse.jdt.internal.compiler.ast.DoStatement;
import org.eclipse.jdt.internal.compiler.ast.EmptyStatement;
import org.eclipse.jdt.internal.compiler.ast.ExplicitConstructorCall;
import org.eclipse.jdt.internal.compiler.ast.Expression;
import org.eclipse.jdt.internal.compiler.ast.ForStatement;
import org.eclipse.jdt.internal.compiler.ast.ForeachStatement;
import org.eclipse.jdt.internal.compiler.ast.IfStatement;
import org.eclipse.jdt.internal.compiler.ast.LabeledStatement;
import org.eclipse.jdt.internal.compiler.ast.LambdaExpression;
import org.eclipse.jdt.internal.compiler.ast.LocalDeclaration;
import org.eclipse.jdt.internal.compiler.ast.MessageSend;
import org.eclipse.jdt.internal.compiler.ast.MethodDeclaration;
import org.eclipse.jdt.internal.compiler.ast.PostfixExpression;
import org.eclipse.jdt.internal.compiler.ast.PrefixExpression;
import org.eclipse.jdt.internal.compiler.ast.QualifiedAllocationExpression;
import org.eclipse.jdt.internal.compiler.ast.ReturnStatement;
import org.eclipse.jdt.internal.compiler.ast.Statement;
import org.eclipse.jdt.internal.compiler.ast.SwitchStatement;
import org.eclipse.jdt.internal.compiler.ast.SynchronizedStatement;
import org.eclipse.jdt.internal.compiler.ast.ThrowStatement;
import org.eclipse.jdt.internal.compiler.ast.TryStatement;
import org.eclipse.jdt.internal.compiler.ast.TypeDeclaration;
import org.eclipse.jdt.internal.compiler.ast.WhileStatement;
import org.eclipse.jdt.internal.compiler.ast.YieldStatement;
import org.eclipse.jdt.internal.compiler.lookup.BlockScope;
import org.eclipse.jdt.internal.compiler.parser.Scanner;
import org.eclipse.jdt.internal.compiler.parser.TerminalTokens;

import com.google.inject.Inject;

import ch.uzh.ifi.seal.changedistiller.ast.ASTNodeTypeConverter;
import ch.uzh.ifi.seal.changedistiller.ast.java.Comment.CommentType;
import ch.uzh.ifi.seal.changedistiller.model.classifiers.EntityType;
import ch.uzh.ifi.seal.changedistiller.model.classifiers.SourceRange;
import ch.uzh.ifi.seal.changedistiller.model.classifiers.java.JavaEntityType;
import ch.uzh.ifi.seal.changedistiller.model.entities.SourceCodeEntity;
import ch.uzh.ifi.seal.changedistiller.treedifferencing.Node;

/**
 * Visitor to generate an intermediate tree (general, rooted, labeled, valued
 * tree) out of a method body.
 *
 * @author Beat Fluri, Giacomo Ghezzi
 *
 */
public class JavaMethodBodyConverter extends ASTVisitor {

    private static final String        COLON = ":";
    private List<Comment>              fComments;
    private final Stack<Node>          fNodeStack;
    private String                     fSource;
    private Scanner                    fScanner;

    private ASTNode                    fLastVisitedNode;
    private Node                       fLastAddedNode;

    private final Stack<ASTNode[]>     fLastAssociationCandidate;
    private final Stack<Node[]>        fLastCommentNodeTuples;
    private final ASTNodeTypeConverter fASTHelper;

    @Inject
    JavaMethodBodyConverter ( final ASTNodeTypeConverter astHelper ) {
        fNodeStack = new Stack<Node>();
        fLastAssociationCandidate = new Stack<ASTNode[]>();
        fLastCommentNodeTuples = new Stack<Node[]>();
        fASTHelper = astHelper;
    }

    /**
     * Initializes the method body converter.
     *
     * @param root
     *            the root node of the tree to generate
     * @param methodRoot
     *            the method AST root node, necessary for comment attachment
     * @param comments
     *            to associate
     * @param scanner
     *            the scanner with which the AST was created
     */
    public void initialize ( final Node root, final ASTNode methodRoot, final List<Comment> comments,
            final Scanner scanner ) {
        fNodeStack.clear();
        fLastAssociationCandidate.clear();
        fLastCommentNodeTuples.clear();
        fLastVisitedNode = methodRoot;
        fLastAddedNode = root;
        fNodeStack.push( root );
        fComments = comments;
        fScanner = scanner;
        fSource = String.valueOf( scanner.getSource() );
    }

    /**
     * Prepares node for comment association.
     *
     * @param node
     *            the node to prepare for comment association
     */
    public void preVisit ( final ASTNode node ) {
        if ( !hasComments() || isUnusableNode( node ) ) {
            return;
        }
        int i = 0;
        while ( i < fComments.size() ) {
            final Comment comment = fComments.get( i );
            if ( previousNodeExistsAndIsNotTheFirstNode() && isCommentBetweenCurrentNodeAndLastNode( comment, node ) ) {
                final ASTNode[] candidate = new ASTNode[] { fLastVisitedNode, comment, node };
                fLastAssociationCandidate.push( candidate );
                final Node[] nodeTuple = new Node[2];
                nodeTuple[0] = fLastAddedNode; // preceeding node
                insertCommentIntoTree( comment );
                nodeTuple[1] = fLastAddedNode; // comment
                fLastCommentNodeTuples.push( nodeTuple );
                fComments.remove( i-- );
            }
            i++;
        }
    }

    private void insertCommentIntoTree ( final Comment comment ) {
        EntityType label = JavaEntityType.LINE_COMMENT;
        if ( comment.getType() == CommentType.BLOCK_COMMENT ) {
            label = JavaEntityType.BLOCK_COMMENT;
        }
        push( label, getSource( comment.sourceStart(), comment.sourceEnd() - 1 ), comment.sourceStart(),
                comment.sourceEnd() );
        pop( comment );
    }

    private boolean previousNodeExistsAndIsNotTheFirstNode () {
        return ( fLastVisitedNode != null ) && ( fLastVisitedNode.sourceStart() > 0 );
    }

    private boolean isCommentBetweenCurrentNodeAndLastNode ( final Comment comment, final ASTNode currentNode ) {
        return ( fLastVisitedNode.sourceStart() < comment.sourceStart() )
                && ( comment.sourceStart() < currentNode.sourceStart() );
    }

    private boolean hasComments () {
        return ( fComments != null ) && !fComments.isEmpty();
    }

    /**
     * Associates a comment to code with the candidate triple {preceedingNode,
     * comment, succeedingNode}
     *
     * @param node
     *            succeeding node of the triple
     */
    public void postVisit ( final ASTNode node ) {
        if ( isUnusableNode( node ) ) {
            return;
        }
        if ( !fLastAssociationCandidate.isEmpty() && ( node == fLastAssociationCandidate.peek()[2] ) ) {
            final ASTNode preceedingNode = fLastAssociationCandidate.peek()[0];
            final ASTNode commentNode = fLastAssociationCandidate.peek()[1];
            final ASTNode succeedingNode = fLastAssociationCandidate.peek()[2];

            if ( ( preceedingNode != null ) && ( succeedingNode != null ) ) {
                final String preceedingNodeString = getASTString( preceedingNode );
                final String succeedingNodeString = getASTString( succeedingNode );
                final String commentNodeString = getCommentString( commentNode );

                int rateForPreceeding = proximityRating( preceedingNode, commentNode );
                int rateForSucceeding = proximityRating( commentNode, succeedingNode );
                if ( rateForPreceeding == rateForSucceeding ) {
                    rateForPreceeding += wordMatching( preceedingNodeString, commentNodeString );
                    rateForSucceeding += wordMatching( succeedingNodeString, commentNodeString );
                }
                if ( rateForPreceeding == rateForSucceeding ) {
                    rateForSucceeding++;
                }

                final Node[] nodeTuple = fLastCommentNodeTuples.peek();
                if ( rateForPreceeding > rateForSucceeding ) {
                    nodeTuple[1].addAssociatedNode( nodeTuple[0] );
                    nodeTuple[0].addAssociatedNode( nodeTuple[1] );
                }
                else {
                    nodeTuple[1].addAssociatedNode( fLastAddedNode );
                    fLastAddedNode.addAssociatedNode( nodeTuple[1] );
                }
            }
            fLastAssociationCandidate.pop();
            fLastCommentNodeTuples.pop();
        }
    }

    /**
     * Calculates the proximity between the two given {@link ASTNode}. Usually
     * one of the nodes is a comment.
     *
     * @param nodeOne
     *            to calculate the proximity
     * @param nodeTwo
     *            to calculate the proximity
     * @return <code>2</code> if the comment node is on the same line as the
     *         other node, <code>1</code> if they are on adjacent line,
     *         <code>0</code> otherwise (times two)
     */
    private int proximityRating ( final ASTNode left, final ASTNode right ) {
        int result = 0;
        ASTNode nodeOne = left;
        ASTNode nodeTwo = right;
        // swap code, if nodeOne is not before nodeTwo
        if ( ( nodeTwo.sourceStart() - nodeOne.sourceStart() ) < 0 ) {
            final ASTNode tmpNode = nodeOne;
            nodeOne = nodeTwo;
            nodeTwo = tmpNode;
        }

        int endOfNodePosition = nodeOne.sourceEnd();

        // comment (nodeTwo) inside nodeOne
        if ( endOfNodePosition > nodeTwo.sourceStart() ) {

            // find position before comment start
            String findNodeEndTemp = fSource.substring( nodeOne.sourceStart(), nodeTwo.sourceStart() );

            // remove white space between nodeOne and comment (nodeTwo)
            final int lastNonSpaceChar = findNodeEndTemp.lastIndexOf( "[^\\s]" );
            if ( lastNonSpaceChar > -1 ) {
                findNodeEndTemp = findNodeEndTemp.substring( lastNonSpaceChar );
            }

            // end position of nodeOne before comment without succeeding white
            // space
            endOfNodePosition = nodeTwo.sourceStart() - findNodeEndTemp.length();
        }
        String betweenOneAndComment = fSource.substring( endOfNodePosition, nodeTwo.sourceStart() );

        // Comment is on the same line as code, but node in code
        final int positionAfterBracket = betweenOneAndComment.lastIndexOf( '}' );
        final int positionAfterSemicolon = betweenOneAndComment.lastIndexOf( ';' );
        final int sameLinePosition = Math.max( positionAfterBracket, positionAfterSemicolon );
        if ( sameLinePosition > -1 ) {
            betweenOneAndComment = betweenOneAndComment.substring( sameLinePosition + 1,
                    betweenOneAndComment.length() );
        }

        // 2 points if on the same line as well as inside the code,
        // i.e. there is no line break between the code and the comment
        final String newLine = System.getProperty( "line.separator" );
        if ( betweenOneAndComment.indexOf( newLine ) == -1 ) {
            result += 2;

            // 1 point if on the succeeding line,
            // i.e. only one line break between the code and the comment
        }
        else if ( betweenOneAndComment.replaceFirst( newLine, "" ).indexOf( newLine ) == -1 ) {
            result++;
        }

        return result * 2;
    }

    /**
     * Calculates the word matching between the candidate and the comment
     * string.
     *
     * @param candidate
     *            to match with
     * @param comment
     *            to match for
     * @return number of tokens the candidate and comment string share (times 2)
     */
    private int wordMatching ( final String candidate, final String comment ) {
        int result = 0;

        // split and tokenize candidate string into a hash table
        final Map<String, Integer> tokenMatchTable = new Hashtable<String, Integer>();
        final String[] candidateTokens = candidate.split( "[\\.\\s]+" );
        for ( final String candidateToken : candidateTokens ) {
            if ( tokenMatchTable.containsKey( candidateToken ) ) {
                tokenMatchTable.put( candidateToken, tokenMatchTable.remove( candidateToken ) + 1 );
            }
            else {
                tokenMatchTable.put( candidateToken, 1 );
            }
        }

        // find comment tokens in candidate tokens;
        // number of occurrences are taken as points
        final String[] commentTokens = comment.split( "\\s+" );
        for ( final String commentToken : commentTokens ) {
            if ( tokenMatchTable.containsKey( commentToken ) ) {
                result += tokenMatchTable.get( commentToken );
            }
        }

        return result * 2;
    }

    private String getASTString ( final ASTNode node ) {
        if ( node instanceof CompilationUnitDeclaration ) {
            return "";
        }
        final String result = node.toString();
        // method and type declaration strings contain their javadoc
        // get rid of the javadoc
        if ( node instanceof MethodDeclaration || node instanceof TypeDeclaration ) {
            final MethodDeclaration method = (MethodDeclaration) node;
            if ( method.javadoc != null ) {
                return result.replace( method.javadoc.toString(), "" );
            }
        }
        return result;
    }

    private String getCommentString ( final ASTNode node ) {
        return ( (Comment) node ).getComment();
    }

    @Override
    public boolean visit ( final Assignment assignment, final BlockScope scope ) {
        return visitExpression( assignment, scope );
    }

    @Override
    public void endVisit ( final Assignment assignment, final BlockScope scope ) {
        endVisitExpression( assignment, scope );
    }

    @Override
    public boolean visit ( final CompoundAssignment compoundAssignment, final BlockScope scope ) {
        return visitExpression( compoundAssignment, scope );
    }

    @Override
    public void endVisit ( final CompoundAssignment compoundAssignment, final BlockScope scope ) {
        endVisitExpression( compoundAssignment, scope );
    }

    @Override
    public boolean visit ( final PostfixExpression postfixExpression, final BlockScope scope ) {
        return visitExpression( postfixExpression, scope );
    }

    @Override
    public void endVisit ( final PostfixExpression postfixExpression, final BlockScope scope ) {
        endVisitExpression( postfixExpression, scope );
    }

    @Override
    public boolean visit ( final PrefixExpression prefixExpression, final BlockScope scope ) {
        return visitExpression( prefixExpression, scope );
    }

    @Override
    public void endVisit ( final PrefixExpression prefixExpression, final BlockScope scope ) {
        endVisitExpression( prefixExpression, scope );
    }

    @Override
    public boolean visit ( final AllocationExpression allocationExpression, final BlockScope scope ) {
        return visitExpression( allocationExpression, scope );
    }

    @Override
    public void endVisit ( final AllocationExpression allocationExpression, final BlockScope scope ) {
        endVisitExpression( allocationExpression, scope );
    }

    @Override
    public boolean visit ( final QualifiedAllocationExpression qualifiedAllocationExpression, final BlockScope scope ) {
        return visitExpression( qualifiedAllocationExpression, scope );
    }

    @Override
    public void endVisit ( final QualifiedAllocationExpression qualifiedAllocationExpression, final BlockScope scope ) {
        endVisitExpression( qualifiedAllocationExpression, scope );
    }

    @Override
    public boolean visit ( final AssertStatement assertStatement, final BlockScope scope ) {
        preVisit( assertStatement );
        String value = assertStatement.assertExpression.toString();
        if ( assertStatement.exceptionArgument != null ) {
            value += COLON + assertStatement.exceptionArgument.toString();
        }
        push( fASTHelper.convertNode( assertStatement ), value, assertStatement.sourceStart(),
                assertStatement.sourceEnd() + 1 );
        return false;
    }

    @Override
    public void endVisit ( final AssertStatement assertStatement, final BlockScope scope ) {
        pop( assertStatement );
        postVisit( assertStatement );
    }

    @Override
    public boolean visit ( final Block block, final BlockScope scope ) {
        // skip block as it is not interesting
        return true;
    }

    @Override
    public void endVisit ( final Block block, final BlockScope scope ) {
        // do nothing
    }

    @Override
    public boolean visit ( final BreakStatement breakStatement, final BlockScope scope ) {
        preVisit( breakStatement );
        pushValuedNode( breakStatement, breakStatement.label != null ? String.valueOf( breakStatement.label ) : "" );
        return false;
    }

    @Override
    public void endVisit ( final BreakStatement breakStatement, final BlockScope scope ) {
        pop( breakStatement );
        postVisit( breakStatement );
    }

    @Override
    public boolean visit ( final ExplicitConstructorCall explicitConstructor, final BlockScope scope ) {
        preVisit( explicitConstructor );
        pushValuedNode( explicitConstructor, explicitConstructor.toString() );
        return false;
    }

    @Override
    public void endVisit ( final ExplicitConstructorCall explicitConstructor, final BlockScope scope ) {
        pop( explicitConstructor );
        postVisit( explicitConstructor );
    }

    @Override
    public boolean visit ( final ContinueStatement continueStatement, final BlockScope scope ) {
        preVisit( continueStatement );
        pushValuedNode( continueStatement,
                continueStatement.label != null ? String.valueOf( continueStatement.label ) : "" );
        return false;
    }

    @Override
    public void endVisit ( final ContinueStatement continueStatement, final BlockScope scope ) {
        pop( continueStatement );
        postVisit( continueStatement );
    }

    @Override
    public boolean visit ( final DoStatement doStatement, final BlockScope scope ) {
        preVisit( doStatement );
        pushValuedNode( doStatement, doStatement.condition.toString() );
        doStatement.action.traverse( this, scope );
        return false;
    }

    @Override
    public void endVisit ( final DoStatement doStatement, final BlockScope scope ) {
        pop( doStatement );
        postVisit( doStatement );
    }

    @Override
    public boolean visit ( final EmptyStatement emptyStatement, final BlockScope scope ) {
        preVisit( emptyStatement );
        pushEmptyNode( emptyStatement );
        return false;
    }

    @Override
    public void endVisit ( final EmptyStatement emptyStatement, final BlockScope scope ) {
        pop( emptyStatement );
        postVisit( emptyStatement );
    }

    @Override
    public boolean visit ( final ForeachStatement foreachStatement, final BlockScope scope ) {
        preVisit( foreachStatement );
        pushValuedNode( foreachStatement,
                foreachStatement.elementVariable.printAsExpression( 0, new StringBuffer() ).toString() + COLON
                        + foreachStatement.collection.toString() );
        foreachStatement.action.traverse( this, scope );
        return false;
    }

    @Override
    public void endVisit ( final ForeachStatement foreachStatement, final BlockScope scope ) {
        pop( foreachStatement );
        postVisit( foreachStatement );
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
    public boolean visitExpression ( final Expression expression, final BlockScope scope ) {
        preVisit( expression );
        // all expression processed in this method are statements
        // - use printStatement to get the ';' at the end of the expression
        // - extend the length of the statement by 1 to add ';'
        push( fASTHelper.convertNode( expression ), expression.toString() + ';', expression.sourceStart(),
                expression.sourceEnd() + 1 );
        return false;
    }

    private String getSource ( final int start, final int end ) {
        return fSource.substring( start, end + 1 );
    }

    /**
     * Ends visiting an expression.
     *
     * @param expression
     *            to end visit with
     * @param scope
     *            in which the visitor visits
     */
    public void endVisitExpression ( final Expression expression, final BlockScope scope ) {
        pop( expression );
        postVisit( expression );
    }

    @Override
    public boolean visit ( final ForStatement forStatement, final BlockScope scope ) {
        preVisit( forStatement );
        // loop condition
        String value = "";
        if ( forStatement.condition != null ) {
            value = forStatement.condition.toString();
        }
        pushValuedNode( forStatement, value );
        forStatement.action.traverse( this, scope );

        // loop init
        if ( forStatement.initializations != null && forStatement.initializations.length > 0 ) {
            for ( final Statement initStatement : forStatement.initializations ) {
                push( JavaEntityType.FOR_INIT, initStatement.toString(), initStatement.sourceStart(),
                        initStatement.sourceEnd() );

                initStatement.traverse( this, scope );

                pop( initStatement );
            }
        }

        // loop afterthought
        if ( forStatement.increments != null && forStatement.increments.length > 0 ) {
            for ( final Statement incrementStatement : forStatement.increments ) {
                push( JavaEntityType.FOR_INCR, incrementStatement.toString(), incrementStatement.sourceStart(),
                        incrementStatement.sourceEnd() );

                incrementStatement.traverse( this, scope );

                pop( incrementStatement );
            }
        }

        return false;
    }

    @Override
    public void endVisit ( final ForStatement forStatement, final BlockScope scope ) {
        pop( forStatement );
        postVisit( forStatement );
    }

    @Override
    public boolean visit ( final IfStatement ifStatement, final BlockScope scope ) {
        preVisit( ifStatement );
        final String expression = ifStatement.condition.toString();
        push( JavaEntityType.IF_STATEMENT, expression, ifStatement.sourceStart(), ifStatement.sourceEnd() );
        if ( ifStatement.thenStatement != null ) {
            push( JavaEntityType.THEN_STATEMENT, expression, ifStatement.thenStatement.sourceStart(),
                    ifStatement.thenStatement.sourceEnd() );
            ifStatement.thenStatement.traverse( this, scope );
            pop( ifStatement.thenStatement );
        }
        if ( ifStatement.elseStatement != null ) {
            push( JavaEntityType.ELSE_STATEMENT, expression, ifStatement.elseStatement.sourceStart(),
                    ifStatement.elseStatement.sourceEnd() );
            ifStatement.elseStatement.traverse( this, scope );
            pop( ifStatement.elseStatement );
        }
        return false;
    }

    @Override
    public void endVisit ( final IfStatement ifStatement, final BlockScope scope ) {
        pop( ifStatement );
        postVisit( ifStatement );
    }

    @Override
    public boolean visit ( final LabeledStatement labeledStatement, final BlockScope scope ) {
        preVisit( labeledStatement );
        pushValuedNode( labeledStatement, String.valueOf( labeledStatement.label ) );
        labeledStatement.statement.traverse( this, scope );
        return false;
    }

    @Override
    public void endVisit ( final LabeledStatement labeledStatement, final BlockScope scope ) {
        pop( labeledStatement );
        postVisit( labeledStatement );
    }

    @Override
    public boolean visit ( final LocalDeclaration localDeclaration, final BlockScope scope ) {
        preVisit( localDeclaration );
        final int start = localDeclaration.type.sourceStart();
        int end = start;
        if ( localDeclaration.initialization != null ) {
            end = localDeclaration.initialization.sourceEnd();
        }
        else {
            end = localDeclaration.sourceEnd;
        }
        push( fASTHelper.convertNode( localDeclaration ), localDeclaration.toString(), start, end + 1 );
        return true;
    }

    @Override
    public void endVisit ( final LocalDeclaration localDeclaration, final BlockScope scope ) {
        pop( localDeclaration );
        postVisit( localDeclaration );
    }

    @Override
    public boolean visit ( final MessageSend messageSend, final BlockScope scope ) {
        preVisit( messageSend );
        return visitExpression( messageSend, scope );
    }

    @Override
    public void endVisit ( final MessageSend messageSend, final BlockScope scope ) {
        endVisitExpression( messageSend, scope );
        postVisit( messageSend );
    }

    @Override
    public boolean visit ( final ReturnStatement returnStatement, final BlockScope scope ) {
        preVisit( returnStatement );
        pushValuedNode( returnStatement,
                returnStatement.expression != null ? "return " + returnStatement.expression.toString() + ';' : "" );
        return false;
    }

    @Override
    public void endVisit ( final ReturnStatement returnStatement, final BlockScope scope ) {
        pop( returnStatement );
        postVisit( returnStatement );
    }

    @Override
    public boolean visit ( final CaseStatement caseStatement, final BlockScope scope ) {
        preVisit( caseStatement );
        pushValuedNode( caseStatement,
                caseStatement.constantExpressions != null ? Arrays.toString( caseStatement.constantExpressions )
                        : "default" );
        return false;
    }

    @Override
    public void endVisit ( final CaseStatement caseStatement, final BlockScope scope ) {
        pop( caseStatement );
        postVisit( caseStatement );
    }

    @Override
    public boolean visit ( final SwitchStatement switchStatement, final BlockScope scope ) {
        preVisit( switchStatement );
        pushValuedNode( switchStatement, switchStatement.expression.toString() );
        visitNodes( switchStatement.statements, scope );
        return true;
    }

    @Override
    public void endVisit ( final SwitchStatement switchStatement, final BlockScope scope ) {
        pop( switchStatement );
        postVisit( switchStatement );
    }

    @Override
    public boolean visit ( final SynchronizedStatement synchronizedStatement, final BlockScope scope ) {
        preVisit( synchronizedStatement );
        pushValuedNode( synchronizedStatement, synchronizedStatement.expression.toString() );
        return true;
    }

    @Override
    public void endVisit ( final SynchronizedStatement synchronizedStatement, final BlockScope scope ) {
        pop( synchronizedStatement );
        postVisit( synchronizedStatement );
    }

    @Override
    public boolean visit ( final ThrowStatement throwStatement, final BlockScope scope ) {
        preVisit( throwStatement );
        pushValuedNode( throwStatement, throwStatement.exception.toString() + ';' );
        return false;
    }

    @Override
    public void endVisit ( final ThrowStatement throwStatement, final BlockScope scope ) {
        pop( throwStatement );
        postVisit( throwStatement );
    }

    /* changes kpresle */
    @Override
    public boolean visit ( final YieldStatement yieldStatement, final BlockScope scope ) {
        preVisit( yieldStatement );
        pushValuedNode( yieldStatement, yieldStatement.toString() );
        return true;
    }

    @Override
    public void endVisit ( final YieldStatement yieldStatement, final BlockScope scope ) {
        pop( yieldStatement );
        postVisit( yieldStatement );
    }

    @Override
    public boolean visit ( final LambdaExpression lambdaExpression, final BlockScope blockScope ) {

        preVisit( lambdaExpression );
        pushValuedNode( lambdaExpression, lambdaExpression.toString() );
        return true;

    }

    @Override
    public void endVisit ( final LambdaExpression lambdaExpression, final BlockScope blockScope ) {
        pop( lambdaExpression );
        postVisit( lambdaExpression );
    }

    /* end changes kpresle */

    @Override
    public boolean visit ( final TryStatement node, final BlockScope scope ) {
        preVisit( node );
        pushEmptyNode( node );
        push( JavaEntityType.BODY, "", node.tryBlock.sourceStart(), node.tryBlock.sourceEnd() );
        node.tryBlock.traverse( this, scope );
        pop( node.tryBlock );
        visitCatchClauses( node, scope );
        visitFinally( node, scope );
        return false;
    }

    private void visitFinally ( final TryStatement node, final BlockScope scope ) {
        if ( node.finallyBlock != null ) {
            push( JavaEntityType.FINALLY, "", node.finallyBlock.sourceStart(), node.finallyBlock.sourceEnd() );
            node.finallyBlock.traverse( this, scope );
            pop( node.finallyBlock );
        }
    }

    private void visitCatchClauses ( final TryStatement node, final BlockScope scope ) {
        if ( ( node.catchBlocks != null ) && ( node.catchBlocks.length > 0 ) ) {
            final Block lastCatchBlock = node.catchBlocks[node.catchBlocks.length - 1];
            push( JavaEntityType.CATCH_CLAUSES, "", node.tryBlock.sourceEnd + 1, lastCatchBlock.sourceEnd );
            int start = node.tryBlock.sourceEnd();
            for ( int i = 0; i < node.catchArguments.length; i++ ) {
                final int catchClauseSourceStart = retrieveStartingCatchPosition( start,
                        node.catchArguments[i].sourceStart );
                push( JavaEntityType.CATCH_CLAUSE, node.catchArguments[i].type.toString(), catchClauseSourceStart,
                        node.catchBlocks[i].sourceEnd );
                node.catchBlocks[i].traverse( this, scope );
                pop( node.catchArguments[i].type );
                start = node.catchBlocks[i].sourceEnd();
            }
            pop( null );
        }
    }

    // logic taken from org.eclipse.jdt.core.dom.ASTConverter
    private int retrieveStartingCatchPosition ( final int start, final int end ) {
        fScanner.resetTo( start, end );
        try {
            int token;
            while ( ( token = fScanner.getNextToken() ) != TerminalTokens.TokenNameEOF ) {
                switch ( token ) {
                    case TerminalTokens.TokenNamecatch:// 225
                        return fScanner.startPosition;
                }
            }
            // CHECKSTYLE:OFF
        }
        catch ( final InvalidInputException e ) {
            // CHECKSTYLE:ON
            // ignore
        }
        return -1;
    }

    @Override
    public void endVisit ( final TryStatement tryStatement, final BlockScope scope ) {
        pop( tryStatement );
        postVisit( tryStatement );
    }

    @Override
    public boolean visit ( final WhileStatement whileStatement, final BlockScope scope ) {
        preVisit( whileStatement );
        push( fASTHelper.convertNode( whileStatement ), whileStatement.condition.toString(),
                whileStatement.sourceStart(), whileStatement.sourceEnd );
        whileStatement.action.traverse( this, scope );
        return false;
    }

    @Override
    public void endVisit ( final WhileStatement whileStatement, final BlockScope scope ) {
        pop( whileStatement );
        postVisit( whileStatement );
    }

    private void visitNodes ( final ASTNode[] nodes, final BlockScope scope ) {
        for ( final ASTNode element : nodes ) {
            element.traverse( this, scope );
        }
    }

    private void pushValuedNode ( final ASTNode node, final String value ) {
        push( fASTHelper.convertNode( node ), value, node.sourceStart(), node.sourceEnd() );
    }

    private void pushEmptyNode ( final ASTNode node ) {
        push( fASTHelper.convertNode( node ), "", node.sourceStart(), node.sourceEnd() );
    }

    private void push ( final EntityType label, final String value, final int start, final int end ) {
        final Node n = new Node( label, value.trim() );
        n.setEntity( new SourceCodeEntity( value.trim(), label, new SourceRange( start, end ) ) );
        getCurrentParent().add( n );
        fNodeStack.push( n );
    }

    private void pop ( final ASTNode node ) {
        fLastVisitedNode = node;
        fLastAddedNode = fNodeStack.pop();
    }

    private Node getCurrentParent () {
        return fNodeStack.peek();
    }

    private boolean isUnusableNode ( final ASTNode node ) {
        return node instanceof Comment;
    }
}
