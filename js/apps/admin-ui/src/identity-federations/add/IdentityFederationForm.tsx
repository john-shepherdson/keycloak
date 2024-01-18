import type IdentityProviderMapperRepresentation from "@keycloak/keycloak-admin-client/lib/defs/identityProviderMapperRepresentation";
import type IdentityFederationRepresentation from "@keycloak/keycloak-admin-client/lib/defs/identityFederationRepresentation";
import {
  ActionGroup,
  AlertVariant,
  Button,
  ButtonVariant,
  Tab,
  TabTitleText,
  ToolbarItem,
  Spinner,
} from "@patternfly/react-core";
import { useTranslation } from "react-i18next";
import { useState, useEffect } from "react";
import { adminClient } from "../../admin-client";
import { useFormContext } from "react-hook-form";
import { FormAccess } from "../../components/form/FormAccess";
import { ScrollForm } from "../../components/scroll-form/ScrollForm";
import { useRealm } from "../../context/realm-context/RealmContext";
import { Link, useParams, useNavigate } from "react-router-dom";
import { toIdentityFederations } from "../routes/IdentityFederations";
import style from "../../components/form/fixed-buttons.module.css";
import IdentityProviderFederationConfig from "./IdentityFederationConfig";
import GeneralSettings from "./GeneralSettings";
import {
  RoutableTabs,
  useRoutableTab,
} from "../../components/routable-tabs/RoutableTabs";
import {
  IdentityFederationParams,
  IdentityFederationTab,
  toIdentityFederation,
} from "../routes/IdentityFederation";
import { toIdentityFederationAddMapper } from "../routes/AddMapper";
import { toIdentityFederationEditMapper } from "../routes/EditMapper";
import {
  KeycloakDataTable,
  Action,
} from "../../components/table-toolbar/KeycloakDataTable";
import { ListEmptyState } from "../../components/list-empty-state/ListEmptyState";
import { useConfirmDialog } from "../../components/confirm-dialog/ConfirmDialog";
import { useAlerts } from "../../components/alert/Alerts";
import AllowDenyList from "./AllowDenyList";

type IdentityFederationFormProps = {
  onSubmit: any;
  type: string;
  internalId?: string;
  providerId: string;
  tab?: string;
};

type IdPWithMapperAttributes = IdentityProviderMapperRepresentation & {
  name: string;
  category?: string;
  helpText?: string;
  type: string;
  providerId: string;
  mapperId: string;
};

type MapperLinkProps = IdPWithMapperAttributes;

const MapperLink = ({ name, mapperId, providerId }: MapperLinkProps) => {
  const { realm } = useRealm();
  const { internalId } = useParams<IdentityFederationParams>();

  return (
    <Link
      to={toIdentityFederationEditMapper({
        realm,
        internalId: internalId!,
        providerId: providerId,
        id: mapperId,
      })}
    >
      {name}
    </Link>
  );
};

export default function IdentityFederationForm({
  onSubmit,
  type,
  internalId,
  providerId,
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
  const [key, setKey] = useState(0);
  const refresh = () => setKey(key + 1);
  const [mapperAction, setmapperAction] = useState("");
  const { addAlert, addError } = useAlerts();

  const toTab = (tab: IdentityFederationTab) =>
    toIdentityFederation({
      realm,
      providerId: providerId,
      internalId: internalId || "",
      tab,
    });

  const useTab = (tab: IdentityFederationTab) => useRoutableTab(toTab(tab));
  const settingsTab = useTab("settings");
  const mappersTab = useTab("mappers");
  const [selectedMapper, setSelectedMapper] =
    useState<IdPWithMapperAttributes>();
  const navigate = useNavigate();
  const [mapperLoading, setMapperLoading] = useState("");

  useEffect(() => {
    refresh();
  }, [mapperLoading]);
  const [toggleConfirmMapperDialog, MapperConfirm] = useConfirmDialog({
    titleKey: `identity-federations:${mapperAction}Mapper`,
    messageKey: t(`identity-federations:${mapperAction}MapperConfirm`, {
      mapper: selectedMapper?.name,
    }),
    continueButtonLabel: `identity-federations:${mapperAction}`,
    continueButtonVariant:
      mapperAction === "update"
        ? ButtonVariant.warning
        : mapperAction === "add"
        ? ButtonVariant.primary
        : ButtonVariant.danger,
    onConfirm: async () => {
      try {
        setMapperLoading(selectedMapper?.mapperId!);
        await adminClient.identityFederations.updateFederationMapper({
          internalId: internalId!,
          action: mapperAction,
          mapperId: selectedMapper?.mapperId!,
        });
        setMapperLoading("");
        addAlert(
          t(`identity-federations:${mapperAction}mapperSuccess`),
          AlertVariant.success,
        );
        navigate(
          toIdentityFederation({
            providerId,
            internalId: internalId!,
            tab: "mappers",
            realm,
          }),
        );
      } catch (error) {
        setMapperLoading("");
        addError("identity-providers:deleteErrorError", error);
      }
    },
  });

  const loader = async () => {
    const [loaderMappers, loaderMapperTypes] = await Promise.all([
      adminClient.identityFederations.findMappers({ internalId: internalId! }),
      adminClient.identityFederations.findMapperTypes(),
    ]);
    const components = loaderMappers.map((loaderMapper) => {
      const mapperType: any = Object.values(loaderMapperTypes).find(
        (loaderMapperType: any) =>
          loaderMapper.identityProviderMapper! === loaderMapperType.id!,
      );
      const result: IdPWithMapperAttributes = {
        ...mapperType,
        name: loaderMapper.name!,
        type: mapperType?.name!,
        mapperId: loaderMapper.id!,
      };

      return result;
    });

    return components;
  };

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
    {
      title: t("allowList"),
      panel: (
        <FormAccess
          role="manage-identity-providers"
          isHorizontal
          onSubmit={handleSubmit(onSubmit)}
        >
          <AllowDenyList type="Allow" />
        </FormAccess>
      ),
    },
    {
      title: t("denyList"),
      panel: (
        <FormAccess
          role="manage-identity-providers"
          isHorizontal
          onSubmit={handleSubmit(onSubmit)}
        >
          <AllowDenyList type="Deny" />
        </FormAccess>
      ),
    },
  ];

  return (
    <>
      <RoutableTabs isBox defaultLocation={toTab("settings")}>
        <Tab
          id="settings"
          title={<TabTitleText>{t("common:settings")}</TabTitleText>}
          {...settingsTab}
        >
          <ScrollForm className="pf-u-px-lg" sections={sections} />
        </Tab>
        {!!internalId && (
          <Tab
            id="mappers"
            data-testid="mappers-tab"
            title={<TabTitleText>{t("common:mappers")}</TabTitleText>}
            {...mappersTab}
          >
            <MapperConfirm />
            <KeycloakDataTable
              emptyState={
                <ListEmptyState
                  message={t("identity-providers:noMappers")}
                  instructions={t("identity-providers:noMappersInstructions")}
                  primaryActionText={t("identity-providers:addMapper")}
                  onPrimaryAction={() =>
                    navigate(
                      toIdentityFederationAddMapper({
                        realm,
                        internalId: internalId!,
                        providerId: providerId!,
                        tab: "mappers",
                      }),
                    )
                  }
                />
              }
              loader={loader}
              key={key}
              ariaLabelKey="identity-providers:mappersList"
              searchPlaceholderKey="identity-providers:searchForMapper"
              toolbarItem={
                <ToolbarItem>
                  <Button
                    id="add-mapper-button"
                    component={(props) => (
                      <Link
                        {...props}
                        to={toIdentityFederationAddMapper({
                          realm,
                          internalId: internalId!,
                          providerId: providerId!,
                          tab: "mappers",
                        })}
                      />
                    )}
                    data-testid="addMapper"
                  >
                    {t("identity-federations:createMapper")}
                  </Button>
                </ToolbarItem>
              }
              columns={[
                {
                  name: "name",
                  displayKey: "common:name",
                  cellRenderer: (row) => (
                    <MapperLink {...row} providerId={providerId} />
                  ),
                },
                {
                  name: "category",
                  displayKey: "common:category",
                },
                {
                  name: "type",
                  displayKey: "common:type",
                },
                {
                  name: "",
                  cellRenderer: (row) =>
                    mapperLoading === row.mapperId ? (
                      <Spinner isSVG size="lg" />
                    ) : (
                      ""
                    ),
                },
              ]}
              actions={[
                ...(!mapperLoading
                  ? [
                      {
                        title: t("identity-federations:addMapper"),
                        onRowClick: (mapper) => {
                          setSelectedMapper(mapper);
                          setmapperAction("add");
                          toggleConfirmMapperDialog();
                        },
                      } as Action<IdPWithMapperAttributes>,
                      {
                        title: t("identity-federations:updateMapper"),
                        onRowClick: (mapper) => {
                          setSelectedMapper(mapper);
                          setmapperAction("update");
                          toggleConfirmMapperDialog();
                        },
                      } as Action<IdPWithMapperAttributes>,
                      {
                        title: t("identity-federations:removeMapper"),
                        onRowClick: (mapper) => {
                          setSelectedMapper(mapper);
                          setmapperAction("remove");
                          toggleConfirmMapperDialog();
                        },
                      } as Action<IdPWithMapperAttributes>,
                    ]
                  : []),
              ]}
            />
          </Tab>
        )}
      </RoutableTabs>
      <FormAccess
        role="manage-identity-providers"
        isHorizontal
        onSubmit={handleSubmit(onSubmit)}
      >
        <ActionGroup className={style.buttonGroup}>
          <Button isDisabled={!isDirty} variant="primary" type="submit">
            {type === "update" ? t("common:save") : t("common:add")}
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
