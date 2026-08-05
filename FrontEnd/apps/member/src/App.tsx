import { BrowserRouter } from "react-router-dom";
import "@springmsa/member-common/App.css";
import UserRouters from "./UserRouters";

// Mounts the user web router.
function App() {
    return (
        <BrowserRouter>
            <UserRouters />
        </BrowserRouter>
    );
}

export default App;

