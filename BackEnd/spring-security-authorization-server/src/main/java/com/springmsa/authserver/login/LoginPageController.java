package com.springmsa.authserver.login;

import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LoginPageController {

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public String loginPage(@RequestAttribute("_csrf") CsrfToken csrfToken) {
        String html = """
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
                            __CSRF_INPUT__
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
                    
                    <section>
                    
                        <h3>Email OTP Login</h3>
                    
                        <div>
                            <label>Email</label>
                            <input type="email" id="emailOtpEmail" value="user@test.com"/>
                        </div>
                    
                        <div>
                            <button type="button" onclick="sendEmailOtp()">Send Email OTP</button>
                        </div>
                    
                        <div>
                            <label>Email OTP</label>
                            <input type="text" id="emailOtpCode" placeholder="Enter OTP"/>
                        </div>
                    
                        <div>
                            <button type="button" onclick="verifyEmailOtp()">Verify Email OTP</button>
                        </div>
                    
                        <pre id="emailOtpResult"></pre>
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
                        
                        async function sendEmailOtp() {
                            const email = document.getElementById("emailOtpEmail").value;
                
                            const response = await fetch("/login/email/send-otp", {
                                method: "POST",
                                headers: {
                                    "Content-Type": "application/json"
                                },
                                body: JSON.stringify({
                                    email: email
                                })
                            });
                
                            const data = await response.json();
                
                            document.getElementById("emailOtpResult").textContent =
                                JSON.stringify(data, null, 2);
                
                            if (data.devOtp) {
                                document.getElementById("emailOtpCode").value = data.devOtp;
                            }
                        }
                
                        async function verifyEmailOtp() {
                            const email = document.getElementById("emailOtpEmail").value;
                            const otp = document.getElementById("emailOtpCode").value;
                
                            const response = await fetch("/login/email/verify", {
                                method: "POST",
                                headers: {
                                    "Content-Type": "application/json"
                                },
                                body: JSON.stringify({
                                    email: email,
                                    otp: otp
                                })
                            });
                
                            const data = await response.json();
                
                            document.getElementById("emailOtpResult").textContent =
                                JSON.stringify(data, null, 2);
                
                            if (data.redirectUrl) {
                                window.location.href = data.redirectUrl;
                            }
                        }
                    </script>
                </body>
                </html>
                """;

        String csrfInput = """
        <input type="hidden" name="%s" value="%s" />
        """.formatted(csrfToken.getParameterName(), csrfToken.getToken());

        return html.replace("__CSRF_INPUT__", csrfInput);
    }
}