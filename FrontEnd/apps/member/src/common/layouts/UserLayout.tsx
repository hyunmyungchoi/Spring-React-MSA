import { useEffect } from "react";
import { Navigate, Outlet } from "react-router-dom";
import UserNavbar from "../components/layout/UserNavbar";
import { useUserHeartbeat } from "../hooks/useUserHeartbeat";
import { useUserMe } from "../hooks/useUserMe";

// Wraps authenticated user pages and guards them with session state.
function UserLayout() {
    const { isAuthenticated, loading, loadMe } = useUserMe();

    useEffect(() => {
        void loadMe();
    }, [loadMe]);

    useUserHeartbeat(isAuthenticated && !loading);

    if (loading) {
        return <div className="screen-loader">Loading...</div>;
    }

    if (!isAuthenticated) {
        return <Navigate to="/auth" replace />;
    }

    return (
        <main className="user-app user-workspace">
            <UserNavbar />
            <Outlet />
        </main>
    );
}

export default UserLayout;
