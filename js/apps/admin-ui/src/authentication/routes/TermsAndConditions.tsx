import { lazy } from "react";
import type { Path } from "react-router-dom";
import { generateEncodedPath } from "../../utils/generateEncodedPath";
import type { AppRouteObject } from "../../routes";

export type TermsAndConditionsParams = { realm: string };

const TermsAndConditions = lazy(() => import("../TermsAndConditions"));

export const TermsAndConditionsRoute: AppRouteObject = {
  path: "/:realm/authentication/required-actions/terms-and-conditions",
  element: <TermsAndConditions />,
  breadcrumb: (t) => t("authentication:termsAndConditions"),
  handle: {
    access: "manage-authorization",
  },
};

export const toTermsAndConditions = (
  params: TermsAndConditionsParams,
): Partial<Path> => ({
  pathname: generateEncodedPath(TermsAndConditionsRoute.path, params),
});
