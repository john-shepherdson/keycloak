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

import com.google.common.collect.Streams;
import jakarta.ws.rs.*;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.extensions.Extension;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.annotations.cache.NoCache;
import org.keycloak.broker.provider.IdentityProvider;
import org.keycloak.broker.provider.IdentityProviderFactory;
import org.keycloak.broker.social.SocialIdentityProvider;
import org.keycloak.connections.httpclient.HttpClientProvider;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.http.FormPartValue;
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
import org.keycloak.services.resources.KeycloakOpenAPI;
import org.keycloak.services.resources.admin.permissions.AdminPermissionEvaluator;
import org.keycloak.utils.ReservedCharValidator;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;

import org.keycloak.services.scheduled.AutoUpdateIdentityProviders;
import org.keycloak.services.scheduled.ClusterAwareScheduledTaskRunner;
import org.keycloak.timer.TimerProvider;
import org.keycloak.utils.ReservedCharValidator;

/**
 * @resource Identity Providers
 * @author Pedro Igor
 */
@Extension(name = KeycloakOpenAPI.Profiles.ADMIN, value = "")
public class IdentityProvidersResource {

    private final RealmModel realm;
    private final KeycloakSession session;
    private final AdminPermissionEvaluator auth;
    private final AdminEventBuilder adminEvent;

    public IdentityProvidersResource(RealmModel realm, KeycloakSession session, AdminPermissionEvaluator auth, AdminEventBuilder adminEvent) {
        this.realm = realm;
        this.session = session;
        this.auth = auth;
        this.adminEvent = adminEvent.resource(ResourceType.IDENTITY_PROVIDER);
    }

    /**
     * Get the identity provider factory for a provider id.
     *
     * @param providerId Provider id
     * @return
     */
    @Path("/providers/{provider_id}")
    @GET
    @NoCache
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = KeycloakOpenAPI.Admin.Tags.IDENTITY_PROVIDERS)
    @Operation( summary = "Get identity providers")
    public Response getIdentityProviders(@Parameter(description = "Provider id") @PathParam("provider_id") String providerId) {
        this.auth.realm().requireViewIdentityProviders();
        IdentityProviderFactory providerFactory = getProviderFactoryById(providerId);
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
    @Tag(name = KeycloakOpenAPI.Admin.Tags.IDENTITY_PROVIDERS)
    @Operation( description = "Import identity provider from uploaded JSON file")
    public Map<String, String> importFrom() throws IOException {
        this.auth.realm().requireManageIdentityProviders();
        MultivaluedMap<String, FormPartValue> formDataMap = session.getContext().getHttpRequest().getMultiPartFormParameters();
        if (!(formDataMap.containsKey("providerId") && formDataMap.containsKey("file"))) {
            throw new BadRequestException();
        }
        String providerId = formDataMap.getFirst("providerId").asString();
        InputStream inputStream = formDataMap.getFirst("file").asInputStream();
        IdentityProviderFactory providerFactory = getProviderFactoryById(providerId);
        Map<String, String> config = providerFactory.parseConfig(session, inputStream, new IdentityProviderModel()).getConfig();
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
    @Tag(name = KeycloakOpenAPI.Admin.Tags.IDENTITY_PROVIDERS)
    @Operation( summary = "Import identity provider from JSON body")
    public Map<String, String> importFrom(@Parameter(description = "JSON body") Map<String, Object> data) throws IOException {
        this.auth.realm().requireManageIdentityProviders();
        if (data == null || !(data.containsKey("providerId") && data.containsKey("fromUrl"))) {
            throw new BadRequestException();
        }
        
        ReservedCharValidator.validate((String)data.get("alias"));
        
        String providerId = data.get("providerId").toString();
        String from = data.get("fromUrl").toString();
        InputStream inputStream = session.getProvider(HttpClientProvider.class).get(from);
        try {
            IdentityProviderFactory providerFactory = getProviderFactoryById(providerId);
            Map<String, String> config = providerFactory.parseConfig(session, inputStream, new IdentityProviderModel()).getConfig();
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
    public Set<String> getUsedIdentityProviderIdTypes() {
        this.auth.realm().requireViewIdentityProviders();
        return realm.getIdentityProvidersStream().map(IdentityProviderModel::getProviderId).collect(Collectors.toCollection(HashSet::new));
    }


    /**
     * List identity providers.
     *
     * @param search Filter to search specific providers by name. Search can be prefixed (name*), contains (*name*) or exact (\"name\"). Default prefixed.
     * @param briefRepresentation Boolean which defines whether brief representations are returned (default: false)
     * @param firstResult Pagination offset
     * @param maxResults Maximum results size (defaults to 100)
     * @return The list of providers.
     */
    @GET
    @Path("instances")
    @NoCache
    @Produces(MediaType.APPLICATION_JSON)
    @Tag(name = KeycloakOpenAPI.Admin.Tags.IDENTITY_PROVIDERS)
    @Operation(summary = "List identity providers")
    public Stream<IdentityProviderRepresentation> getIdentityProviders(
            @Parameter(description = "Filter specific providers by name. Search can be prefix (name*), contains (*name*) or exact (\"name\"). Default prefixed.") @QueryParam("search") String search,
            @Parameter(description = "Boolean which defines whether brief representations are returned (default: false)") @DefaultValue ("true") @QueryParam("briefRepresentation") Boolean briefRepresentation,
            @Parameter(description = "Pagination offset") @DefaultValue ("0") @QueryParam("first") Integer firstResult,
            @Parameter(description = "Maximum results size (defaults to 100)") @DefaultValue ("100") @QueryParam("max") Integer maxResults) {
        this.auth.realm().requireViewIdentityProviders();

        Function<IdentityProviderModel, IdentityProviderRepresentation> toRepresentation = briefRepresentation != null && briefRepresentation
                ? m -> ModelToRepresentation.toBriefRepresentation(realm, m)
                : m -> StripSecretsUtils.strip(ModelToRepresentation.toRepresentation(realm, m));

        Stream<IdentityProviderModel> stream = realm.searchIdentityProviders(search, firstResult, maxResults);

        return stream.map(toRepresentation);
    }

     /**
     * get IdPs alias per federation
     * @param federationId
     * @return
     */
    @Path("/federation/{federationId}")
    @GET
    @NoCache
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getIdPsPerFederation(@PathParam("federationId") String federationId) {
        this.auth.realm().requireViewIdentityProviders();
       return realm.getIdentityProvidersByFederation(federationId);
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
    @Tag(name = KeycloakOpenAPI.Admin.Tags.IDENTITY_PROVIDERS)
    @Operation( summary = "Create a new identity provider")
    public Response create(@Parameter(description = "JSON body") IdentityProviderRepresentation representation) {
        this.auth.realm().requireManageIdentityProviders();

        ReservedCharValidator.validate(representation.getAlias());
        
        try {
            IdentityProviderModel identityProvider = RepresentationToModel.toModel(realm, representation, session);
            this.realm.addIdentityProvider(identityProvider);

            representation.setInternalId(identityProvider.getInternalId());
            //for autoupdated IdPs create schedule task
            if (identityProvider.getConfig().get(IdentityProviderModel.REFRESH_PERIOD) != null)
                createScheduleTask(identityProvider.getAlias(), Long.parseLong(identityProvider.getConfig().get(IdentityProviderModel.REFRESH_PERIOD)) * 1000);
            adminEvent.operation(OperationType.CREATE).resourcePath(session.getContext().getUri(), identityProvider.getAlias())
                    .representation(StripSecretsUtils.strip(representation)).success();
            
            return Response.created(session.getContext().getUri().getAbsolutePathBuilder().path(representation.getAlias()).build()).build();
        } catch (IllegalArgumentException e) {
            String message = e.getMessage();
            
            if (message == null) {
                message = "Invalid request";
            }
            
            throw ErrorResponse.error(message, BAD_REQUEST);
        } catch (ModelDuplicateException e) {
            throw ErrorResponse.exists("Identity Provider " + representation.getAlias() + " already exists");
        }
    }

    private void createScheduleTask(String alias,long interval) {
        TimerProvider timer = session.getProvider(TimerProvider.class);
        AutoUpdateIdentityProviders autoUpdateProvider = new AutoUpdateIdentityProviders(alias, realm.getId());
        ClusterAwareScheduledTaskRunner taskRunner = new ClusterAwareScheduledTaskRunner(session.getKeycloakSessionFactory(), autoUpdateProvider, interval);
        timer.schedule(taskRunner, interval, realm.getId()+"_AutoUpdateIdP_" + alias);
    }

    @Path("instances/{alias}")
    public IdentityProviderResource getIdentityProvider(@PathParam("alias") String alias) {
        this.auth.realm().requireViewIdentityProviders();
        IdentityProviderModel identityProviderModel =  this.realm.getIdentityProvidersStream()
                .filter(p -> Objects.equals(p.getAlias(), alias) || Objects.equals(p.getInternalId(), alias))
                .findFirst().orElse(null);

        return new IdentityProviderResource(this.auth, realm, session, identityProviderModel, adminEvent);
    }

    private IdentityProviderFactory getProviderFactoryById(String providerId) {
        return getProviderFactories()
                .filter(providerFactory -> Objects.equals(providerId, providerFactory.getId()))
                .map(IdentityProviderFactory.class::cast)
                .findFirst()
                .orElse(null);
    }

    private Stream<ProviderFactory> getProviderFactories() {
        return Streams.concat(session.getKeycloakSessionFactory().getProviderFactoriesStream(IdentityProvider.class),
                session.getKeycloakSessionFactory().getProviderFactoriesStream(SocialIdentityProvider.class));
    }
}
