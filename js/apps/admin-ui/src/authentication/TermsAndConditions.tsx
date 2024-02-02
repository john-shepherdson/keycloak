import type RequiredActionProviderRepresentation from "@keycloak/keycloak-admin-client/lib/defs/requiredActionProviderRepresentation";
import {
  ActionGroup,
  AlertVariant,
  Button,
  ButtonVariant,
  DropdownItem,
  FormGroup,
  NumberInput,
  PageSection,
  Select,
  SelectOption,
  SelectVariant,
  ValidatedOptions,
} from "@patternfly/react-core";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link } from "react-router-dom";
import { HelpItem } from "ui-shared";
import { adminClient } from "../admin-client";
import { useAlerts } from "../components/alert/Alerts";
import { useConfirmDialog } from "../components/confirm-dialog/ConfirmDialog";
import { ViewHeader } from "../components/view-header/ViewHeader";
import { useRealm } from "../context/realm-context/RealmContext";
import { useFetch } from "../utils/useFetch";
import { toAuthentication } from "./routes/Authentication";
import { FormAccess } from "../components/form/FormAccess";
import { Controller, useForm, useWatch } from "react-hook-form";

export default function TermsAndConditions() {
  const { t } = useTranslation("authentication");
  const { realm } = useRealm();
  const { addAlert, addError } = useAlerts();
  const [key, setKey] = useState(0);
  const refresh = () => setKey(key + 1);
  const [termsAndConditions, setTermsAndConditions] =
    useState<RequiredActionProviderRepresentation>();
  const [resetMultiplierOpen, setResetMultiplierOpen] = useState(false);
  const multipliers = { 3600: "hours", 86400: "days", 2592000: "months" };

  const form = useForm<RequiredActionProviderRepresentation>({
    shouldUnregister: true,
  });

  const {
    handleSubmit,
    control,
    formState: { errors },
    reset,
    getValues,
    setValue,
  } = form;

  const enabled = useWatch({
    control,
    name: "enabled",
  }) as boolean;

  const defaultAction = useWatch({
    control,
    name: "defaultAction",
  }) as boolean;

  useFetch(
    async () => {
      const requiredActions =
        await adminClient.authenticationManagement.getRequiredActions();
      return requiredActions.filter((a) => a.alias === "TERMS_AND_CONDITIONS");
    },
    (result) => {
      reset(result[0] as RequiredActionProviderRepresentation);
      setTermsAndConditions(result[0]);
    },
    [key],
  );

  const save = async () => {
    toggleUpdateTermsDialog();
  };

  const [toggleResetDialog, ResetConfirm] = useConfirmDialog({
    titleKey: t("resetConfirmTitle"),
    messageKey: t("resetConfirmMessage"),
    continueButtonLabel: t("resetConfirmButton"),
    continueButtonVariant: ButtonVariant.danger,
    onConfirm: async () => {
      try {
        await adminClient.authenticationManagement.updateRequiredAction(
          { alias: "TERMS_AND_CONDITIONS" },
          getValues(),
        );
        addAlert(t("requiredActionResetSuccess"), AlertVariant.success);
      } catch (error) {
        addError(t("requiredActionResetError"), error);
      }
    },
  });

  const [toggleUpdateTermsDialog, UpdateTermsConfirm] = useConfirmDialog({
    titleKey: t("updateTermsConfirmTitle"),
    messageKey: t("updateTermsConfirmMessage"),
    continueButtonLabel: t("updateTermsConfirmButton"),
    continueButtonVariant: ButtonVariant.danger,
    onConfirm: async () => {
      try {
        await adminClient.authenticationManagement.updateRequiredAction(
          { alias: "TERMS_AND_CONDITIONS" },
          getValues(),
        );
        refresh();
        addAlert(t("updateTermsResetSuccess"), AlertVariant.success);
      } catch (error) {
        addError(t("updateTermsResetError"), error);
      }
    },
  });

  const updateAction = async (field: "enabled" | "defaultAction") => {
    try {
      if (termsAndConditions) {
        termsAndConditions[field] = !termsAndConditions[field];
        await adminClient.authenticationManagement.updateRequiredAction(
          { alias: termsAndConditions.alias! },
          termsAndConditions,
        );
        setValue("enabled", !enabled);
        addAlert(t("updateTermsResetSuccess"), AlertVariant.success);
      }
    } catch (error) {
      addError("updateTermsResetSuccess", error);
    }
  };

  return (
    <PageSection variant="light">
      <ResetConfirm />
      <UpdateTermsConfirm />
      <ViewHeader
        subKey={t("termsAndConditionsDescription")}
        titleKey={t("termsAndConditions")}
        divider
        dropdownItems={[
          <DropdownItem key="delete" onClick={() => toggleResetDialog()}>
            {t("common:reset")}
          </DropdownItem>,
        ]}
        isEnabled={enabled}
        onToggle={() => {
          updateAction("enabled");
        }}
        isEnabledSecondary={defaultAction}
        isDisabledToggleSecondary={!enabled}
        secondaryToggleLabel={t("setAsDefaultAction")}
        onToggleSecondary={() => updateAction("defaultAction")}
      />
      <FormAccess
        role="manage-identity-providers"
        isHorizontal
        onSubmit={handleSubmit(save)}
        className="pf-u-mt-lg"
      >
        <FormGroup
          label={t("resetInterval")}
          fieldId="kc-name"
          labelIcon={
            <HelpItem
              helpText={t("authentication-help:resetInterval")}
              fieldLabelId="name"
            />
          }
          validated={
            errors.name ? ValidatedOptions.error : ValidatedOptions.default
          }
          helperTextInvalid={t("common:required")}
        >
          <Controller
            name="config.reset_every"
            control={control}
            render={({ field }) => {
              const v = Number(field.value);
              return (
                <NumberInput
                  data-testid="allowedClockSkew"
                  inputName="allowedClockSkew"
                  min={1}
                  max={2147483}
                  value={v}
                  allowEmptyInput={true}
                  readOnly
                  onPlus={() => field.onChange(v + 1)}
                  onMinus={() => field.onChange(v - 1)}
                  onChange={(event) => {
                    const value = Number(
                      (event.target as HTMLInputElement).value,
                    );
                    field.onChange(value < 0 ? 0 : value);
                  }}
                />
              );
            }}
          />
          <Controller
            name="config.reset_every_multiplier"
            control={control}
            render={({ field }) => {
              return (
                <Select
                  placeholderText={t("common:select")}
                  direction="down"
                  onToggle={() => setResetMultiplierOpen(!resetMultiplierOpen)}
                  onSelect={(_, value) => {
                    field.onChange(value);
                    setResetMultiplierOpen(false);
                  }}
                  variant={SelectVariant.single}
                  isOpen={resetMultiplierOpen}
                  width={150}
                  selections={
                    multipliers[field.value as keyof typeof multipliers]
                  }
                >
                  {Object.keys(multipliers).map((key) => {
                    const option = key as unknown as keyof typeof multipliers;
                    return (
                      <SelectOption
                        selected={key === field.value}
                        key={multipliers[option]}
                        data-testid={multipliers[option]}
                        value={key}
                      >
                        {multipliers[option]}
                      </SelectOption>
                    );
                  })}
                </Select>
              );
            }}
          />
        </FormGroup>

        <ActionGroup>
          <Button
            data-testid="new-mapper-save-button"
            variant="primary"
            type="submit"
          >
            {t("common:save")}
          </Button>
          <Button
            data-testid="new-mapper-cancel-button"
            variant="link"
            component={(props) => (
              <Link
                {...props}
                to={toAuthentication({
                  realm,
                  tab: "required-actions",
                })}
              />
            )}
          >
            {t("common:cancel")}
          </Button>
        </ActionGroup>
      </FormAccess>
    </PageSection>
  );
}
