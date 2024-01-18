import IdentityProviderRepresentation from "@keycloak/keycloak-admin-client/lib/defs/identityProviderRepresentation";
import { FormGroup } from "@patternfly/react-core";
import { Controller, useFormContext } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { HelpItem } from "ui-shared";
import { KeycloakTextInput } from "../../components/keycloak-text-input/KeycloakTextInput";
import { SwitchField } from "./SwitchField";

import "../add/discovery-settings.css";
import { TimeSelector } from "../../components/time-selector/TimeSelector";

type AutoUpdateFieldsProps = {
  hideMetadata?: boolean;
  protocol?: string;
};

export const AutoUpdateFields = ({
  hideMetadata,
  protocol,
}: AutoUpdateFieldsProps) => {
  const { t } = useTranslation("identity-providers");

  const {
    register,
    watch,
    formState: { errors },
  } = useFormContext<IdentityProviderRepresentation>();

  const autoUpdated = watch("config.autoUpdate") as unknown as string;
  const lastRefreshed = watch("config.lastRefreshTime") as unknown as string;

  return (
    <div className="pf-c-form pf-m-horizontal">
      {!hideMetadata && (
        <FormGroup
          label={
            protocol === "saml"
              ? t("clients:metadataUrl")
              : t("identity-providers:discoveryEndpoint")
          }
          isRequired={autoUpdated === "true"}
          fieldId="kc-saml-identity-provider-metadata-url"
          helperTextInvalid={t("common:required")}
          validated={errors.config?.metadataUrl ? "error" : "default"}
          labelIcon={
            <HelpItem
              helpText={
                protocol === "saml"
                  ? t("clients-help::metadataUrl")
                  : t("identity-providers-help:discoveryEndpoint")
              }
              fieldLabelId="clients-help:metadataUrl"
            />
          }
        >
          <KeycloakTextInput
            data-testid="identityProviderMetadataUrl"
            id="kc-saml-identity-provider-metadata-url"
            {...register("config.metadataUrl", {
              required: autoUpdated === "true",
            })}
          />
        </FormGroup>
      )}

      <SwitchField field="config.autoUpdate" label="autoUpdate" />
      {autoUpdated === "true" && (
        <>
          <FormGroup
            label={t("clients:refreshPeriod")}
            labelIcon={
              <HelpItem
                helpText={t("clients-help:refreshPeriod")}
                fieldLabelId="clients-help:refreshPeriod"
              />
            }
            helperTextInvalid={t("refreshPeriodInvalid")}
            validated={
              "refreshPeriod" in (errors.config ?? {}) ? "error" : "default"
            }
            isRequired
          >
            <Controller
              {...register("config.refreshPeriod", {
                required: { value: true, message: t("common:required") },
                min: 1,
              })}
              render={({ field }) => (
                <TimeSelector
                  value={field.value || 0}
                  onChange={field.onChange}
                  units={["minute", "hour", "day"]}
                  validated={
                    "refreshPeriod" in (errors.config ?? {})
                      ? "error"
                      : "default"
                  }
                />
              )}
            />
          </FormGroup>
          {!!lastRefreshed && (
            <FormGroup
              label={t("clients:lastRefresh")}
              labelIcon={
                <HelpItem
                  helpText={t("clients-help:lastRefresh")}
                  fieldLabelId="clients-help:lastRefresh"
                />
              }
            >
              {new Date(parseInt(lastRefreshed)).toLocaleString()}
            </FormGroup>
          )}
        </>
      )}
    </div>
  );
};
