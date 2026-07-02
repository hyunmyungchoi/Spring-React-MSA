import { Navigate, Route, Routes } from "react-router-dom";
import UserAuthLayout from "../layouts/UserAuthLayout";
import UserLayout from "../layouts/UserLayout";
import CommunityEntryPage from "../community/pages/CommunityEntryPage";
import ServiceSelectPage from "../pages/ServiceSelectPage";
import StockEntryPage from "../stock/pages/StockEntryPage";
import UserAuthPage from "../pages/UserAuthPage";

// Defines the user web page routes.
function UserRoutes() {
    return (
        <Routes>
            <Route element={<UserAuthLayout />}>
                <Route path="/auth" element={<UserAuthPage />} />
            </Route>

            <Route element={<UserLayout />}>
                <Route path="/" element={<ServiceSelectPage />} />
                <Route path="/community" element={<CommunityEntryPage />} />
                <Route path="/stock" element={<StockEntryPage />} />
            </Route>

            <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
    );
}

export default UserRoutes;
