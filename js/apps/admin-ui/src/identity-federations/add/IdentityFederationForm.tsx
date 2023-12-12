import type IdentityFederationRepresentation from "@keycloak/keycloak-admin-client/lib/defs/identityFederationRepresentation";
import { ActionGroup, Button } from "@patternfly/react-core";
import { useTranslation } from "react-i18next";
import { useFormContext } from "react-hook-form";
import { FormAccess } from "../../components/form/FormAccess";
import { ScrollForm } from "../../components/scroll-form/ScrollForm";
import { useRealm } from "../../context/realm-context/RealmContext";
import { Link } from "react-router-dom";
import { toIdentityFederations } from "../routes/IdentityFederations";
import style from "../../components/form/fixed-buttons.module.css";
import IdentityProviderFederationConfig from "./IdentityFederationConfig";
import GeneralSettings from "./GeneralSettings";

type IdentityFederationFormProps = {
  onSubmit: any;
  type: string;
};

export default function IdentityFederationForm({
  onSubmit,
  type,
}: IdentityFederationFormProps) {
  const form = useFormContext<IdentityFederationRepresentation>();
  const {
    handleSubmit,
    watch,
    formState: { isDirty },
  } = form;
  const { t } = useTranslation("identity-federations");
  const { t: th } = useTranslation("identity-federations-help");
  const category = watch("category") as unknown as string;
  const { realm } = useRealm();

  const sections = [
    {
      title: "General Settings",
      panel: (
        <FormAccess
          role="manage-identity-providers"
          isHorizontal
          onSubmit={handleSubmit(onSubmit)}
        >
          <GeneralSettings type={type} />
        </FormAccess>
      ),
    },
    {
      title: t("identityProvidersFederation"),
      helpText: th("identityProvidersFederation"),
      isHidden: category === "Clients",
      panel: (
        <FormAccess
          role="manage-identity-providers"
          isHorizontal
          onSubmit={handleSubmit(onSubmit)}
        >
          <IdentityProviderFederationConfig />
        </FormAccess>
      ),
    },
  ];

  return (
    <>
      <ScrollForm className="pf-u-px-lg" sections={sections} />
      <FormAccess
        role="manage-identity-providers"
        isHorizontal
        onSubmit={handleSubmit(onSubmit)}
      >
        <ActionGroup className={style.buttonGroup}>
          <Button isDisabled={!isDirty} variant="primary" type="submit">
            {type === "edit" ? t("common:save") : t("common:add")}
          </Button>
          <Button
            variant="link"
            data-testid="cancel"
            component={(props) => (
              <Link {...props} to={toIdentityFederations({ realm })} />
            )}
          >
            {t("common:cancel")}
          </Button>
        </ActionGroup>
      </FormAccess>
    </>
  );
}
