package org.keycloak.models;

import org.keycloak.models.enums.ClientRegistrationTypeEnum;
import org.keycloak.models.enums.EntityTypeEnum;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenIdFederationConfig {

    private String internalId;
    private String trustAnchor;
    private EntityTypeEnum entityType;
    private List<ClientRegistrationTypeEnum> clientRegistrationTypesSupported;
    private Map<String, String> idpConfiguration  = new HashMap<>();

    public OpenIdFederationConfig() {}

    public String getInternalId() {
        return internalId;
    }

    public void setInternalId(String internalId) {
        this.internalId = internalId;
    }

    public String getTrustAnchor() {
        return trustAnchor;
    }

    public void setTrustAnchor(String trustAnchor) {
        this.trustAnchor = trustAnchor;
    }

    public List<ClientRegistrationTypeEnum> getClientRegistrationTypesSupported() {
        return clientRegistrationTypesSupported;
    }

    public void setClientRegistrationTypesSupported(List<ClientRegistrationTypeEnum> clientRegistrationTypesSupported) {
        this.clientRegistrationTypesSupported = clientRegistrationTypesSupported;
    }

    public EntityTypeEnum getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityTypeEnum entityType) {
        this.entityType = entityType;
    }

    public Map<String, String> getIdpConfiguration() {
        return idpConfiguration;
    }

    public void setIdpConfiguration(Map<String, String> idpConfiguration) {
        this.idpConfiguration = idpConfiguration;
    }
}
