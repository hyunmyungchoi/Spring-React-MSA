import { NavLink } from "react-router-dom";
import { useUserLogout } from "../../hooks/useUserLogout";
import { useUserMe } from "../../hooks/useUserMe";

// Renders user navigation and account actions.
function UserNavbar() {
    const { me, loading } = useUserMe();
    const logout = useUserLogout();
    const displayName = me?.name ?? me?.loginId ?? me?.email ?? "사용자";

    return (
        <header className="topbar">
            <div>
                <span className="eyebrow">User Console</span>
                <h1>서비스 선택</h1>
                <nav className="user-nav" aria-label="user navigation">
                    <NavLink to="/">홈</NavLink>
                    <NavLink to="/community">Community</NavLink>
                    <NavLink to="/stock">Stock</NavLink>
                </nav>
            </div>

            <div className="account-area">
                <div className="account-summary">
                    <strong>{displayName}</strong>
                    <span>{me?.email ?? me?.loginId ?? "-"}</span>
                </div>
                <button type="button" onClick={logout} disabled={loading}>
                    로그아웃
                </button>
            </div>
        </header>
    );
}

export default UserNavbar;
