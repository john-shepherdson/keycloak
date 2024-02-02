import type { AppRouteObject } from "../routes";
import {
  AuthenticationRoute,
  AuthenticationRouteWithTab,
} from "./routes/Authentication";
import { CreateFlowRoute } from "./routes/CreateFlow";
import { FlowRoute, FlowWithBuiltInRoute } from "./routes/Flow";
import { TermsAndConditionsRoute } from "./routes/TermsAndConditions";

const routes: AppRouteObject[] = [
  AuthenticationRoute,
  AuthenticationRouteWithTab,
  CreateFlowRoute,
  FlowRoute,
  FlowWithBuiltInRoute,
  TermsAndConditionsRoute,
];

export default routes;
