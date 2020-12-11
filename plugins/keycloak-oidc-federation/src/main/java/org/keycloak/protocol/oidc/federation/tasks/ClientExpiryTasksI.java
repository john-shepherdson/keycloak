package org.keycloak.protocol.oidc.federation.tasks;

import org.keycloak.provider.Provider;

public interface ClientExpiryTasksI extends Provider {
    
    public void scheduleTask(String id, String realmId, long expiresAt);

}
