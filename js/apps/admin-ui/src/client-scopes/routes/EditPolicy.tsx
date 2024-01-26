import { lazy } from "react";
import type { Path } from "react-router-dom";
import { generateEncodedPath } from "../../utils/generateEncodedPath";
import type { AppRouteObject } from "../../routes";

export type EditPolicyParams = {
  realm: string;
  id: string;
  policyId: string;
};

const PolicyPage = lazy(() => import("../PolicyPage"));

export const EditPolicyPageRoute: AppRouteObject = {
  path: "/:realm/client-scopes/:id/policies/:policyId",
  element: <PolicyPage />,
  breadcrumb: (t) => t("client-scopes:scopePolicies"),
  handle: {
    access: "manage-clients",
  },
};

export const toEditPolicy = (params: EditPolicyParams): Partial<Path> => {
  const path = EditPolicyPageRoute.path;
  return {
    pathname: generateEncodedPath(path, params),
  };
};
