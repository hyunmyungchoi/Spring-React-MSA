import { useEffect } from "react";
import { sendUserHeartbeat } from "../api/userAuthApi";

const HEARTBEAT_INTERVAL_MS = 15_000;

// Keeps the member online TTL key alive while an authenticated page is mounted.
export function useUserHeartbeat(enabled: boolean) {
    useEffect(() => {
        if (!enabled) {
            return undefined;
        }

        const heartbeat = () => {
            void sendUserHeartbeat().catch(() => undefined);
        };

        heartbeat();

        const intervalId = window.setInterval(heartbeat, HEARTBEAT_INTERVAL_MS);
        const handleVisibilityChange = () => {
            if (document.visibilityState === "visible") {
                heartbeat();
            }
        };

        document.addEventListener("visibilitychange", handleVisibilityChange);

        return () => {
            window.clearInterval(intervalId);
            document.removeEventListener("visibilitychange", handleVisibilityChange);
        };
    }, [enabled]);
}
