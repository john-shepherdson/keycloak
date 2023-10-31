import { useTranslation } from "react-i18next";
import { HelpItem } from "ui-shared";
import { useState } from "react";
import {
  FormGroup,
  Select,
  SelectOption,
  SelectVariant,
} from "@patternfly/react-core";
import { Controller, useFormContext } from "react-hook-form";
import { convertAttributeNameToForm } from "../../util";
import { FormFields } from "../ClientDetails";
import { useFetch } from "../../utils/useFetch";
import { fetchAdminUI } from "../../context/auth/admin-ui-endpoint";

export const Countries = () => {
  const { t } = useTranslation("clients");
  const { control } = useFormContext();
  const [countries, setCountries] = useState<any>({});
  const [countryOpen, setCountryOpen] = useState(false);

  useFetch(
    async () => {
      const countries = await fetchAdminUI("countries");
      return { countries };
    },
    ({ countries }) => {
      if (countries && !Object.keys(countries).includes("error")) {
        setCountries(countries);
      }
    },
    [],
  );

  const countryOptions = [
    <SelectOption key="empty" value="">
      {t("common:choose")}
    </SelectOption>,
    ...Object.keys(countries).map((key) => (
      <SelectOption key={key} value={key}>
        {countries[key]}
      </SelectOption>
    )),
  ];

  return (
    <FormGroup
      label={t("clients:country")}
      fieldId="country"
      labelIcon={
        <HelpItem
          helpText={t("clients-help:country")}
          fieldLabelId="clients:country"
        />
      }
    >
      <Controller
        name={convertAttributeNameToForm<FormFields>("attributes.country")}
        defaultValue=""
        control={control}
        render={({ field }) => (
          <Select
            toggleId="country"
            variant={SelectVariant.single}
            maxHeight="20rem"
            onToggle={setCountryOpen}
            isOpen={countryOpen}
            onSelect={(_, value) => {
              field.onChange(value);
              setCountryOpen(false);
            }}
            selections={field.value}
          >
            {countryOptions}
          </Select>
        )}
      />
    </FormGroup>
  );
};
