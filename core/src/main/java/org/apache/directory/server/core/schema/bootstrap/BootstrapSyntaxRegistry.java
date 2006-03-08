/*
 *   Copyright 2004 The Apache Software Foundation
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */
package org.apache.directory.server.core.schema.bootstrap;


import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.naming.NamingException;

import org.apache.directory.server.core.schema.OidRegistry;
import org.apache.directory.server.core.schema.SyntaxRegistry;
import org.apache.directory.shared.ldap.schema.Syntax;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * A SyntaxRegistry service available during server startup when other resources
 * like a syntax backing store is unavailable.
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class BootstrapSyntaxRegistry implements SyntaxRegistry
{
    /** static class logger */
    private final static Logger log = LoggerFactory.getLogger( BootstrapSyntaxRegistry.class );
    /** a map of entries using an OID for the key and a Syntax for the value */
    private final Map byOid;
    /** maps an OID to a schema name*/
    private final Map oidToSchema;
    /** the OID oidRegistry this oidRegistry uses to register new syntax OIDs */
    private final OidRegistry oidRegistry;


    // ------------------------------------------------------------------------
    // C O N S T R U C T O R S
    // ------------------------------------------------------------------------

    /**
     * Creates a BootstrapSyntaxRegistry.
     */
    public BootstrapSyntaxRegistry(OidRegistry registry)
    {
        this.oidRegistry = registry;
        this.byOid = new HashMap();
        this.oidToSchema = new HashMap();
    }


    // ------------------------------------------------------------------------
    // SyntaxRegistry interface methods
    // ------------------------------------------------------------------------

    
    public Syntax lookup( String id ) throws NamingException
    {
        id = oidRegistry.getOid( id );

        if ( byOid.containsKey( id ) )
        {
            Syntax syntax = ( Syntax ) byOid.get( id );
            if ( log.isDebugEnabled() )
            {
                log.debug( "looked up using id '" + id + "': " + syntax );
            }
            return syntax;
        }

        NamingException fault = new NamingException( "Unknown syntax OID " + id );
        throw fault;
    }


    public void register( String schema, Syntax syntax ) throws NamingException
    {
        if ( byOid.containsKey( syntax.getOid() ) )
        {
            NamingException e = new NamingException( "syntax w/ OID " + syntax.getOid()
                + " has already been registered!" );
            throw e;
        }

        oidRegistry.register( syntax.getName(), syntax.getOid() );
        byOid.put( syntax.getOid(), syntax );
        oidToSchema.put( syntax.getOid(), schema );
        if ( log.isDebugEnabled() )
        {
            log.debug( "registered syntax: " + syntax );
        }
    }


    public boolean hasSyntax( String id )
    {
        if ( oidRegistry.hasOid( id ) )
        {
            try
            {
                return byOid.containsKey( oidRegistry.getOid( id ) );
            }
            catch ( NamingException e )
            {
                return false;
            }
        }

        return false;
    }


    public String getSchemaName( String id ) throws NamingException
    {
        id = oidRegistry.getOid( id );
        if ( oidToSchema.containsKey( id ) )
        {
            return ( String ) oidToSchema.get( id );
        }

        throw new NamingException( "OID " + id + " not found in oid to " + "schema name map!" );
    }


    public Iterator list()
    {
        return byOid.values().iterator();
    }
}
