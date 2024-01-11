package org.keycloak.migration.migrators;

import org.keycloak.broker.federation.FederationProvider;
import org.keycloak.broker.federation.SAMLFederationProviderFactory;
import org.keycloak.events.admin.OperationType;
import org.keycloak.migration.ModelVersion;
import org.keycloak.models.*;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.services.scheduled.ClusterAwareScheduledTaskRunner;
import org.keycloak.services.scheduled.RemoveFederation;
import org.keycloak.services.scheduled.UpgradeTo22Task;
import org.keycloak.timer.TimerProvider;
import org.keycloak.utils.StringUtil;

import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.HashMap;
import java.util.stream.Stream;

public class MigrateTo22_0_0_1_1 implements Migration {

    public static final ModelVersion VERSION = new ModelVersion("22.0.5-1.1");

    @Override
    public void migrate(KeycloakSession session) {
        session.realms().getRealmsStream().forEach(realm -> changeSamlIdPs(realm, session));
    }

    @Override
    public void migrateImport(KeycloakSession session, RealmModel realm, RealmRepresentation rep, boolean skipUserDependent) {
        changeSamlIdPs(realm, session);
    }

    private void changeSamlIdPs(RealmModel realm, KeycloakSession session) {
        session.users().searchForUserStream(realm, Collections.singletonMap(UserModel.INCLUDE_SERVICE_ACCOUNT, Boolean.FALSE.toString())).forEach(user -> {
            session.users().getFederatedIdentitiesStream(realm, user).forEach(identity -> {
                IdentityProviderModel idp = realm.getIdentityProviderByAlias(identity.getIdentityProvider());
                if (idp.getFederations() != null && idp.getFederations().size() > 0) {
                    try {
                        //only federated idp users change
                        session.users().removeFederatedIdentity(realm, user, idp.getAlias());

                        // And create new
                        FederatedIdentityModel newFederatedIdentity = new FederatedIdentityModel(StringUtil.getBase64(idp.getConfig().get("entityId")), identity.getUserId(), identity.getUserName(),
                                identity.getToken());
                        session.users().addFederatedIdentity(realm, user, newFederatedIdentity);
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException(e);
                    }

                }
            });
        });
        for (FederationModel oldFederation : realm.getSAMLFederations()){
              realm.upgrateTo22IdPFederation(oldFederation.getInternalId());
//            UpgradeTo22Task upgradeTo22Task = new UpgradeTo22Task(oldFederation.getInternalId(), realm.getId());
//            ClusterAwareScheduledTaskRunner taskRunner = new ClusterAwareScheduledTaskRunner(session.getKeycloakSessionFactory(), upgradeTo22Task,60 * 1000);
//            TimerProvider timer = session.getProvider(TimerProvider.class);
//            timer.scheduleOnce(taskRunner, 30 * 1000, " UpgradeTo22Federation" + oldFederation.getInternalId());
        }
    }

    @Override
    public ModelVersion getVersion() {
        return VERSION;
    }
}
