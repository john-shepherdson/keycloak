import type { AppRouteObject } from "../routes";
import { IdentityFederationsRoute } from "./routes/IdentityFederations";
import { IdentityFederationCreateRoute } from "./routes/IdentityFederationCreate";
import { IdentityFederationRoute } from "./routes/IdentityFederation";

const routes: AppRouteObject[] = [
  IdentityFederationsRoute,
  IdentityFederationCreateRoute,
  IdentityFederationRoute,
];

export default routes;
