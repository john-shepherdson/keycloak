import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Link, useNavigate, useParams } from "react-router-dom";
import { AlertVariant, Button, ButtonVariant } from "@patternfly/react-core";
import type ProtocolMapperRepresentation from "@keycloak/keycloak-admin-client/lib/defs/protocolMapperRepresentation";
import { ListEmptyState } from "../../components/list-empty-state/ListEmptyState";
import {
  Action,
  KeycloakDataTable,
} from "../../components/table-toolbar/KeycloakDataTable";
import { ClientScopeParams } from "../routes/ClientScope";
import { useRealm } from "../../context/realm-context/RealmContext";
import { toAddPolicy } from "../routes/AddPolicy";
import { useFetch } from "../../utils/useFetch";
import { adminClient } from "../../admin-client";
import { toEditPolicy } from "../routes/EditPolicy";
import { useAlerts } from "../../components/alert/Alerts";
import { useConfirmDialog } from "../../components/confirm-dialog/ConfirmDialog";
type Row = ProtocolMapperRepresentation & {
  category: string;
  type: string;
  priority: number;
};

export const PoliciesList = () => {
  const { t } = useTranslation("client-scopes");
  const { id } = useParams<ClientScopeParams>();
  const { realm } = useRealm();
  const navigate = useNavigate();
  const [policies, setPolicies] = useState<any>([]);
  const { addAlert, addError } = useAlerts();
  const [selectedPolicy, setSelectedPolicy] = useState("");
  const [key, setKey] = useState(1);
  const refresh = () => {
    setKey(key + 1);
  };
  useFetch(
    async () => {
      const policies = await adminClient.clientScopes.listPolicies({ id: id! });
      return { policies };
    },
    ({ policies }) => {
      setPolicies(policies);
    },
    [key],
  );

  const [toggleDeletePolicyDialog, DeletePolicyConfirm] = useConfirmDialog({
    titleKey: "client-scopes:deleteScopePolicy",
    messageKey: t("deleteScopePolicyConfirm"),
    continueButtonLabel: "common:delete",
    continueButtonVariant: ButtonVariant.danger,
    onConfirm: async () => {
      try {
        await adminClient.clientScopes.delPolicy({
          policyId: selectedPolicy!,
          id: id!,
        });
        addAlert(t("deleteScopePolicySuccess"), AlertVariant.success);
        refresh();
      } catch (error) {
        addError("deleteScopePolicyError", error);
      }
    },
  });

  return (
    <>
      <DeletePolicyConfirm />
      <KeycloakDataTable
        loader={policies}
        ariaLabelKey="client-scopes:clientScopeList"
        isPaginated={false}
        toolbarItem={
          <Button
            component={(props) => (
              <Link
                {...props}
                to={toAddPolicy({
                  realm,
                  id: id!,
                })}
              />
            )}
          >
            {t("createPolicy")}
          </Button>
        }
        actions={[
          {
            title: t("common:delete"),
            onRowClick: (node) => {
              setSelectedPolicy(node.id!);
              toggleDeletePolicyDialog();
            },
          } as Action<Row>,
        ]}
        columns={[
          {
            name: t("userAttribute"),
            cellRenderer: (row: any) => {
              return (
                <Link
                  to={toEditPolicy({
                    realm,
                    id: id!,
                    policyId: row.id!,
                  })}
                >
                  {row.userAttribute}
                </Link>
              );
            },
          },
        ]}
        emptyState={
          <ListEmptyState
            message={t("emptyPolicies")}
            instructions={t("emptyPoliciesInstructions")}
            secondaryActions={[
              {
                text: t("common:create"),
                onClick: () =>
                  navigate(
                    toAddPolicy({
                      realm,
                      id: id!,
                    }),
                  ),
              },
            ]}
          />
        }
      />
    </>
  );
};
