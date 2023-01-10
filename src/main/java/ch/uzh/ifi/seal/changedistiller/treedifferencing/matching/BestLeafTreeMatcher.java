package ch.uzh.ifi.seal.changedistiller.treedifferencing.matching;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

import ch.uzh.ifi.seal.changedistiller.treedifferencing.LeafPair;
import ch.uzh.ifi.seal.changedistiller.treedifferencing.Node;
import ch.uzh.ifi.seal.changedistiller.treedifferencing.NodePair;
import ch.uzh.ifi.seal.changedistiller.treedifferencing.TreeMatcher;
import ch.uzh.ifi.seal.changedistiller.treedifferencing.matching.measure.NodeSimilarityCalculator;
import ch.uzh.ifi.seal.changedistiller.treedifferencing.matching.measure.StringSimilarityCalculator;
import ch.uzh.ifi.seal.changedistiller.treedifferencing.matching.measure.TokenBasedCalculator;

/**
 * Implementation of the best matching tree matcher.
 *
 * @author Beat Fluri
 *
 */
public class BestLeafTreeMatcher implements TreeMatcher {

    private StringSimilarityCalculator       fLeafGenericStringSimilarityCalculator;
    private double                           fLeafGenericStringSimilarityThreshold;

    // Hardcoded! Needs integration into benchmark facilities.
    private final StringSimilarityCalculator fLeafCommentStringSimilarityCalculator   = new TokenBasedCalculator();
    private static final double              LEAF_COMMENT_STRING_SIMILARITY_THRESHOLD = 0.4;

    private NodeSimilarityCalculator         fNodeSimilarityCalculator;
    private double                           fNodeSimilarityThreshold;

    private StringSimilarityCalculator       fNodeStringSimilarityCalculator;
    private double                           fNodeStringSimilarityThreshold;
    private static final double              WEIGHTING_THRESHOLD                      = 0.8;

    private boolean                          fDynamicEnabled;
    private int                              fDynamicDepth;
    private double                           fDynamicThreshold;

    private Set<NodePair>                    fMatch;

    @Override
    public void init ( final StringSimilarityCalculator leafStringSimCalc, final double leafStringSimThreshold,
            final NodeSimilarityCalculator nodeSimCalc, final double nodeSimThreshold ) {
        fLeafGenericStringSimilarityCalculator = leafStringSimCalc;
        fLeafGenericStringSimilarityThreshold = leafStringSimThreshold;
        fNodeStringSimilarityCalculator = leafStringSimCalc;
        fNodeStringSimilarityThreshold = leafStringSimThreshold;
        fNodeSimilarityCalculator = nodeSimCalc;
        fNodeSimilarityThreshold = nodeSimThreshold;
    }

    @Override
    public void init ( final StringSimilarityCalculator leafStringSimCalc, final double leafStringSimThreshold,
            final StringSimilarityCalculator nodeStringSimCalc, final double nodeStringSimThreshold,
            final NodeSimilarityCalculator nodeSimCalc, final double nodeSimThreshold ) {
        init( leafStringSimCalc, leafStringSimThreshold, nodeSimCalc, nodeSimThreshold );
        fNodeStringSimilarityCalculator = nodeStringSimCalc;
        fNodeStringSimilarityThreshold = nodeStringSimThreshold;
    }

    @Override
    public void enableDynamicThreshold ( final int depth, final double threshold ) {
        fDynamicDepth = depth;
        fDynamicThreshold = threshold;
        fDynamicEnabled = true;
    }

    @Override
    public void disableDynamicThreshold () {
        fDynamicEnabled = false;
    }

    @Override
    public void setMatchingSet ( final Set<NodePair> matchingSet ) {
        fMatch = matchingSet;
    }

    @Override
    public void match ( final Node left, final Node right ) {
        final List<LeafPair> matchedLeafs = matchLeaves( left, right );
        // sort matching set according to similarity in descending order
        Collections.sort( matchedLeafs );
        markMatchedLeaves( matchedLeafs );
        matchNodes( left, right );
    }

    @SuppressWarnings ( "unchecked" )
    private void matchNodes ( final Node left, final Node right ) {
        for ( final Enumeration<Node> leftNodes = (Enumeration<Node>) (Enumeration< ? >) left
                .postorderEnumeration(); leftNodes.hasMoreElements(); ) {
            final Node x = leftNodes.nextElement();
            if ( !x.isMatched() && ( !x.isLeaf() || x.isRoot() ) ) {
                for ( final Enumeration<Node> rightNodes = (Enumeration<Node>) (Enumeration< ? >) right
                        .postorderEnumeration(); rightNodes.hasMoreElements() && !x.isMatched(); ) {
                    final Node y = rightNodes.nextElement();
                    if ( ( !y.isMatched() && ( !y.isLeaf() || y.isRoot() ) ) && equal( x, y ) ) {
                        fMatch.add( new NodePair( x, y ) );
                        x.enableMatched();
                        y.enableMatched();
                    }
                }
            }
        }
    }

    private void markMatchedLeaves ( final List<LeafPair> matchedLeafs ) {
        for ( final LeafPair pair : matchedLeafs ) {
            final Node x = pair.getLeft();
            final Node y = pair.getRight();
            if ( ! ( x.isMatched() || y.isMatched() ) ) {
                fMatch.add( pair );
                x.enableMatched();
                y.enableMatched();
            }
        }
    }

    @SuppressWarnings ( "unchecked" )
    private List<LeafPair> matchLeaves ( final Node left, final Node right ) {
        final List<LeafPair> matchedLeafs = new ArrayList<LeafPair>();
        for ( final Enumeration<Node> leftNodes = (Enumeration<Node>) (Enumeration< ? >) left
                .postorderEnumeration(); leftNodes.hasMoreElements(); ) {
            final Node x = leftNodes.nextElement();
            if ( x.isLeaf() ) {
                for ( final Enumeration<Node> rightNodes = (Enumeration<Node>) (Enumeration< ? >) right
                        .postorderEnumeration(); rightNodes.hasMoreElements(); ) {
                    final Node y = rightNodes.nextElement();
                    if ( y.isLeaf() && haveSameLabel( x, y ) ) {
                        double similarity = 0;

                        if ( x.getLabel().isComment() ) {
                            similarity = fLeafCommentStringSimilarityCalculator.calculateSimilarity( x.getValue(),
                                    y.getValue() );

                            // Important! Otherwhise nodes that match poorly
                            // will make it into final matching set,
                            // if no better matches are found!
                            if ( similarity >= LEAF_COMMENT_STRING_SIMILARITY_THRESHOLD ) {
                                matchedLeafs.add( new LeafPair( x, y, similarity ) );
                            }

                        }
                        else { // ...other statements.
                            similarity = fLeafGenericStringSimilarityCalculator.calculateSimilarity( x.getValue(),
                                    y.getValue() );

                            // Important! Otherwise nodes that match poorly will
                            // make it into final matching set,
                            // if no better matches are found!
                            if ( similarity >= fLeafGenericStringSimilarityThreshold ) {
                                matchedLeafs.add( new LeafPair( x, y, similarity ) );
                            }
                        }
                    }
                }
            }
        }
        return matchedLeafs;
    }

    private boolean haveSameLabel ( final Node x, final Node y ) {
        return x.getLabel() == y.getLabel();
    }

    private boolean equal ( final Node x, final Node y ) {
        // inner nodes
        if ( areInnerOrRootNodes( x, y ) && haveSameLabel( x, y ) ) {
            // little heuristic
            if ( x.isRoot() ) {
                return x.getValue().equals( x.getValue() );
            }
            else {
                double t = fNodeSimilarityThreshold;
                if ( fDynamicEnabled && ( x.getLeafCount() < fDynamicDepth ) && ( y.getLeafCount() < fDynamicDepth ) ) {
                    t = fDynamicThreshold;
                }
                final double simNode = fNodeSimilarityCalculator.calculateSimilarity( x, y );
                final double simString = fNodeStringSimilarityCalculator.calculateSimilarity( x.getValue(),
                        y.getValue() );
                if ( ( simString < fNodeStringSimilarityThreshold ) && ( simNode >= WEIGHTING_THRESHOLD ) ) {
                    return true;
                }
                else {
                    return ( simNode >= t ) && ( simString >= fNodeStringSimilarityThreshold );
                }
            }
        }
        return false;
    }

    private boolean areInnerOrRootNodes ( final Node x, final Node y ) {
        return areInnerNodes( x, y ) || areRootNodes( x, y );
    }

    private boolean areInnerNodes ( final Node x, final Node y ) {
        return ( !x.isLeaf() && !y.isLeaf() );
    }

    private boolean areRootNodes ( final Node x, final Node y ) {
        return ( x.isRoot() && y.isRoot() );
    }
}
