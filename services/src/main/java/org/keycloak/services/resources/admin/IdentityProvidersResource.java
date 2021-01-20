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

package org.keycloak.services.resources.admin;

import org.jboss.resteasy.annotations.cache.NoCache;
import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.keycloak.broker.provider.IdentityProvider;
import org.keycloak.broker.provider.IdentityProviderFactory;
import org.keycloak.broker.social.SocialIdentityProvider;
import org.keycloak.connections.httpclient.HttpClientProvider;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ModelDuplicateException;
import org.keycloak.models.RealmModel;
import org.keycloak.models.utils.ModelToRepresentation;
import org.keycloak.models.utils.RepresentationToModel;
import org.keycloak.models.utils.StripSecretsUtils;
import org.keycloak.provider.ProviderFactory;
import org.keycloak.representations.idm.IdentityProviderRepresentation;
import org.keycloak.services.ErrorResponse;
import org.keycloak.services.resources.admin.permissions.AdminPermissionEvaluator;

import javax.ws.rs.BadRequestException;
import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import org.keycloak.utils.ReservedCharValidator;

/**
 * @resource Identity Providers
 * @author Pedro Igor
 */
public class IdentityProvidersResource {

    private final RealmModel realm;
    private final KeycloakSession session;
    private AdminPermissionEvaluator auth;
    private AdminEventBuilder adminEvent;

    public IdentityProvidersResource(RealmModel realm, KeycloakSession session, AdminPermissionEvaluator auth, AdminEventBuilder adminEvent) {
        this.realm = realm;
        this.session = session;
        this.auth = auth;
        this.adminEvent = adminEvent.resource(ResourceType.IDENTITY_PROVIDER);
    }

    /**
     * Get identity providers
     *
     * @param providerId Provider id
     * @return
     */
    @Path("/providers/{provider_id}")
    @GET
    @NoCache
    @Produces(MediaType.APPLICATION_JSON)
    public Response getIdentityProviders(@PathParam("provider_id") String providerId) {
        this.auth.realm().requireViewIdentityProviders();
        IdentityProviderFactory providerFactory = getProviderFactorytById(providerId);
        if (providerFactory != null) {
            return Response.ok(providerFactory).build();
        }
        return Response.status(BAD_REQUEST).build();
    }

    /**
     * Import identity provider from uploaded JSON file
     *
     * @param input
     * @return
     * @throws IOException
     */
    @POST
    @Path("import-config")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> importFrom(MultipartFormDataInput input) throws IOException {
        this.auth.realm().requireManageIdentityProviders();
        Map<String, List<InputPart>> formDataMap = input.getFormDataMap();
        if (!(formDataMap.containsKey("providerId") && formDataMap.containsKey("file"))) {
            throw new BadRequestException();
        }
        String providerId = formDataMap.get("providerId").get(0).getBodyAsString();
        InputPart file = formDataMap.get("file").get(0);
        InputStream inputStream = file.getBody(InputStream.class, null);
        IdentityProviderFactory providerFactory = getProviderFactorytById(providerId);
        Map<String, String> config = providerFactory.parseConfig(session, inputStream);
        return config;
    }

    /**
     * Import identity provider from JSON body
     *
     * @param data JSON body
     * @return
     * @throws IOException
     */
    @POST
    @Path("import-config")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, String> importFrom(Map<String, Object> data) throws IOException {
        this.auth.realm().requireManageIdentityProviders();
        if (!(data.containsKey("providerId") && data.containsKey("fromUrl"))) {
            throw new BadRequestException();
        }
        
        ReservedCharValidator.validate((String)data.get("alias"));
        
        String providerId = data.get("providerId").toString();
        String from = data.get("fromUrl").toString();
        InputStream inputStream = session.getProvider(HttpClientProvider.class).get(from);
        try {
            IdentityProviderFactory providerFactory = getProviderFactorytById(providerId);
            Map<String, String> config;
            config = providerFactory.parseConfig(session, inputStream);
            return config;
        } finally {
            try {
                inputStream.close();
            } catch (IOException e) {
            }
        }
    }
    
    
    /**
     * Get used (initiated) identity provider providerId(s).  i.e. ['saml', 'oidc', 'github'] 
     *
     * @return
     */
    @GET
    @Path("types-used")
    @NoCache
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getUsedIdentityProviderIdTypes() {
        this.auth.realm().requireViewIdentityProviders();
        return session.identityProviderStorage().getUsedIdentityProviderIdTypes(realm);
    }

    /**
     * Get identity providers
     *
     * @return
     */
    @GET
    @Path("instances")
    @NoCache
    @Produces(MediaType.APPLICATION_JSON)
    public List<IdentityProviderRepresentation> getIdentityProviders(
    		@QueryParam("brief") @DefaultValue("false") Boolean brief,
    		@QueryParam("keyword") @DefaultValue("") String keyword,
    		@QueryParam("first") @DefaultValue("-1") Integer firstResult,
            @QueryParam("max") @DefaultValue("-1") Integer maxResults
    		) {
    	
        this.auth.realm().requireViewIdentityProviders();

        List<IdentityProviderModel> identityProviders = null;
        if(keyword.isEmpty() && firstResult == -1 && maxResults == -1)
        	identityProviders = session.identityProviderStorage().getIdentityProviders(realm);
        else
        	identityProviders = session.identityProviderStorage().searchIdentityProviders(realm, keyword, firstResult, maxResults);
        
        if(brief)
        	return identityProviders.stream().map(idpModel -> StripSecretsUtils.strip(ModelToRepresentation.toBriefRepresentation(realm, idpModel))).collect(Collectors.toList());
        else
        	return identityProviders.stream().map(idpModel -> StripSecretsUtils.strip(ModelToRepresentation.toRepresentation(realm, idpModel))).collect(Collectors.toList());
        
    }

    

    
    /**
     * Create a new identity provider
     *
     * @param representation JSON body
     * @return
     */
    @POST
    @Path("instances")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(IdentityProviderRepresentation representation) {
        this.auth.realm().requireManageIdentityProviders();

        ReservedCharValidator.validate(representation.getAlias());
        
        try {
            IdentityProviderModel identityProvider = RepresentationToModel.toModel(realm, representation, session);
            this.session.identityProviderStorage().addIdentityProvider(realm, identityProvider);

            representation.setInternalId(identityProvider.getInternalId());
            adminEvent.operation(OperationType.CREATE).resourcePath(session.getContext().getUri(), identityProvider.getAlias())
                    .representation(StripSecretsUtils.strip(representation)).success();
            
            return Response.created(session.getContext().getUri().getAbsolutePathBuilder().path(representation.getAlias()).build()).build();
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            
            if (message == null) {
                message = "Invalid request";
            }
            
            return ErrorResponse.error(message, BAD_REQUEST);
        } catch (ModelDuplicateException e) {
            return ErrorResponse.exists("Identity Provider " + representation.getAlias() + " already exists");
        }
    }

    @Path("instances/{alias}")
    public IdentityProviderResource getIdentityProvider(@PathParam("alias") String alias) {
        this.auth.realm().requireViewIdentityProviders();

        IdentityProviderModel identityProviderModel = this.session.identityProviderStorage().getIdentityProviderByAlias(realm, alias);
        if(identityProviderModel==null)
        	identityProviderModel = this.session.identityProviderStorage().getIdentityProviderById(realm, alias);
        
        IdentityProviderResource identityProviderResource = new IdentityProviderResource(this.auth, realm, session, identityProviderModel, adminEvent);
        ResteasyProviderFactory.getInstance().injectProperties(identityProviderResource);
        
        return identityProviderResource;
    }

    private IdentityProviderFactory getProviderFactorytById(String providerId) {
        List<ProviderFactory> allProviders = getProviderFactories();

        for (ProviderFactory providerFactory : allProviders) {
            if (providerFactory.getId().equals(providerId)) {
                return (IdentityProviderFactory) providerFactory;
            }
        }

        return null;
    }

    private List<ProviderFactory> getProviderFactories() {
        List<ProviderFactory> allProviders = new ArrayList<ProviderFactory>();

        allProviders.addAll(this.session.getKeycloakSessionFactory().getProviderFactories(IdentityProvider.class));
        allProviders.addAll(this.session.getKeycloakSessionFactory().getProviderFactories(SocialIdentityProvider.class));

        return allProviders;
    }
}
