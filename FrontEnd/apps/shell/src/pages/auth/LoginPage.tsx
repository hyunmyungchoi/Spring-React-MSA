function LoginPage() {
    return (
        <div className="min-h-screen flex items-center justify-center bg-slate-100">
            <div className="w-full max-w-md rounded-xl bg-white p-8 shadow">
                <h1 className="text-2xl font-bold">Login</h1>

                <div className="mt-6 space-y-4">
                    <input
                        className="w-full rounded border px-4 py-2"
                        placeholder="Email"
                    />

                    <input
                        className="w-full rounded border px-4 py-2"
                        placeholder="Password"
                        type="password"
                    />

                    <button className="w-full rounded bg-slate-900 px-4 py-2 text-white">
                        Login
                    </button>
                </div>
            </div>
        </div>
    )
}

export default LoginPage