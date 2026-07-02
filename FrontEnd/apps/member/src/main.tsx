import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { Provider } from "react-redux";
import App from "./App";
import { userStore } from "./store/userStore";
import "./index.css";

createRoot(document.getElementById("root")!).render(
    <StrictMode>
        <Provider store={userStore}>
            <App />
        </Provider>
    </StrictMode>
);
