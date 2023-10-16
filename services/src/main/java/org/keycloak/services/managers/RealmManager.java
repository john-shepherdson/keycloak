/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.services.managers;

import org.keycloak.Config;
import org.keycloak.common.Profile;
import org.keycloak.common.enums.SslRequired;
import org.keycloak.models.AccountRoles;
import org.keycloak.models.AdminRoles;
import org.keycloak.models.BrowserSecurityHeaders;
import org.keycloak.models.ClientModel;
import org.keycloak.models.Constants;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.ImpersonationConstants;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.OTPPolicy;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RealmProvider;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserSessionProvider;
import org.keycloak.models.utils.DefaultAuthenticationFlows;
import org.keycloak.models.utils.DefaultClientScopes;
import org.keycloak.models.utils.DefaultKeyProviders;
import org.keycloak.models.utils.DefaultRequiredActions;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.models.utils.RepresentationToModel;
import org.keycloak.protocol.ProtocolMapperUtils;
import org.keycloak.protocol.oidc.OIDCConfigAttributes;
import org.keycloak.protocol.oidc.OIDCLoginProtocol;
import org.keycloak.protocol.oidc.OIDCLoginProtocolFactory;
import org.keycloak.protocol.oidc.OIDCWellKnownProvider;
import org.keycloak.protocol.oidc.mappers.AudienceResolveProtocolMapper;
import org.keycloak.protocol.saml.ConfigureAutoUpdateSAMLClient;
import org.keycloak.protocol.saml.SamlConfigAttributes;
import org.keycloak.representations.idm.ApplicationRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.ClientScopeRepresentation;
import org.keycloak.representations.idm.OAuthClientRepresentation;
import org.keycloak.representations.idm.RealmEventsConfigRepresentation;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.services.scheduled.AutoUpdateIdentityProviders;
import org.keycloak.services.scheduled.ClusterAwareScheduledTaskRunner;
import org.keycloak.sessions.AuthenticationSessionProvider;
import org.keycloak.storage.LegacyStoreMigrateRepresentationEvent;
import org.keycloak.storage.LegacyStoreSyncEvent;
import org.keycloak.services.clientregistration.policy.DefaultClientRegistrationPolicies;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import org.keycloak.timer.TimerProvider;
import org.keycloak.utils.ReservedCharValidator;

/**
 * Per request object
 *
 * @author <a href="mailto:bill@burkecentral.com">Bill Burke</a>
 * @version $Revision: 1 $
 */
public class RealmManager {

    protected KeycloakSession session;
    protected RealmProvider model;

    public RealmManager(KeycloakSession session) {
        this.session = session;
        this.model = session.realms();
    }

    public KeycloakSession getSession() {
        return session;
    }

    public RealmModel getKeycloakAdminstrationRealm() {
        return getRealmByName(Config.getAdminRealm());
    }

    public static boolean isAdministrationRealm(RealmModel realm) {
        return realm.getName().equals(Config.getAdminRealm());
    }

    public RealmModel getRealm(String id) {
        return model.getRealm(id);
    }

    public RealmModel getRealmByName(String name) {
        return model.getRealmByName(name);
    }

    public RealmModel createRealm(String name) {
        return createRealm(null, name);
    }

    public RealmModel createRealm(String id, String name) {
        if (id == null || id.trim().isEmpty()) {
            id = KeycloakModelUtils.generateId();
        }
        else {
            ReservedCharValidator.validate(id);
        }
        ReservedCharValidator.validate(name);
        RealmModel realm = model.createRealm(id, name);
        realm.setName(name);

        // setup defaults
        setupRealmDefaults(realm);

        KeycloakModelUtils.setupDefaultRole(realm, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + name.toLowerCase());
        setupMasterAdminManagement(realm);
        setupRealmAdminManagement(realm);
        setupAccountManagement(realm);
        setupBrokerService(realm);
        setupAdminConsole(realm);
        setupAdminConsoleLocaleMapper(realm);
        setupAdminCli(realm);
        setupImpersonationService(realm);
        setupAuthenticationFlows(realm);
        setupRequiredActions(realm);
        setupOfflineTokens(realm, null);
        createDefaultClientScopes(realm);
        setupAuthorizationServices(realm);
        setupClientRegistrations(realm);
        session.clientPolicy().setupClientPoliciesOnCreatedRealm(realm);

        fireRealmPostCreate(realm);

        return realm;
    }

    protected void setupAuthenticationFlows(RealmModel realm) {
        if (realm.getAuthenticationFlowsStream().count() == 0) DefaultAuthenticationFlows.addFlows(realm);
    }

    protected void setupRequiredActions(RealmModel realm) {
        if (realm.getRequiredActionProvidersStream().count() == 0) DefaultRequiredActions.addActions(realm);
    }

    private void setupOfflineTokens(RealmModel realm, RealmRepresentation realmRep) {
        RoleModel offlineRole = KeycloakModelUtils.setupOfflineRole(realm);

        if (realmRep != null && hasRealmRole(realmRep, Constants.OFFLINE_ACCESS_ROLE)) {
            // Case when realmRep had the offline_access role, but not the offline_access client scope. Need to manually remove the role
            List<RoleRepresentation> realmRoles = realmRep.getRoles().getRealm();
            for (RoleRepresentation role : realmRoles) {
                if (Constants.OFFLINE_ACCESS_ROLE.equals(role.getName())) {
                    realmRoles.remove(role);
                    break;
                }
            }
        }

        if (realmRep == null || !hasClientScope(realmRep, Constants.OFFLINE_ACCESS_ROLE)) {
            DefaultClientScopes.createOfflineAccessClientScope(realm, offlineRole);
        }
    }

    protected void createDefaultClientScopes(RealmModel realm) {
        DefaultClientScopes.createDefaultClientScopes(session, realm, true);
    }

    protected void setupAdminConsole(RealmModel realm) {
        ClientModel adminConsole = realm.getClientByClientId(Constants.ADMIN_CONSOLE_CLIENT_ID);
        if (adminConsole == null) adminConsole = KeycloakModelUtils.createPublicClient(realm, Constants.ADMIN_CONSOLE_CLIENT_ID);
        adminConsole.setName("${client_" + Constants.ADMIN_CONSOLE_CLIENT_ID + "}");

        adminConsole.setRootUrl(Constants.AUTH_ADMIN_URL_PROP);
        String baseUrl = "/admin/" + realm.getName() + "/console/";
        adminConsole.setBaseUrl(baseUrl);
        adminConsole.addRedirectUri(baseUrl + "*");
        adminConsole.setAttribute(OIDCConfigAttributes.POST_LOGOUT_REDIRECT_URIS, "+");
        adminConsole.setWebOrigins(Collections.singleton("+"));

        adminConsole.setEnabled(true);
        adminConsole.setAlwaysDisplayInConsole(false);
        adminConsole.setPublicClient(true);
        adminConsole.setFullScopeAllowed(false);
        adminConsole.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);

        adminConsole.setAttribute(OIDCConfigAttributes.PKCE_CODE_CHALLENGE_METHOD, "S256");
    }

    protected void setupAdminConsoleLocaleMapper(RealmModel realm) {
        ClientModel adminConsole = session.clients().getClientByClientId(realm, Constants.ADMIN_CONSOLE_CLIENT_ID);
        ProtocolMapperModel localeMapper = adminConsole.getProtocolMapperByName(OIDCLoginProtocol.LOGIN_PROTOCOL, OIDCLoginProtocolFactory.LOCALE);

        if (localeMapper == null) {
            localeMapper = ProtocolMapperUtils.findLocaleMapper(session);
            if (localeMapper != null) {
                adminConsole.addProtocolMapper(localeMapper);
            }
        }
    }

    public void setupAdminCli(RealmModel realm) {
        ClientModel adminCli = realm.getClientByClientId(Constants.ADMIN_CLI_CLIENT_ID);
        if (adminCli == null) {
            adminCli = KeycloakModelUtils.createPublicClient(realm, Constants.ADMIN_CLI_CLIENT_ID);
            adminCli.setName("${client_" + Constants.ADMIN_CLI_CLIENT_ID + "}");
            adminCli.setEnabled(true);
            adminCli.setAlwaysDisplayInConsole(false);
            adminCli.setFullScopeAllowed(false);
            adminCli.setStandardFlowEnabled(false);
            adminCli.setDirectAccessGrantsEnabled(true);
            adminCli.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
        }

    }
    public void addQueryCompositeRoles(ClientModel realmAccess) {
        RoleModel queryClients = realmAccess.getRole(AdminRoles.QUERY_CLIENTS);
        RoleModel queryUsers = realmAccess.getRole(AdminRoles.QUERY_USERS);
        RoleModel queryGroups = realmAccess.getRole(AdminRoles.QUERY_GROUPS);

        RoleModel viewClients = realmAccess.getRole(AdminRoles.VIEW_CLIENTS);
        viewClients.addCompositeRole(queryClients);
        RoleModel viewUsers = realmAccess.getRole(AdminRoles.VIEW_USERS);
        viewUsers.addCompositeRole(queryUsers);
        viewUsers.addCompositeRole(queryGroups);
    }


    public String getRealmAdminClientId(RealmModel realm) {
        return Constants.REALM_MANAGEMENT_CLIENT_ID;
    }

    public String getRealmAdminClientId(RealmRepresentation realm) {
        return Constants.REALM_MANAGEMENT_CLIENT_ID;
    }



    protected void setupRealmDefaults(RealmModel realm) {
        realm.setBrowserSecurityHeaders(BrowserSecurityHeaders.realmDefaultHeaders);

        // brute force
        realm.setBruteForceProtected(false); // default settings off for now todo set it on
        realm.setPermanentLockout(false);
        realm.setMaxFailureWaitSeconds(900);
        realm.setMinimumQuickLoginWaitSeconds(60);
        realm.setWaitIncrementSeconds(60);
        realm.setQuickLoginCheckMilliSeconds(1000);
        realm.setMaxDeltaTimeSeconds(60 * 60 * 12); // 12 hours
        realm.setFailureFactor(30);
        realm.setSslRequired(SslRequired.EXTERNAL);
        realm.setOTPPolicy(OTPPolicy.DEFAULT_POLICY);
        realm.setLoginWithEmailAllowed(true);
        realm.setClaimsSupported(OIDCWellKnownProvider.DEFAULT_CLAIMS_SUPPORTED);

        realm.setEventsListeners(Collections.singleton("jboss-logging"));
    }

    public boolean removeRealm(RealmModel realm) {

        ClientModel masterAdminClient = realm.getMasterAdminClient();
        boolean removed = model.removeRealm(realm.getId());
        if (removed) {
            if (masterAdminClient != null) {
                session.clients().removeClient(getKeycloakAdminstrationRealm(), masterAdminClient.getId());
            }

            UserSessionProvider sessions = session.sessions();
            if (sessions != null) {
                sessions.onRealmRemoved(realm);
            }

            AuthenticationSessionProvider authSessions = session.authenticationSessions();
            if (authSessions != null) {
                authSessions.onRealmRemoved(realm);
            }

          // Refresh periodic sync tasks for configured storageProviders
          LegacyStoreSyncEvent.fire(session, realm, true);
        }
        return removed;
    }

    public void updateRealmEventsConfig(RealmEventsConfigRepresentation rep, RealmModel realm) {
        realm.setEventsEnabled(rep.isEventsEnabled());
        realm.setEventsExpiration(rep.getEventsExpiration() != null ? rep.getEventsExpiration() : 0);
        if (rep.getEventsListeners() != null) {
            realm.setEventsListeners(new HashSet<>(rep.getEventsListeners()));
        }
        if(rep.getEnabledEventTypes() != null) {
            realm.setEnabledEventTypes(new HashSet<>(rep.getEnabledEventTypes()));
        }
        if(rep.isAdminEventsEnabled() != null) {
            realm.setAdminEventsEnabled(rep.isAdminEventsEnabled());
        }
        if(rep.isAdminEventsDetailsEnabled() != null){
            realm.setAdminEventsDetailsEnabled(rep.isAdminEventsDetailsEnabled());
        }
    }


    public void setupMasterAdminManagement(RealmModel realm) {
        // Need to refresh masterApp for current realm
        String adminRealmName = Config.getAdminRealm();
        RealmModel adminRealm = model.getRealmByName(adminRealmName);
        ClientModel masterApp = adminRealm.getClientByClientId(KeycloakModelUtils.getMasterRealmAdminApplicationClientId(realm.getName()));
        if (masterApp == null) {
            createMasterAdminManagement(realm);
            return;
        }
        realm.setMasterAdminClient(masterApp);
    }

    private void createMasterAdminManagement(RealmModel realm) {
        RealmModel adminRealm;
        RoleModel adminRole;

        if (realm.getName().equals(Config.getAdminRealm())) {
            adminRealm = realm;

            adminRole = realm.addRole(AdminRoles.ADMIN);

            RoleModel createRealmRole = realm.addRole(AdminRoles.CREATE_REALM);
            adminRole.addCompositeRole(createRealmRole);
            createRealmRole.setDescription("${role_" + AdminRoles.CREATE_REALM + "}");
        } else {
            adminRealm = model.getRealmByName(Config.getAdminRealm());
            adminRole = adminRealm.getRole(AdminRoles.ADMIN);
        }
        adminRole.setDescription("${role_"+AdminRoles.ADMIN+"}");

        ClientModel realmAdminApp = KeycloakModelUtils.createManagementClient(adminRealm, KeycloakModelUtils.getMasterRealmAdminApplicationClientId(realm.getName()));
        // No localized name for now
        realmAdminApp.setName(realm.getName() + " Realm");
        realm.setMasterAdminClient(realmAdminApp);

        for (String r : AdminRoles.ALL_REALM_ROLES) {
            RoleModel role = realmAdminApp.addRole(r);
            role.setDescription("${role_"+r+"}");
            adminRole.addCompositeRole(role);
        }
        addQueryCompositeRoles(realmAdminApp);
    }

    private void checkMasterAdminManagementRoles(RealmModel realm) {
        RealmModel adminRealm = model.getRealmByName(Config.getAdminRealm());
        RoleModel adminRole = adminRealm.getRole(AdminRoles.ADMIN);

        ClientModel masterAdminClient = realm.getMasterAdminClient();
        for (String r : AdminRoles.ALL_REALM_ROLES) {
            RoleModel found = masterAdminClient.getRole(r);
            if (found == null) {
                addAndSetAdminRole(r, masterAdminClient, adminRole);
            }
        }
        addQueryCompositeRoles(masterAdminClient);
    }


    private void setupRealmAdminManagement(RealmModel realm) {
        if (realm.getName().equals(Config.getAdminRealm())) { return; } // don't need to do this for master realm

        String realmAdminClientId = getRealmAdminClientId(realm);
        ClientModel realmAdminClient = realm.getClientByClientId(realmAdminClientId);
        if (realmAdminClient == null) {
            realmAdminClient = KeycloakModelUtils.createManagementClient(realm, realmAdminClientId);
            realmAdminClient.setName("${client_" + realmAdminClientId + "}");
        }
        RoleModel adminRole = realmAdminClient.addRole(AdminRoles.REALM_ADMIN);
        adminRole.setDescription("${role_" + AdminRoles.REALM_ADMIN + "}");
        realmAdminClient.setBearerOnly(true);
        realmAdminClient.setFullScopeAllowed(false);
        realmAdminClient.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);

        for (String r : AdminRoles.ALL_REALM_ROLES) {
            addAndSetAdminRole(r, realmAdminClient, adminRole);
        }
        addQueryCompositeRoles(realmAdminClient);
    }

    private void addAndSetAdminRole(String roleName, ClientModel parentClient, RoleModel parentRole) {
        RoleModel role = parentClient.addRole(roleName);
        role.setDescription("${role_" + roleName + "}");
        parentRole.addCompositeRole(role);
    }


    private void checkRealmAdminManagementRoles(RealmModel realm) {
        if (realm.getName().equals(Config.getAdminRealm())) { return; } // don't need to do this for master realm

        String realmAdminClientId = getRealmAdminClientId(realm);
        ClientModel realmAdminClient = realm.getClientByClientId(realmAdminClientId);
        RoleModel adminRole = realmAdminClient.getRole(AdminRoles.REALM_ADMIN);

        // if realm-admin role isn't in the realm model, create it
        if (adminRole == null) {
            adminRole = realmAdminClient.addRole(AdminRoles.REALM_ADMIN);
            adminRole.setDescription("${role_" + AdminRoles.REALM_ADMIN + "}");
        }

        for (String r : AdminRoles.ALL_REALM_ROLES) {
            RoleModel found = realmAdminClient.getRole(r);
            if (found == null) {
                addAndSetAdminRole(r, realmAdminClient, adminRole);
            }
        }
        addQueryCompositeRoles(realmAdminClient);
    }


    private void setupAccountManagement(RealmModel realm) {
        ClientModel accountClient = realm.getClientByClientId(Constants.ACCOUNT_MANAGEMENT_CLIENT_ID);
        if (accountClient == null) {
            accountClient = KeycloakModelUtils.createPublicClient(realm, Constants.ACCOUNT_MANAGEMENT_CLIENT_ID);
            accountClient.setName("${client_" + Constants.ACCOUNT_MANAGEMENT_CLIENT_ID + "}");
            accountClient.setEnabled(true);
            accountClient.setAlwaysDisplayInConsole(false);
            accountClient.setFullScopeAllowed(false);

            accountClient.setRootUrl(Constants.AUTH_BASE_URL_PROP);
            String baseUrl = "/realms/" + realm.getName() + "/account/";
            accountClient.setBaseUrl(baseUrl);
            accountClient.addRedirectUri(baseUrl + "*");
            accountClient.setAttribute(OIDCConfigAttributes.POST_LOGOUT_REDIRECT_URIS, "+");

            accountClient.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);

            for (String role : AccountRoles.DEFAULT) {
                RoleModel roleModel = accountClient.addRole(role);
                roleModel.setDescription("${role_" + role + "}");
                realm.addToDefaultRoles(roleModel);
            }
            RoleModel manageAccountLinks = accountClient.addRole(AccountRoles.MANAGE_ACCOUNT_LINKS);
            manageAccountLinks.setDescription("${role_" + AccountRoles.MANAGE_ACCOUNT_LINKS + "}");
            RoleModel manageAccount = accountClient.getRole(AccountRoles.MANAGE_ACCOUNT);
            manageAccount.addCompositeRole(manageAccountLinks);
            RoleModel viewAppRole = accountClient.addRole(AccountRoles.VIEW_APPLICATIONS);
            viewAppRole.setDescription("${role_" + AccountRoles.VIEW_APPLICATIONS + "}");
            RoleModel viewConsentRole = accountClient.addRole(AccountRoles.VIEW_CONSENT);
            viewConsentRole.setDescription("${role_" + AccountRoles.VIEW_CONSENT + "}");
            RoleModel manageConsentRole = accountClient.addRole(AccountRoles.MANAGE_CONSENT);
            manageConsentRole.setDescription("${role_" + AccountRoles.MANAGE_CONSENT + "}");
            manageConsentRole.addCompositeRole(viewConsentRole);
            RoleModel manageAccountBasicAuth = accountClient.addRole(AccountRoles.MANAGE_ACCOUNT_BASIC_AUTH);
            manageAccountBasicAuth.setDescription("${role_" + AccountRoles.MANAGE_ACCOUNT_BASIC_AUTH + "}");
            manageAccount.addCompositeRole(manageAccountBasicAuth);
            RoleModel manageAccount2fa = accountClient.addRole(AccountRoles.MANAGE_ACCOUNT_2FA);
            manageAccount2fa.setDescription("${role_" + AccountRoles.MANAGE_ACCOUNT_2FA + "}");
            manageAccount.addCompositeRole(manageAccount2fa);
            RoleModel viewGroups = accountClient.addRole(AccountRoles.VIEW_GROUPS);
            viewGroups.setDescription("${role_" + AccountRoles.VIEW_GROUPS + "}");


            KeycloakModelUtils.setupDeleteAccount(accountClient);

            ClientModel accountConsoleClient = realm.getClientByClientId(Constants.ACCOUNT_CONSOLE_CLIENT_ID);
            if (accountConsoleClient == null) {
                accountConsoleClient = KeycloakModelUtils.createPublicClient(realm, Constants.ACCOUNT_CONSOLE_CLIENT_ID);
                accountConsoleClient.setName("${client_" + Constants.ACCOUNT_CONSOLE_CLIENT_ID + "}");
                accountConsoleClient.setEnabled(true);
                accountConsoleClient.setAlwaysDisplayInConsole(false);
                accountConsoleClient.setFullScopeAllowed(false);
                accountConsoleClient.setDirectAccessGrantsEnabled(false);

                accountConsoleClient.setRootUrl(Constants.AUTH_BASE_URL_PROP);
                accountConsoleClient.setBaseUrl(baseUrl);
                accountConsoleClient.addRedirectUri(baseUrl + "*");
                accountConsoleClient.setAttribute(OIDCConfigAttributes.POST_LOGOUT_REDIRECT_URIS, "+");

                accountConsoleClient.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);

                accountConsoleClient.addScopeMapping(accountClient.getRole(AccountRoles.MANAGE_ACCOUNT));
                accountConsoleClient.addScopeMapping(accountClient.getRole(AccountRoles.VIEW_GROUPS));

                ProtocolMapperModel audienceMapper = new ProtocolMapperModel();
                audienceMapper.setName(OIDCLoginProtocolFactory.AUDIENCE_RESOLVE);
                audienceMapper.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);
                audienceMapper.setProtocolMapper(AudienceResolveProtocolMapper.PROVIDER_ID);

                accountConsoleClient.addProtocolMapper(audienceMapper);

                accountConsoleClient.setAttribute(OIDCConfigAttributes.PKCE_CODE_CHALLENGE_METHOD, "S256");
            }
        }
    }

    public void setupImpersonationService(RealmModel realm) {
        ImpersonationConstants.setupImpersonationService(session, realm);
    }

    public void setupBrokerService(RealmModel realm) {
        ClientModel client = realm.getClientByClientId(Constants.BROKER_SERVICE_CLIENT_ID);
        if (client == null) {
            client = KeycloakModelUtils.createManagementClient(realm, Constants.BROKER_SERVICE_CLIENT_ID);
            client.setEnabled(true);
            client.setAlwaysDisplayInConsole(false);
            client.setName("${client_" + Constants.BROKER_SERVICE_CLIENT_ID + "}");
            client.setFullScopeAllowed(false);
            client.setProtocol(OIDCLoginProtocol.LOGIN_PROTOCOL);

            for (String role : Constants.BROKER_SERVICE_ROLES) {
                RoleModel roleModel = client.addRole(role);
                roleModel.setDescription("${role_"+ role.toLowerCase().replaceAll("_", "-") +"}");
            }
        }
    }

    public RealmModel importRealm(RealmRepresentation rep) {
        return importRealm(rep, false);
    }


    /**
     * if "skipUserDependent" is true, then import of any models, which needs users already imported in DB, will be skipped. For example authorization
     */
    public RealmModel importRealm(RealmRepresentation rep, boolean skipUserDependent) {
        String id = rep.getId();
        if (id == null || id.trim().isEmpty()) {
            id = KeycloakModelUtils.generateId();
        } else {
            ReservedCharValidator.validate(id);
        }

        RealmModel realm = model.createRealm(id, rep.getRealm());
        RealmModel currentRealm = session.getContext().getRealm();

        try {
            session.getContext().setRealm(realm);
            ReservedCharValidator.validate(rep.getRealm());
            realm.setName(rep.getRealm());

            // setup defaults

            setupRealmDefaults(realm);

            if (rep.getDefaultRole() == null) {
                KeycloakModelUtils.setupDefaultRole(realm, determineDefaultRoleName(rep));
            } else {
                realm.setDefaultRole(RepresentationToModel.createRole(realm, rep.getDefaultRole()));
            }

            boolean postponeMasterClientSetup = postponeMasterClientSetup(rep);
            if (!postponeMasterClientSetup) {
                setupMasterAdminManagement(realm);
            }

            if (!hasRealmAdminManagementClient(rep)) setupRealmAdminManagement(realm);
            if (!hasAccountManagementClient(rep)) setupAccountManagement(realm);

            boolean postponeImpersonationSetup = hasRealmAdminManagementClient(rep);
            if (!postponeImpersonationSetup) {
                setupImpersonationService(realm);
            }

            if (!hasBrokerClient(rep)) setupBrokerService(realm);
            if (!hasAdminConsoleClient(rep)) setupAdminConsole(realm);

            boolean postponeAdminCliSetup = false;
            if (!hasAdminCliClient(rep)) {
                postponeAdminCliSetup = hasRealmAdminManagementClient(rep);

                if(!postponeAdminCliSetup) {
                    setupAdminCli(realm);
                }
            }

            if (!hasRealmRole(rep, Constants.OFFLINE_ACCESS_ROLE) || !hasClientScope(rep, Constants.OFFLINE_ACCESS_ROLE)) {
                setupOfflineTokens(realm, rep);
            }


            if (rep.getClientScopes() == null) {
                createDefaultClientScopes(realm);
            }

            RepresentationToModel.importRealm(session, rep, realm, skipUserDependent);

        if (rep.getIdentityProviders() != null) {
            rep.getIdentityProviders().stream().filter(idp -> idp.getConfig().get(IdentityProviderModel.REFRESH_PERIOD) != null).forEach(idp->{
                //case IdP is autoUpdated create its schedule task
                TimerProvider timer = session.getProvider(TimerProvider.class);
                AutoUpdateIdentityProviders autoUpdateProvider = new AutoUpdateIdentityProviders(idp.getAlias(), realm.getId());
                ClusterAwareScheduledTaskRunner taskRunner = new ClusterAwareScheduledTaskRunner(session.getKeycloakSessionFactory(), autoUpdateProvider, Long.parseLong(idp.getConfig().get(IdentityProviderModel.REFRESH_PERIOD)) * 1000);
                timer.schedule(taskRunner, Long.parseLong(idp.getConfig().get(IdentityProviderModel.REFRESH_PERIOD)) * 1000, realm.getId()+"_AutoUpdateIdP_" + idp.getAlias());
            });
        }
        if (rep.getClients()!= null){
            //saml autoupdated schedule task
            ConfigureAutoUpdateSAMLClient conf = session.getProvider(ConfigureAutoUpdateSAMLClient.class);
            rep.getClients().stream().filter(clientRep ->"saml".equals(clientRep.getProtocol()) && clientRep.getAttributes() != null && Boolean.valueOf(clientRep.getAttributes().get(SamlConfigAttributes.SAML_AUTO_UPDATED))).forEach(clientRep -> conf.configure(realm.getClientById(clientRep.getId()), realm));
        }

        setupClientServiceAccountsAndAuthorizationOnImport(rep, skipUserDependent);

            setupAdminConsoleLocaleMapper(realm);

            if (postponeMasterClientSetup) {
                setupMasterAdminManagement(realm);
            }

            if (rep.getRoles() != null || hasRealmAdminManagementClient(rep)) {
                // Assert all admin roles are available once import took place. This is needed due to import from previous version where JSON file may not contain all admin roles
                checkMasterAdminManagementRoles(realm);
                checkRealmAdminManagementRoles(realm);
            }

            // Could happen when migrating from older version and I have exported JSON file, which contains "realm-management" client but not "impersonation" client
            // I need to postpone impersonation because it needs "realm-management" client and its roles set
            if (postponeImpersonationSetup) {
                setupImpersonationService(realm);
            }

            if (postponeAdminCliSetup) {
                setupAdminCli(realm);
            }

            setupAuthenticationFlows(realm);
            setupRequiredActions(realm);

            if (!hasRealmRole(rep, AccountRoles.DELETE_ACCOUNT)) {
                KeycloakModelUtils.setupDeleteAccount(realm.getClientByClientId(Constants.ACCOUNT_MANAGEMENT_CLIENT_ID));
            }

            // Refresh periodic sync tasks for configured storageProviders
            LegacyStoreSyncEvent.fire(session, realm, false);

            setupAuthorizationServices(realm);
            setupClientRegistrations(realm);

            if (rep.getKeycloakVersion() != null) {
                LegacyStoreMigrateRepresentationEvent.fire(session, realm, rep, skipUserDependent);
            }

            session.clientPolicy().updateRealmModelFromRepresentation(realm, rep);

            fireRealmPostCreate(realm);
        } finally {
            session.getContext().setRealm(currentRealm);
        }

        return realm;
    }

    private String determineDefaultRoleName(RealmRepresentation rep) {
        String defaultRoleName = Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + rep.getRealm().toLowerCase(); 
        if (! hasRealmRole(rep, defaultRoleName)) {
            return defaultRoleName;
        } else {
            for (int i = 1; i < Integer.MAX_VALUE; i++) {
                defaultRoleName = Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + rep.getRealm().toLowerCase() + "-" + i;
                if (! hasRealmRole(rep, defaultRoleName)) {
                    return defaultRoleName;
                }
            }
        }
        return null;
    }

    private boolean postponeMasterClientSetup(RealmRepresentation rep) {
        if (!Config.getAdminRealm().equals(rep.getRealm())) {
            return false;
        }

        return hasRealmAdminManagementClient(rep);
    }

    private boolean hasRealmAdminManagementClient(RealmRepresentation rep) {
        String realmAdminClientId =  Config.getAdminRealm().equals(rep.getRealm()) ?  KeycloakModelUtils.getMasterRealmAdminApplicationClientId(rep.getRealm()) : getRealmAdminClientId(rep);
        return hasClient(rep, realmAdminClientId);
    }

    private boolean hasAccountManagementClient(RealmRepresentation rep) {
        return hasClient(rep, Constants.ACCOUNT_MANAGEMENT_CLIENT_ID);
    }

    private boolean hasBrokerClient(RealmRepresentation rep) {
        return hasClient(rep, Constants.BROKER_SERVICE_CLIENT_ID);
    }

    private boolean hasAdminConsoleClient(RealmRepresentation rep) {
        return hasClient(rep, Constants.ADMIN_CONSOLE_CLIENT_ID);
    }

    private boolean hasAdminCliClient(RealmRepresentation rep) {
        return hasClient(rep, Constants.ADMIN_CLI_CLIENT_ID);
    }

    private boolean hasClient(RealmRepresentation rep, String clientId) {
        if (rep.getClients() != null) {
            for (ClientRepresentation clientRep : rep.getClients()) {
                if (clientRep.getClientId() != null && clientRep.getClientId().equals(clientId)) {
                    return true;
                }
            }
        }

        // TODO: Just for compatibility with old versions. Should be removed later...
        if (rep.getApplications() != null) {
            for (ApplicationRepresentation clientRep : rep.getApplications()) {
                if (clientRep.getName().equals(clientId)) {
                    return true;
                }
            }
        }
        if (rep.getOauthClients() != null) {
            for (OAuthClientRepresentation clientRep : rep.getOauthClients()) {
                if (clientRep.getName().equals(clientId)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasRealmRole(RealmRepresentation rep, String roleName) {
        if (rep.getRoles() == null || rep.getRoles().getRealm() == null) {
            return false;
        }

        for (RoleRepresentation role : rep.getRoles().getRealm()) {
            if (roleName.equals(role.getName())) {
                return true;
            }
        }

        return false;
    }

    private boolean hasClientScope(RealmRepresentation rep, String clientScopeName) {
        if (rep.getClientScopes() == null) {
            return false;
        }

        for (ClientScopeRepresentation clientScope : rep.getClientScopes()) {
            if (clientScopeName.equals(clientScope.getName())) {
                return true;
            }
        }

        return false;
    }

    private void setupAuthorizationServices(RealmModel realm) {
        KeycloakModelUtils.setupAuthorizationServices(realm);
    }

    private void setupClientRegistrations(RealmModel realm) {
        DefaultClientRegistrationPolicies.addDefaultPolicies(realm);
    }

    private void fireRealmPostCreate(RealmModel realm) {
        session.getKeycloakSessionFactory().publish(new RealmModel.RealmPostCreateEvent() {
            @Override
            public RealmModel getCreatedRealm() {
                return realm;
            }
            @Override
            public KeycloakSession getKeycloakSession() {
                return session;
            }
        });

    }

    public void setupClientServiceAccountsAndAuthorizationOnImport(RealmRepresentation rep, boolean skipUserDependent) {
        List<ClientRepresentation> clients = rep.getClients();
        // do not initialize services accounts or authorization if skipUserDependent
        // they need the users and should be done at the end in dir import
        if (clients != null && !skipUserDependent) {
            ClientManager clientManager = new ClientManager(this);

            for (ClientRepresentation client : clients) {
                RealmModel realmModel = this.getRealmByName(rep.getRealm());

                ClientModel clientModel = Optional.ofNullable(client.getId())
                        .map(realmModel::getClientById)
                        .orElseGet(() -> realmModel.getClientByClientId(client.getClientId()));
                
                if (clientModel == null) {
                    throw new RuntimeException("Cannot find provided client by dir import.");
                }

                UserModel serviceAccount = null;
                if (clientModel.isServiceAccountsEnabled()) {
                    serviceAccount = this.getSession().users().getServiceAccount(clientModel);
                    if (serviceAccount == null) {
                        // initialize the service account if the account is missing
                        clientManager.enableServiceAccount(clientModel);
                    }
                }

                if (Profile.isFeatureEnabled(Profile.Feature.AUTHORIZATION) && Boolean.TRUE.equals(client.getAuthorizationServicesEnabled())) {
                    // just create the default roles if the service account was missing in the import
                    RepresentationToModel.createResourceServer(clientModel, session, serviceAccount == null);
                    RepresentationToModel.importAuthorizationSettings(client, clientModel, session);
                }
            }
        }
    }

    public void setDefaultsForNewRealm(RealmModel realm) {
        // setup defaults
        setupRealmDefaults(realm);
        KeycloakModelUtils.setupDefaultRole(realm, Constants.DEFAULT_ROLES_ROLE_PREFIX + "-" + realm.getName().toLowerCase());
        setupRealmAdminManagement(realm);
        setupAccountManagement(realm);
        setupBrokerService(realm);
        setupAdminConsole(realm);
        setupAdminConsoleLocaleMapper(realm);
        setupAdminCli(realm);
        setupAuthenticationFlows(realm);
        setupRequiredActions(realm);
        setupOfflineTokens(realm, null);
        createDefaultClientScopes(realm);
        setupAuthorizationServices(realm);
        setupClientRegistrations(realm);
        session.clientPolicy().setupClientPoliciesOnCreatedRealm(realm);
        DefaultKeyProviders.createProviders(realm);
    }
}
