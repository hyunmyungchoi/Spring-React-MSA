import { useEffect } from "react";
import { Navigate, Outlet } from "react-router-dom";
import { useUserMe } from "../hooks/useUserMe";

// Wraps user authentication pages and redirects signed-in users.
function UserAuthLayout() {
    const { isAuthenticated, loading, loadMe } = useUserMe();

    useEffect(() => {
        void loadMe();
    }, [loadMe]);

    if (loading) {
        return <div className="screen-loader">Loading...</div>;
    }

    if (isAuthenticated) {
        return <Navigate to="/" replace />;
    }

    return (
        <main className="auth-page user-auth-page">
            <Outlet />
        </main>
    );
}

export default UserAuthLayout;
