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
package org.apache.directory.mitosis.operation;


import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchResult;

import org.apache.directory.mitosis.common.CSN;
import org.apache.directory.mitosis.operation.support.EntryUtil;
import org.apache.directory.mitosis.store.ReplicationStore;
import org.apache.directory.server.core.entry.ServerEntry;
import org.apache.directory.server.core.entry.ServerEntryUtils;
import org.apache.directory.server.core.interceptor.context.AddOperationContext;
import org.apache.directory.server.core.interceptor.context.DeleteOperationContext;
import org.apache.directory.server.core.interceptor.context.ListOperationContext;
import org.apache.directory.server.core.interceptor.context.LookupOperationContext;
import org.apache.directory.server.core.partition.PartitionNexus;
import org.apache.directory.server.schema.registries.Registries;
import org.apache.directory.shared.ldap.name.LdapDN;


/**
 * An {@link Operation} that adds a new entry.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public class AddEntryOperation extends Operation
{
    private static final long serialVersionUID = 2294492811671880570L;

    private final LdapDN normalizedName;
    private final Attributes entry;


    /**
     * Creates a new instance.
     * 
     * @param entry an entry
     */
    public AddEntryOperation( CSN csn, LdapDN normalizedName, ServerEntry entry )
    {
        super( csn );

        assert normalizedName != null;
        assert entry != null;

        this.normalizedName = normalizedName;
        this.entry = ServerEntryUtils.toAttributesImpl( entry );
    }


    public String toString()
    {
        return super.toString() + ": [" + normalizedName + "].new( " + entry + " )";
    }


    protected void execute0( PartitionNexus nexus, ReplicationStore store, Registries registries )
        throws NamingException
    {
        if ( !EntryUtil.isEntryUpdatable( registries, nexus, normalizedName, getCSN() ) )
        {
            return;
        }
        
        EntryUtil.createGlueEntries( registries, nexus, normalizedName, false );

        // Replace the entry if an entry with the same name exists.
        if ( nexus.lookup( new LookupOperationContext( registries, normalizedName ) ) != null )
        {
            recursiveDelete( nexus, normalizedName, registries );
        }

        nexus.add( new AddOperationContext( registries, normalizedName, ServerEntryUtils.toServerEntry( entry, normalizedName, registries ) ) );
    }


    @SuppressWarnings("unchecked")
    private void recursiveDelete( PartitionNexus nexus, LdapDN normalizedName, Registries registries )
        throws NamingException
    {
        NamingEnumeration<SearchResult> ne = nexus.list( new ListOperationContext( registries, normalizedName ) );
        
        if ( !ne.hasMore() )
        {
            nexus.delete( new DeleteOperationContext( registries, normalizedName ) );
            return;
        }

        while ( ne.hasMore() )
        {
            SearchResult sr = ne.next();
            LdapDN dn = new LdapDN( sr.getName() );
            dn.normalize( registries.getAttributeTypeRegistry().getNormalizerMapping() );
            recursiveDelete( nexus, dn, registries );
        }
        
        nexus.delete( new DeleteOperationContext( registries, normalizedName ) );
    }
}
