import { NavLink } from "react-router-dom";
import { useUserLogout } from "../../hooks/useUserLogout";
import { useUserMe } from "../../hooks/useUserMe";

// Renders member navigation and account actions.
function UserNavbar() {
    const { me, loading } = useUserMe();
    const logout = useUserLogout();
    const displayName = me?.name ?? me?.loginId ?? me?.email ?? "Member";

    return (
        <header className="topbar">
            <div>
                <span className="eyebrow">Member Console</span>
                <h1>Choose service</h1>
                <nav className="user-nav" aria-label="member navigation">
                    <NavLink to="/">Home</NavLink>
                    <NavLink to="/chat">Chat</NavLink>
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
                    Sign out
                </button>
            </div>
        </header>
    );
}

export default UserNavbar;
