import { useState } from "react";
import { useAppDispatch } from "../../app/reduxHooks";
import { logoutCurrentUser } from "../../features/auth/authSlice";
import { getUserMe, type UserMeResponse } from "../../features/user/userApi";

function HomePage() {
    const dispatch = useAppDispatch();


    const [userMe, setUserMe] = useState<UserMeResponse | null>(null);
    const [loading, setLoading] = useState(false);
    const [errorMessage, setErrorMessage] = useState<string | null>(null);

    const handleLoadUserMe = async () => {
        setLoading(true);
        setErrorMessage(null);

        try {
            const response = await getUserMe();
            setUserMe(response);
        } catch (error) {
            console.error(error);
            setUserMe(null);
            setErrorMessage("/bff/user/me 호출 실패");
        } finally {
            setLoading(false);
        }
    };

    const handleLogout = async () => {
        try {
            await dispatch(logoutCurrentUser()).unwrap();
        } catch (error) {
            console.error(error);
            setErrorMessage("로그아웃 실패");
        }
    };



    return (
        <main style={{ padding: "24px" }}>
            <h1>Spring MSA Home</h1>

            <section style={{ marginTop: "16px" }}>
                <button type="button" onClick={handleLoadUserMe} disabled={loading}>
                    {loading ? "Loading..." : "User Me 호출"}
                </button>

                <button
                    type="button"
                    onClick={handleLogout}
                    style={{ marginTop: "8px" }}
                >
                    Logout
                </button>
            </section>

            {errorMessage && (
                <p style={{ marginTop: "16px", color: "red" }}>{errorMessage}</p>
            )}

            {userMe && (
                <section style={{ marginTop: "16px" }}>
                    <h2>User Service Response</h2>

                    <pre
                        style={{
                            padding: "16px",
                            backgroundColor: "#f5f5f5",
                            borderRadius: "8px",
                            whiteSpace: "pre-wrap",
                        }}
                    >
            {JSON.stringify(userMe, null, 2)}
          </pre>
                </section>
            )}
        </main>
    );
}

export default HomePage;