import { Navigate } from "react-router-dom";
import { useAppSelector } from "../../app/reduxHooks";
import * as React from "react";

type ProtectedRouteProps = {
    children: React.ReactNode;
};

function ProtectedRoute({ children }: ProtectedRouteProps) {
    const { authenticated, loading } = useAppSelector((state) => state.auth);

    if (loading) {
        return <div>Loading...</div>;
    }

    if (!authenticated) {
        return <Navigate to="/login" replace />;
    }

    return <>{children}</>;
}

export default ProtectedRoute;