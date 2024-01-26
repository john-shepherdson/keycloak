import { lazy } from "react";
import type { Path } from "react-router-dom";
import { generateEncodedPath } from "../../utils/generateEncodedPath";
import type { AppRouteObject } from "../../routes";

export type AddPolicyParams = {
  realm: string;
  id: string;
};

const PolicyPage = lazy(() => import("../PolicyPage"));

export const AddPolicyPageRoute: AppRouteObject = {
  path: "/:realm/client-scopes/:id/policies/add",
  element: <PolicyPage />,
  breadcrumb: (t) => t("client-scopes:createClientScopePolicy"),
  handle: {
    access: "manage-clients",
  },
};

export const toAddPolicy = (params: AddPolicyParams): Partial<Path> => {
  const path = AddPolicyPageRoute.path;
  return {
    pathname: generateEncodedPath(path, params),
  };
};
