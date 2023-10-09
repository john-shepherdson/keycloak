/*
 *  Copyright 2016 Red Hat, Inc. and/or its affiliates
 *  and other contributors as indicated by the @author tags.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.keycloak.protocol.oidc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.ws.rs.NotFoundException;
import org.apache.commons.io.IOUtils;
import org.jboss.logging.Logger;
import org.keycloak.TokenVerifier;
import org.keycloak.broker.oidc.OIDCIdentityProvider;
import org.keycloak.broker.oidc.OIDCIdentityProviderConfig;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.common.VerificationException;
import org.keycloak.common.util.Time;
import org.keycloak.connections.httpclient.HttpClientProvider;
import org.keycloak.crypto.SignatureProvider;
import org.keycloak.crypto.SignatureVerifierContext;
import org.keycloak.models.*;
import org.keycloak.models.customcache.CustomCacheProvider;
import org.keycloak.models.customcache.CustomCacheProviderFactory;
import org.keycloak.protocol.oidc.representations.OIDCConfigurationRepresentation;
import org.keycloak.representations.AccessToken;
import org.keycloak.services.Urls;
import org.keycloak.util.JsonSerialization;
import org.keycloak.protocol.oidc.utils.Key;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Base64;

/**
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
public class AccessTokenIntrospectionProvider implements TokenIntrospectionProvider {

    private final KeycloakSession session;
    private final TokenManager tokenManager;
    private final RealmModel realm;
    private static final Logger logger = Logger.getLogger(AccessTokenIntrospectionProvider.class);
    private static final String wellKnown = "/.well-known/openid-configuration";
    private static final String PARAM_TOKEN = "token";

    private static CustomCacheProvider tokenRelayCache;

    public AccessTokenIntrospectionProvider(KeycloakSession session) {
        this.session = session;
        this.realm = session.getContext().getRealm();
        this.tokenManager = new TokenManager();
        initTokenCache();
    }

    private void initTokenCache(){
        if(tokenRelayCache != null)
            return;
        CustomCacheProviderFactory factory = (CustomCacheProviderFactory)session.getKeycloakSessionFactory().getProviderFactory(CustomCacheProvider.class, "token-relay-cache");
        if(factory == null)
            throw new NotFoundException("Could not initate TokenRelayCacheProvider. Was not found");
        tokenRelayCache = factory.create(session);
    }

    public Response introspect(String token) {
        try {
            String[] splitToken = token.split("\\.");
            String accessTokenStr = new String(Base64.getUrlDecoder().decode(splitToken[1]));
            JsonNode tokenJson = new ObjectMapper().readTree(accessTokenStr);
            String issuer = tokenJson.get("iss").asText();
            String realmUrl = Urls.realmIssuer(session.getContext().getUri().getBaseUri(), realm.getName());
            if (realmUrl.equals(issuer)) {
                return introspectKeycloak(token);
            } else {
                if (isExpired(tokenJson.get("exp").asLong())) {
                    ObjectNode tokenMetadata = JsonSerialization.createObjectNode();
                    tokenMetadata.put("active", false);
                    return Response.ok(JsonSerialization.writeValueAsBytes(tokenMetadata)).type(MediaType.APPLICATION_JSON_TYPE).build();
                }  else {
                    return introspectWithExternal(token, issuer, realm);
                }
            }

        } catch (Exception e) {
            ObjectNode tokenMetadata = JsonSerialization.createObjectNode();
            tokenMetadata.put("active", false);
            try {
                return Response.ok(JsonSerialization.writeValueAsBytes(tokenMetadata)).type(MediaType.APPLICATION_JSON_TYPE).build();
            } catch (IOException ioException) {
                throw new RuntimeException("Error creating token introspection response.", e);
            }
        }
    }

    protected Response introspectKeycloak (String token) {
        try {

            AccessToken accessToken = verifyAccessToken(token);
            ObjectNode tokenMetadata;

            if (accessToken != null) {
                tokenMetadata = JsonSerialization.createObjectNode(accessToken);
                tokenMetadata.put("client_id", accessToken.getIssuedFor());

                if (!tokenMetadata.has("username")) {
                    if (accessToken.getPreferredUsername() != null) {
                        tokenMetadata.put("username", accessToken.getPreferredUsername());
                    } else {
                        UserModel userModel = session.users().getUserById(realm, accessToken.getSubject());
                        if (userModel != null) {
                            tokenMetadata.put("username", userModel.getUsername());
                        }
                    }
                }

                String sessionState = accessToken.getSessionState();

                if (sessionState != null) {
                    UserSessionModel userSession = session.sessions().getUserSession(realm, sessionState);

                    if (userSession != null) {
                        String actor = userSession.getNote(ImpersonationSessionNote.IMPERSONATOR_USERNAME.toString());

                        if (actor != null) {
                            // for token exchange delegation semantics when an entity (actor) other than the subject is the acting party to whom authority has been delegated
                            tokenMetadata.putObject("act").put("sub", actor);
                        }
                    }
                }
            } else {
                tokenMetadata = JsonSerialization.createObjectNode();
            }

            tokenMetadata.put("active", accessToken != null);

            return Response.ok(JsonSerialization.writeValueAsBytes(tokenMetadata)).type(MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            throw new RuntimeException("Error creating token introspection response.", e);
        }
    }

    protected AccessToken verifyAccessToken(String token) {
        AccessToken accessToken;

        try {
            TokenVerifier<AccessToken> verifier = TokenVerifier.create(token, AccessToken.class)
                    .realmUrl(Urls.realmIssuer(session.getContext().getUri().getBaseUri(), realm.getName()));

            SignatureVerifierContext verifierContext = session.getProvider(SignatureProvider.class, verifier.getHeader().getAlgorithm().name()).verifier(verifier.getHeader().getKeyId());
            verifier.verifierContext(verifierContext);

            accessToken = verifier.verify().getToken();
        } catch (VerificationException e) {
            logger.debugf("JWT check failed: %s", e.getMessage());
            return null;
        }

        RealmModel realm = this.session.getContext().getRealm();

        return tokenManager.checkTokenValidForIntrospection(session, realm, accessToken, false) ? accessToken : null;
    }

    protected Response introspectWithExternal(String token, String issuer, RealmModel realm) throws IOException {

        try {
            String cachedToken = (String) tokenRelayCache.get(new Key(token, realm.getName()));
            if(cachedToken != null)
                return Response.ok(cachedToken).type(MediaType.APPLICATION_JSON_TYPE).build();

            IdentityProviderModel issuerIdp = realm.getIdentityProvidersStream().filter(idp -> issuer.equals(idp.getConfig().get("issuer"))).findAny().orElse(null);
            if (issuerIdp != null) {
                OIDCIdentityProviderConfig oidcIssuerIdp = new OIDCIdentityProviderConfig(issuerIdp);
                OIDCIdentityProvider oidcIssuerProvider = new OIDCIdentityProvider(session, oidcIssuerIdp);
                InputStream inputStream = session.getProvider(HttpClientProvider.class).get(new String(oidcIssuerIdp.getIssuer() + wellKnown));
                OIDCConfigurationRepresentation rep = JsonSerialization.readValue(inputStream, OIDCConfigurationRepresentation.class);
                if (rep.getIntrospectionEndpoint() != null) {
                    SimpleHttp.Response response = oidcIssuerProvider.authenticateTokenRequest(SimpleHttp.doPost(rep.getIntrospectionEndpoint(), session).param(PARAM_TOKEN, token)).asResponse();
                    if (response.getResponse().getStatusLine().getStatusCode() > 300) {
                        ObjectNode tokenMetadata = JsonSerialization.createObjectNode();
                        tokenMetadata.put("active", false);
                        return Response.ok(JsonSerialization.writeValueAsBytes(tokenMetadata)).type(MediaType.APPLICATION_JSON_TYPE).build();
                    }
                    String responseJson = IOUtils.toString(response.getResponse().getEntity().getContent(), Charset.defaultCharset());
                    tokenRelayCache.put(new Key(token, realm.getName()), responseJson);
                    return Response.status(response.getResponse().getStatusLine().getStatusCode()).type(MediaType.APPLICATION_JSON_TYPE).entity(responseJson).build();
                }
            }
            //if failed to find issuer in IdPs or IntrospectionEndpoint does not exist for specific Idp return false
            ObjectNode tokenMetadata = JsonSerialization.createObjectNode();
            tokenMetadata.put("active", false);
            return Response.ok(JsonSerialization.writeValueAsBytes(tokenMetadata)).type(MediaType.APPLICATION_JSON_TYPE).build();
        } catch (Exception e) {
            throw new RuntimeException("Error creating token introspection response.", e);
        }
    }

    private boolean isExpired(Long exp) {
        return exp != null && exp != 0 ? Time.currentTime() > exp : false;
    }

    @Override
    public void close() {

    }
}
