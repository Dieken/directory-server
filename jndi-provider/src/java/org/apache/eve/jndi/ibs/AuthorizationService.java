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
package org.apache.eve.jndi.ibs;


import java.io.IOException;
import javax.naming.Name;
import javax.naming.NamingException;
import javax.naming.ldap.LdapContext;
import javax.naming.directory.Attributes;
import javax.naming.directory.ModificationItem;
import javax.naming.directory.SearchControls;

import org.apache.eve.SystemPartition;
import org.apache.eve.db.SearchResultFilter;
import org.apache.eve.db.DbSearchResult;
import org.apache.eve.exception.EveNoPermissionException;
import org.apache.eve.jndi.*;
import org.apache.ldap.common.name.NameComponentNormalizer;
import org.apache.ldap.common.name.DnParser;


/**
 * A service used to for applying access controls to backing store operations.
 *
 * @author <a href="mailto:directory-dev@incubator.apache.org">Apache Directory Project</a>
 * @version $Rev$
 */
public class AuthorizationService extends BaseInterceptor
{
    /** the administrator's distinguished {@link Name} */
    private static final Name ADMIN_DN = SystemPartition.getAdminDn();
    /** the base distinguished {@link Name} for all users */
    private static final Name USER_BASE_DN = SystemPartition.getUsersBaseDn();
    /** the base distinguished {@link Name} for all groups */
    private static final Name GROUP_BASE_DN = SystemPartition.getGroupsBaseDn();

    /** the name parser used by this service */
    private final DnParser dnParser;


    /**
     * Creates an authorization service interceptor.
     *
     * @param normalizer a schema enabled name component normalizer
     * @param filterService a {@link FilterService} to register filters with
     */
    public AuthorizationService( NameComponentNormalizer normalizer,
                                 FilterService filterService )
            throws NamingException
    {
        try
        {
            this.dnParser = new DnParser( normalizer );
        }
        catch ( IOException e )
        {
            NamingException ne = new NamingException();
            ne.setRootCause( e );
            throw ne;
        }

        AuthorizationFilter filter = new AuthorizationFilter();
        filterService.addLookupFilter( filter );
        filterService.addSearchResultFilter( filter );
    }


    // Note:
    //    Lookup, search and list operations need to be handled using a filter
    // and so we need access to the filter service.


    protected void delete( Name name ) throws NamingException
    {
        Invocation invocation = getInvocation();

        if ( invocation.getState() == InvocationStateEnum.PREINVOCATION )
        {
            Name principalDn = getPrincipal( invocation ).getDn();

            if ( name == ADMIN_DN || name.equals( ADMIN_DN ) )
            {
                String msg = "User " + principalDn;
                msg += " does not have permission to delete the admin account.";
                msg += " No one not even the admin can delete this account!";
                throw new EveNoPermissionException( msg );
            }

            if ( name.size() > 2 && name.startsWith( USER_BASE_DN )
                    && ! principalDn.equals( ADMIN_DN ) )
            {
                String msg = "User " + principalDn;
                msg += " does not have permission to delete the user account: ";
                msg += name + ". Only the admin can delete user accounts.";
                throw new EveNoPermissionException( msg );
            }

            if ( name.size() > 2 && name.startsWith( GROUP_BASE_DN )
                    && ! principalDn.equals( ADMIN_DN ) )
            {
                String msg = "User " + principalDn;
                msg += " does not have permission to delete the group entry: ";
                msg += name + ". Only the admin can delete groups.";
                throw new EveNoPermissionException( msg );
            }
        }
    }


    /**
     * Note that we do nothing here. First because this is not an externally
     * exposed function via the JNDI interfaces.  It is used internally be the
     * provider for optimization purposes so there is no reason for us to start
     * to constrain it.
     *
     * @see BaseInterceptor#hasEntry(Name)
     */
    protected void hasEntry( Name dn ) throws NamingException
    {
    }


    // ------------------------------------------------------------------------
    // Entry Modification Operations
    // ------------------------------------------------------------------------


    /**
     * This policy needs to be really tight too because some attributes may
     * take part in giving the user permissions to protected resources.  We
     * do not want users to self access these resources.  As far as we're
     * concerned no one but the admin needs access.
     *
     * @see BaseInterceptor#modify(Name, int, Attributes)
     */
    protected void modify( Name dn, int modOp, Attributes mods ) throws NamingException
    {
        protectModifyAlterations( dn );
    }


    /**
     * This policy needs to be really tight too because some attributes may
     * take part in giving the user permissions to protected resources.  We
     * do not want users to self access these resources.  As far as we're
     * concerned no one but the admin needs access.
     *
     * @see BaseInterceptor#modify(Name, ModificationItem[])
     */
    protected void modify( Name dn, ModificationItem[] mods ) throws NamingException
    {
        protectModifyAlterations( dn );
    }


    private void protectModifyAlterations( Name dn ) throws EveNoPermissionException
    {
        Invocation invocation = getInvocation();

        if ( invocation.getState() == InvocationStateEnum.PREINVOCATION )
        {
            Name principalDn = getPrincipal( invocation ).getDn();

            if ( ! principalDn.equals( ADMIN_DN ) )
            {
                if ( dn == ADMIN_DN || dn.equals( ADMIN_DN ) )
                {
                    String msg = "User " + principalDn;
                    msg += " does not have permission to modify the admin account.";
                    throw new EveNoPermissionException( msg );
                }

                if ( dn.size() > 2 && dn.startsWith( USER_BASE_DN ) )
                {
                    String msg = "User " + principalDn;
                    msg += " does not have permission to modify the account of the";
                    msg += " user " + dn + ".\nEven the owner of an account cannot";
                    msg += " modify it.\nUser accounts can only be modified by the";
                    msg += " administrator.";
                    throw new EveNoPermissionException( msg );
                }

                if ( dn.size() > 2 && dn.startsWith( GROUP_BASE_DN ) )
                {
                    String msg = "User " + principalDn;
                    msg += " does not have permission to modify the group entry ";
                    msg += dn + ".\nGroups can only be modified by the admin.";
                    throw new EveNoPermissionException( msg );
                }
            }
        }
    }


    // ------------------------------------------------------------------------
    // DN altering operations are a no no for any user entry.  Basically here
    // are the rules of conduct to follow:
    //
    //  o No user should have the ability to move or rename their entry
    //  o Only the administrator can move or rename non-admin user entries
    //  o The administrator entry cannot be moved or renamed by anyone
    // ------------------------------------------------------------------------


    protected void modifyRdn( Name dn, String newRdn, boolean deleteOldRdn ) throws NamingException
    {
        protectDnAlterations( dn );
    }


    protected void move( Name oriChildName, Name newParentName ) throws NamingException
    {
        protectDnAlterations( oriChildName );
    }


    protected void move( Name oriChildName, Name newParentName, String newRdn,
                         boolean deleteOldRdn ) throws NamingException
    {
        protectDnAlterations( oriChildName );
    }


    private void protectDnAlterations( Name dn ) throws EveNoPermissionException
    {
        Invocation invocation = getInvocation();

        if ( invocation.getState() == InvocationStateEnum.PREINVOCATION )
        {
            Name principalDn = getPrincipal( invocation ).getDn();

            if ( dn == ADMIN_DN || dn.equals( ADMIN_DN ) )
            {
                String msg = "User '" + principalDn;
                msg += "' does not have permission to move or rename the admin";
                msg += " account.  No one not even the admin can move or";
                msg += " rename " + dn + "!";
                throw new EveNoPermissionException( msg );
            }

            if ( dn.size() > 2 && dn.startsWith( USER_BASE_DN ) && ! principalDn.equals( ADMIN_DN ) )
            {
                String msg = "User '" + principalDn;
                msg += "' does not have permission to move or rename the user";
                msg += " account: " + dn + ". Only the admin can move or";
                msg += " rename user accounts.";
                throw new EveNoPermissionException( msg );
            }

            if ( dn.size() > 2 && dn.startsWith( GROUP_BASE_DN ) && ! principalDn.equals( ADMIN_DN ) )
            {
                String msg = "User " + principalDn;
                msg += " does not have permission to move or rename the group entry ";
                msg += dn + ".\nGroups can only be moved or renamed by the admin.";
                throw new EveNoPermissionException( msg );
            }
        }
    }
    
    
    private class AuthorizationFilter implements SearchResultFilter, LookupFilter
    {
        public boolean accept( LdapContext ctx, DbSearchResult result, SearchControls controls )
                throws NamingException
        {
            Name dn;

            synchronized( dnParser )
            {
                dn = dnParser.parse( result.getName() );
            }

            Name principalDn = ( ( EveContext ) ctx ).getPrincipal().getDn();
            if ( ! principalDn.equals( ADMIN_DN ) )
            {
                if ( dn.size() > 2  )
                {
                    if ( dn.startsWith( USER_BASE_DN ) || dn.startsWith( GROUP_BASE_DN ) )
                    {
                        return false;
                    }
                }

                if ( dn.equals( ADMIN_DN ) )
                {
                    return false;
                }

            }

            return true;
        }


        public void filter( LdapContext ctx, Name dn, Attributes entry ) throws NamingException
        {
            filter( ctx, dn );
        }


        public void filter( LdapContext ctx, Name dn, Attributes entry, String[] ids )
                throws NamingException
        {
            filter( ctx, dn );
        }


        private void filter( LdapContext ctx, Name dn ) throws NamingException
        {
            Name principalDn = ( ( EveContext ) ctx ).getPrincipal().getDn();


            if ( ! principalDn.equals( ADMIN_DN ) )
            {
                if ( dn.size() > 2 && dn.startsWith( USER_BASE_DN ) )
                {
                    String msg = "Access to user account '" + dn + "' not permitted";
                    msg += " for user '" + principalDn + "'.  Only the admin can";
                    msg += " access user account information";
                    throw new EveNoPermissionException( msg );
                }

                if ( dn.size() > 2 && dn.startsWith( GROUP_BASE_DN ) )
                {
                    String msg = "Access to group '" + dn + "' not permitted";
                    msg += " for user '" + principalDn + "'.  Only the admin can";
                    msg += " access group information";
                    throw new EveNoPermissionException( msg );
                }

                if ( dn.equals( ADMIN_DN ) )
                {
                    String msg = "Access to admin account not permitted for user '";
                    msg += principalDn + "'.  Only the admin can";
                    msg += " access admin account information";
                    throw new EveNoPermissionException( msg );
                }
            }
        }
    }
}
