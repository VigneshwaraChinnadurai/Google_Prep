import { useQuery } from "@tanstack/react-query";
import { Route, Switch, useLocation } from "wouter";
import { api } from "./lib/api";
import LoginPage from "./pages/LoginPage";
import DashboardPage from "./pages/DashboardPage";
import ProfilePage from "./pages/ProfilePage";
import JobsPage from "./pages/JobsPage";
import ApplicationsPage from "./pages/ApplicationsPage";
import Layout from "./components/Layout";

export default function App() {
  const { data: auth, isLoading } = useQuery({
    queryKey: ["auth"],
    queryFn: () => api.getMe(),
    retry: false,
  });

  if (isLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-primary"></div>
      </div>
    );
  }

  if (!auth?.authenticated) {
    return <LoginPage />;
  }

  return (
    <Layout>
      <Switch>
        <Route path="/" component={DashboardPage} />
        <Route path="/profile" component={ProfilePage} />
        <Route path="/jobs" component={JobsPage} />
        <Route path="/applications" component={ApplicationsPage} />
        <Route>
          <div className="p-8 text-center text-muted-foreground">Page not found</div>
        </Route>
      </Switch>
    </Layout>
  );
}
