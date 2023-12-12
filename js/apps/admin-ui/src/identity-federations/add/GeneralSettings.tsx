import type IdentityFederationRepresentation from "@keycloak/keycloak-admin-client/lib/defs/identityFederationRepresentation";
import {
  FormGroup,
  Select,
  SelectOption,
  SelectVariant,
  ValidatedOptions,
} from "@patternfly/react-core";
import { useState } from "react";
import { KeycloakTextInput } from "../../components/keycloak-text-input/KeycloakTextInput";
import { HelpItem } from "ui-shared";
import { useTranslation } from "react-i18next";
import { Controller, useFormContext } from "react-hook-form";
import { TimeSelector } from "../../components/time-selector/TimeSelector";

const categories = ["All", "Identity Providers", "Clients"];

type GeneralSettingsProps = {
  type?: string;
};

const GeneralSettings = ({ type }: GeneralSettingsProps) => {
  const {
    register,
    control,
    formState: { errors },
  } = useFormContext<IdentityFederationRepresentation>();
  const { t } = useTranslation("identity-federations");
  const { t: th } = useTranslation("identity-federations-help");
  const [categoriesOpen, setCategoriesOpen] = useState(false);

  return (
    <>
      <FormGroup
        label={t("importFromURL")}
        isRequired
        helperTextInvalid={t("common:required")}
        validated={errors.url ? "error" : "default"}
        labelIcon={
          <HelpItem
            helpText={th("importFromURL")}
            fieldLabelId="identity-federations-help:importFromURL"
          />
        }
      >
        <KeycloakTextInput
          validated={
            errors.url ? ValidatedOptions.error : ValidatedOptions.default
          }
          {...register("url", {
            required: { value: true, message: t("common:required") },
          })}
        />
      </FormGroup>
      <FormGroup
        label={t("alias")}
        isRequired
        helperTextInvalid={t("common:required")}
        validated={errors.alias ? "error" : "default"}
        labelIcon={
          <HelpItem
            helpText={th("alias")}
            fieldLabelId="identity-federation-help:alias"
          />
        }
      >
        <KeycloakTextInput
          isDisabled={type === "edit"}
          validated={
            errors.alias ? ValidatedOptions.error : ValidatedOptions.default
          }
          {...register("alias", {
            required: { value: true, message: t("common:required") },
          })}
        />
      </FormGroup>
      <FormGroup
        label={t("updateFrequency")}
        labelIcon={
          <HelpItem
            helpText={th("updateFrequency")}
            fieldLabelId="updateFrequency"
          />
        }
        helperTextInvalid={t("refreshPeriodInvalid")}
        validated={"updateFrequencyInMins" in errors ? "error" : "default"}
        isRequired
      >
        <Controller
          {...register("updateFrequencyInMins", {
            required: { value: true, message: t("common:required") },
            min: 1,
          })}
          render={({ field }) => (
            <TimeSelector
              value={field.value || 0}
              onChange={field.onChange}
              units={["minute", "hour", "day"]}
              validated={
                "updateFrequencyInMins" in errors ? "error" : "default"
              }
            />
          )}
        />
      </FormGroup>
      <FormGroup
        label={t("category")}
        isRequired
        labelIcon={
          <HelpItem
            helpText={th("category")}
            fieldLabelId="identity-providers:syncModeOverride"
          />
        }
      >
        <Controller
          name="category"
          defaultValue={categories[0]}
          control={control}
          render={({ field }) => (
            <Select
              toggleId="categories"
              required
              direction="down"
              onToggle={() => setCategoriesOpen(!categoriesOpen)}
              onSelect={(_, value) => {
                field.onChange(value.toString());
                setCategoriesOpen(false);
              }}
              selections={field.value}
              variant={SelectVariant.single}
              aria-label={t("category")}
              isOpen={categoriesOpen}
            >
              {categories.map((option) => (
                <SelectOption
                  selected={option === field.value}
                  key={option}
                  data-testid={option}
                  value={option}
                >
                  {option}
                </SelectOption>
              ))}
            </Select>
          )}
        />
      </FormGroup>
    </>
  );
};

export default GeneralSettings;
