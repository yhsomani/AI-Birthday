export type MainTabParamList = {
  Home: undefined;
  People: undefined;
  Settings: undefined;
};

export type RootStackParamList = {
  Main: undefined;
  Activity: undefined;
  ActivityDetail: { activityId: string };
  Attention: undefined;
  ApprovedMessage: undefined;
  PersonDetail: { personId: string };
  DataBoundary: undefined;
  HelpLegal: undefined;
};

declare global {
  namespace ReactNavigation {
    interface RootParamList extends RootStackParamList {}
  }
}
