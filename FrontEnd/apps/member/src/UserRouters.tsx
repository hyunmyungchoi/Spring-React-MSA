import { Navigate, Route, Routes } from "react-router-dom";
import CommunityEntryPage from "./community/pages/CommunityEntryPage";
import UserAuthLayout from "./common/layouts/UserAuthLayout";
import UserLayout from "./common/layouts/UserLayout";
import ServiceSelectPage from "./common/pages/ServiceSelectPage";
import UserAuthPage from "./common/pages/UserAuthPage";
import StockEntryPage from "./stock/pages/StockEntryPage";

// Defines the user web page routers.
function UserRouters() {
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

export default UserRouters;
