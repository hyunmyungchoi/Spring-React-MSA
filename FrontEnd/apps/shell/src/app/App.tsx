import { useEffect } from "react";
import { Outlet } from "react-router-dom";
import { useAppDispatch } from "./reduxHooks";
import { loadCurrentUser } from "../features/auth/authSlice";

function App() {
    const dispatch = useAppDispatch();

    useEffect(() => {
        dispatch(loadCurrentUser());
    }, [dispatch]);

    return <Outlet />;
}

export default App;