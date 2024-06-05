package org.keycloak.connections.jpa.updater.liquibase.custom;

import liquibase.exception.CustomChangeException;
import liquibase.statement.core.InsertStatement;
import liquibase.structure.core.Table;
import org.keycloak.broker.saml.SAMLIdentityProviderConfig;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class JpaUpdateOmitAttributeConsumingServiceIndexAuthn extends CustomKeycloakTask {

    @Override
    protected void generateStatementsImpl() throws CustomChangeException {
        try (PreparedStatement statement = jdbcConnection.prepareStatement("SELECT INTERNAL_ID FROM " + getTableName("IDENTITY_PROVIDER")
                + " WHERE PROVIDER_ID = 'saml' AND INTERNAL_ID NOT IN (select IDENTITY_PROVIDER_ID FROM "+ getTableName("IDENTITY_PROVIDER_CONFIG")
                + " WHERE NAME = 'attributeConsumingServiceIndex' )")) {
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                     String idpId = rs.getString(1);
                     statements.add(
                         new InsertStatement(null, null, database.correctObjectName("IDENTITY_PROVIDER_CONFIG", Table.class))
                             .addColumnValue("IDENTITY_PROVIDER_ID", idpId)
                             .addColumnValue("NAME", SAMLIdentityProviderConfig.OMIT_ATTRIBUTE_CONSUMING_SERVICE_INDEX_AUTHN)
                             .addColumnValue("VALUE", "true")
                     );
                }
            }
        } catch (Exception e) {
            throw new CustomChangeException(getTaskId() + ": Exception when extracting data from previous version", e);
        }
    }

    @Override
    protected String getTaskId() {
        return "Add omitAttributeConsumingServiceIndexAuthn true when attributeConsumingServiceIndex is null for SAML IdP";
    }
}
