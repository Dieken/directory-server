/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package org.apache.directory.server.xdbm.search.impl;


import org.apache.directory.shared.ldap.filter.OrNode;
import org.apache.directory.shared.ldap.filter.ExprNode;
import org.apache.directory.server.xdbm.IndexEntry;

import javax.naming.directory.Attributes;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;


/**
 * An Evaluator for logical disjunction (OR) expressions.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $$Rev$$
 */
public class OrEvaluator implements Evaluator<OrNode,Attributes>
{
    private final List<Evaluator<? extends ExprNode, Attributes>> evaluators;

    private final OrNode node;


    public OrEvaluator( OrNode node, List<Evaluator<? extends ExprNode, Attributes>> evaluators )
    {
        this.node = node;
        this.evaluators = optimize( evaluators );
    }


    /**
     * Takes a set of Evaluators and copies then sorts them in a new list with
     * decreasing scan counts on their expression nodes.  This is done to have
     * the Evaluators with the greatest scan counts which have the highest
     * probability of accepting a candidate first.  That will increase the
     * chance of shorting the checks on evaluators early so extra lookups and
     * comparisons are avoided.
     *
     * @param unoptimized the unoptimized list of Evaluators
     * @return optimized Evaluator list with decreasing scan count ordering
     */
    private List<Evaluator<? extends ExprNode,Attributes>>
        optimize( List<Evaluator<? extends ExprNode, Attributes>> unoptimized )
    {
        List<Evaluator<? extends ExprNode, Attributes>> optimized =
            new ArrayList<Evaluator<? extends ExprNode, Attributes>>( unoptimized.size() );
        optimized.addAll( unoptimized );
        Collections.sort( optimized, new Comparator<Evaluator<? extends ExprNode,Attributes>>()
        {
            public int compare( Evaluator<? extends ExprNode, Attributes> e1, Evaluator<? extends ExprNode, Attributes> e2 )
            {
                int scanCount1 = ( Integer ) e1.getExpression().get( "count" );
                int scanCount2 = ( Integer ) e2.getExpression().get( "count" );

                if ( scanCount1 == scanCount2 )
                {
                    return 0;
                }

                /*
                 * We want the Evaluator with the largest scan count first
                 * since this node has the highest probability of accepting,
                 * or rather the least probability of failing.  That way we
                 * can short the sub-expression evaluation process.
                 */
                if ( scanCount1 < scanCount2 )
                {
                    return 1;
                }

                return -1;
            }
        });

        return optimized;
    }


    public boolean evaluate( IndexEntry<?, Attributes> indexEntry ) throws Exception
    {
        for ( Evaluator<?,Attributes> evaluator : evaluators )
        {
            if ( evaluator.evaluate( indexEntry ) )
            {
                return true;
            }
        }

        return false;
    }


    public OrNode getExpression()
    {
        return node;
    }
}