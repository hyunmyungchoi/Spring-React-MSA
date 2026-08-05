import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { Provider } from "react-redux";
import { userStore } from "@springmsa/member-common/store/userStore";
import "@springmsa/member-common/index.css";
import App from "./App";

createRoot(document.getElementById("root")!).render(
    <StrictMode>
        <Provider store={userStore}>
            <App />
        </Provider>
    </StrictMode>
);

