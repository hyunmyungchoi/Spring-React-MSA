package com.springmsa.authserver.login;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginPageController {

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public String loginPage() {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <title>Spring MSA Login</title>
                    <style>
                        body {
                            font-family: Arial, sans-serif;
                            max-width: 480px;
                            margin: 60px auto;
                        }
                        section {
                            border: 1px solid #ddd;
                            padding: 20px;
                            margin-bottom: 20px;
                            border-radius: 8px;
                        }
                        input, button {
                            width: 100%;
                            padding: 10px;
                            margin-top: 8px;
                            box-sizing: border-box;
                        }
                        pre {
                            background: #f5f5f5;
                            padding: 12px;
                            white-space: pre-wrap;
                        }
                    </style>
                </head>
                <body>
                    <h1>Spring MSA Login</h1>

                    <section>
                        <h2>ID / Password Login</h2>
                        <form method="post" action="/login">
                            <input type="text" name="username" placeholder="Login ID" value="user" />
                            <input type="password" name="password" placeholder="Password" value="password" />
                            <button type="submit">Login</button>
                        </form>
                    </section>

                    <section>
                        <h2>WhatsApp OTP Login</h2>
                        <input id="whatsappNumber" type="text" value="+821012345678" />
                        <button type="button" onclick="sendOtp()">Send OTP</button>

                        <input id="otp" type="text" placeholder="OTP" />
                        <button type="button" onclick="verifyOtp()">Verify OTP</button>

                        <pre id="result"></pre>
                    </section>

                    <script>
                        async function sendOtp() {
                            const whatsappNumber = document.getElementById("whatsappNumber").value;

                            const response = await fetch("/login/whatsapp/send-otp", {
                                method: "POST",
                                headers: {
                                    "Content-Type": "application/json"
                                },
                                body: JSON.stringify({ whatsappNumber })
                            });

                            const data = await response.json();
                            document.getElementById("result").textContent = JSON.stringify(data, null, 2);

                            if (data.devOtp) {
                                document.getElementById("otp").value = data.devOtp;
                            }
                        }

                        async function verifyOtp() {
                            const whatsappNumber = document.getElementById("whatsappNumber").value;
                            const otp = document.getElementById("otp").value;

                            const response = await fetch("/login/whatsapp/verify", {
                                method: "POST",
                                headers: {
                                    "Content-Type": "application/json"
                                },
                                body: JSON.stringify({ whatsappNumber, otp })
                            });

                            const data = await response.json();
                            document.getElementById("result").textContent = JSON.stringify(data, null, 2);

                            if (data.verified && data.authenticated && data.redirectUrl) {
                                window.location.href = data.redirectUrl;
                            }
                        }
                    </script>
                </body>
                </html>
                """;
    }
}