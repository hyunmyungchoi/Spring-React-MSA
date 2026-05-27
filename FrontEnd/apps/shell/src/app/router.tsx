import { createBrowserRouter } from "react-router-dom";
import App from "./App";
import LoginPage from "../pages/auth/LoginPage";
import HomePage from "../pages/home/HomePage";
import ProtectedRoute from "../common/components/ProtectedRoute";

export const router = createBrowserRouter([
    {
        path: "/",
        element: <App />,
        children: [
            {
                index: true,
                element: (
                    <ProtectedRoute>
                        <HomePage />
                    </ProtectedRoute>
                ),
            },
            {
                path: "login",
                element: <LoginPage />,
            },
        ],
    },
]);