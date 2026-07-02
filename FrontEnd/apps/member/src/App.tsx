import { BrowserRouter } from "react-router-dom";
import UserRoutes from "./routes/UserRoutes";

// Mounts the user web router.
function App() {
    return (
        <BrowserRouter>
            <UserRoutes />
        </BrowserRouter>
    );
}

export default App;
