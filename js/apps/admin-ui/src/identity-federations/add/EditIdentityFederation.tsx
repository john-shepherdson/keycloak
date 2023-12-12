import type IdentityFederationRepresentation from "@keycloak/keycloak-admin-client/lib/defs/identityFederationRepresentation";
import { AlertVariant, PageSection } from "@patternfly/react-core";
import { useState } from "react";
import { FormProvider, useForm } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useNavigate } from "react-router-dom";

import { adminClient } from "../../admin-client";
import { useAlerts } from "../../components/alert/Alerts";
import { ViewHeader } from "../../components/view-header/ViewHeader";
import { useRealm } from "../../context/realm-context/RealmContext";
import { useFetch } from "../../utils/useFetch";
import { useParams } from "../../utils/useParams";
import { IdentityFederationParams } from "../routes/IdentityFederation";
import IdentityFederationForm from "./IdentityFederationForm";
import { toIdentityFederations } from "../routes/IdentityFederations";

export default function EditIdentityFederation() {
  const { t } = useTranslation("identity-federations");
  const { internalId } = useParams<IdentityFederationParams>();
  const form = useForm<IdentityFederationRepresentation>();
  const { reset } = form;
  const [federation, setFederation] =
    useState<IdentityFederationRepresentation>();
  const { realm } = useRealm();
  const { addAlert, addError } = useAlerts();
  const navigate = useNavigate();

  useFetch(
    () => adminClient.identityFederations.findOne({ internalId }),
    (fetchedFederation) => {
      if (!fetchedFederation) {
        throw new Error(t("common:notFound"));
      }
      reset(fetchedFederation);
      setFederation(fetchedFederation);
    },
    [],
  );

  const onSubmit = async (
    identityFederation: IdentityFederationRepresentation,
  ) => {
    try {
      await adminClient.identityFederations.create({
        ...identityFederation,
      });
      navigate(
        toIdentityFederations({
          realm,
        }),
      );
      addAlert(t("updateSuccess"), AlertVariant.success);
    } catch (error) {
      addError("identity-providers:updateError", error);
    }
  };

  return (
    <PageSection variant="light">
      <ViewHeader titleKey={federation?.alias || ""} />
      <FormProvider {...form}>
        <IdentityFederationForm type={"edit"} onSubmit={onSubmit} />
      </FormProvider>
    </PageSection>
  );
}
