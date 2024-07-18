package org.keycloak.services.scheduled;

import java.time.Instant;

import org.keycloak.broker.federation.FederationProvider;
import org.keycloak.broker.federation.SAMLFederationProviderFactory;
import org.keycloak.models.IdentityProviderModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.protocol.saml.ConfigureAutoUpdateSAMLClient;
import org.keycloak.timer.ScheduledTask;
import org.keycloak.timer.TimerProvider;

public class StartAutoUpdatedScheduledTasks implements ScheduledTask {

    private static final String SAML_AUTO_UPDATED = "saml.auto.updated";

    public StartAutoUpdatedScheduledTasks() {
    }

    /**
     * fide autoupdated IdPs in storage and create scheduled tasks based on refreshPeriod and lastRefreshTime
     *
     * @param session
     */
    @Override
    public void run(KeycloakSession session) {
        TimerProvider timer = session.getProvider(TimerProvider.class);
        ConfigureAutoUpdateSAMLClient conf = session.getProvider(ConfigureAutoUpdateSAMLClient.class);
        session.realms().getRealmsStream().forEach(realm -> {
            realm.getAutoUpdatedIdentityProvidersStream().forEach(idp -> {
                if (idp.getConfig().get(IdentityProviderModel.REFRESH_PERIOD) != null ) {
                    AutoUpdateIdentityProviders autoUpdateProvider = new AutoUpdateIdentityProviders(idp.getAlias(), realm.getId());
                    ClusterAwareScheduledTaskRunner taskRunner = new ClusterAwareScheduledTaskRunner(session.getKeycloakSessionFactory(), autoUpdateProvider, Long.valueOf(idp.getConfig().get(IdentityProviderModel.REFRESH_PERIOD)) * 1000);
                    long delay = idp.getConfig().get(IdentityProviderModel.LAST_REFRESH_TIME) == null ? Long.parseLong(idp.getConfig().get(IdentityProviderModel.REFRESH_PERIOD)) * 1000 : Long.parseLong(idp.getConfig().get(IdentityProviderModel.LAST_REFRESH_TIME)) + (Long.parseLong(idp.getConfig().get(IdentityProviderModel.REFRESH_PERIOD)) * 1000) - Instant.now().toEpochMilli();
                    timer.schedule(taskRunner, delay < 1000 ? 1000 : delay, Long.valueOf(idp.getConfig().get(IdentityProviderModel.REFRESH_PERIOD)) * 1000, realm.getId() + "_AutoUpdateIdP_" + idp.getAlias());
                }
            });
            realm.getSAMLFederations().stream().forEach(fedModel -> {
                FederationProvider federationProvider = SAMLFederationProviderFactory.getSAMLFederationProviderFactoryById(session, fedModel.getProviderId()).create(session, fedModel, realm.getId());
                federationProvider.enableUpdateTask();
            });
            session.clients().getClientsStream(realm).filter(clientModel -> "saml".equals(clientModel.getProtocol()) && clientModel.getAttributes() != null && Boolean.valueOf(clientModel.getAttributes().get(SAML_AUTO_UPDATED))).forEach(clientModel -> conf.configure(clientModel, realm));
        });

    }

}
