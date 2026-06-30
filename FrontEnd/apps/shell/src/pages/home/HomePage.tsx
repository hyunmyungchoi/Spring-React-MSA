import { useState } from "react";
import { useAppDispatch, useAppSelector } from "../../app/reduxHooks";
import { logoutCurrentUser } from "../../features/auth/authSlice";
import { getUserMe, type UserMeResponse } from "../../features/user/userApi";

function HomePage() {
    const dispatch = useAppDispatch();
    const user = useAppSelector((state) => state.auth.user);
    const [userMe, setUserMe] = useState<UserMeResponse | null>(null);
    const [message, setMessage] = useState("");
    const [loading, setLoading] = useState(false);

    const displayName = user?.name ?? user?.loginId ?? user?.email ?? "사용자";

    const handleLoadUserMe = async () => {
        setLoading(true);
        setMessage("");

        try {
            const response = await getUserMe();
            setUserMe(response);
        } catch (error) {
            setUserMe(null);
            setMessage(error instanceof Error ? error.message : "내정보 조회 실패");
        } finally {
            setLoading(false);
        }
    };

    const handleLogout = async () => {
        setLoading(true);
        setMessage("");

        try {
            await dispatch(logoutCurrentUser()).unwrap();
        } catch (error) {
            setMessage(error instanceof Error ? error.message : "로그아웃 실패");
            setLoading(false);
        }
    };

    const handleComingSoon = (serviceName: string) => {
        setUserMe(null);
        setMessage(`${serviceName} 준비중입니다.`);
    };

    return (
        <main className="app-shell user-shell">
            <header className="topbar">
                <div>
                    <span className="eyebrow">User Console</span>
                    <h1>서비스 선택</h1>
                </div>

                <div className="account-area">
                    <div className="account-summary">
                        <strong>{displayName}</strong>
                        <span>{user?.email ?? user?.loginId ?? "-"}</span>
                    </div>
                    <button type="button" onClick={handleLoadUserMe} disabled={loading}>
                        내정보보기
                    </button>
                    <button type="button" onClick={handleLogout} disabled={loading}>
                        로그아웃
                    </button>
                </div>
            </header>

            <section className="service-grid">
                <button type="button" className="service-tile" onClick={() => handleComingSoon("커뮤니티")}>
                    <span>커뮤니티</span>
                    <strong>Community</strong>
                </button>
                <button type="button" className="service-tile" onClick={handleLoadUserMe}>
                    <span>유저</span>
                    <strong>User</strong>
                </button>
                <button type="button" className="service-tile" onClick={() => handleComingSoon("스톡")}>
                    <span>스톡</span>
                    <strong>Stock</strong>
                </button>
            </section>

            {message && <p className="status-message">{message}</p>}

            {userMe && (
                <section className="info-panel">
                    <h2>내정보</h2>
                    <dl>
                        <div>
                            <dt>User ID</dt>
                            <dd>{userMe.userId}</dd>
                        </div>
                        <div>
                            <dt>Login ID</dt>
                            <dd>{userMe.loginId}</dd>
                        </div>
                        <div>
                            <dt>Email</dt>
                            <dd>{userMe.email}</dd>
                        </div>
                        <div>
                            <dt>Roles</dt>
                            <dd>{userMe.roles.join(", ")}</dd>
                        </div>
                    </dl>
                </section>
            )}
        </main>
    );
}

export default HomePage;
