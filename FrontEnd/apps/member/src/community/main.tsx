import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { Provider } from "react-redux";
import { BrowserRouter, Route, Routes } from "react-router-dom";
import UserAuthLayout from "../common/layouts/UserAuthLayout";
import UserLayout from "../common/layouts/UserLayout";
import UserAuthPage from "../common/pages/UserAuthPage";
import { userStore } from "../common/store/userStore";
import "../App.css";
import "../index.css";
import CommunityEntryPage from "./pages/CommunityEntryPage";

createRoot(document.getElementById("root")!).render(
    <StrictMode>
        <Provider store={userStore}>
            <BrowserRouter>
                <Routes>
                    <Route element={<UserAuthLayout />}>
                        <Route path="/auth" element={<UserAuthPage />} />
                    </Route>
                    <Route element={<UserLayout />}>
                        <Route path="*" element={<CommunityEntryPage />} />
                    </Route>
                </Routes>
            </BrowserRouter>
        </Provider>
    </StrictMode>
);
