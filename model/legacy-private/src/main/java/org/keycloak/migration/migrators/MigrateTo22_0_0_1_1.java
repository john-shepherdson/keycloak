package org.keycloak.migration.migrators;

import org.keycloak.migration.ModelVersion;
import org.keycloak.models.*;
import org.keycloak.representations.idm.RealmRepresentation;
import org.keycloak.utils.StringUtil;

import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.List;

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
        List<FederationModel> federations = realm.getSAMLFederations();
        if (federations != null && !federations.isEmpty()) {
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
            federations.stream().forEach(federation -> realm.upgrateTo22IdPFederation(federation.getInternalId()));
        }
    }

    @Override
    public ModelVersion getVersion() {
        return VERSION;
    }
}
