package com.springmsa.memberbff.auth.service;

import com.springmsa.memberbff.auth.dto.SessionUserResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class BffSessionMetadataService {

    public static final String SESSION_ATTRIBUTE_SUB = "sub";
    public static final String SESSION_ATTRIBUTE_NAME = "name";
    public static final String SESSION_ATTRIBUTE_USERNAME = "username";
    public static final String SESSION_ATTRIBUTE_USER_ID = "userId";
    public static final String SESSION_ATTRIBUTE_LOGIN_ID = "loginId";
    public static final String SESSION_ATTRIBUTE_EMAIL = "email";
    public static final String SESSION_ATTRIBUTE_ROLES = "roles";

    private final BffAuthenticationService bffAuthenticationService;

    public void saveMemberSessionMetadata(HttpSession session, Authentication authentication) {
        SessionUserResponse user = bffAuthenticationService.getSessionUser(authentication);

        setAttribute(session, SESSION_ATTRIBUTE_SUB, user.sub());
        setAttribute(session, SESSION_ATTRIBUTE_NAME, user.name());
        setAttribute(session, SESSION_ATTRIBUTE_USERNAME, user.username());
        setAttribute(session, SESSION_ATTRIBUTE_USER_ID, user.userId());
        setAttribute(session, SESSION_ATTRIBUTE_LOGIN_ID, user.loginId());
        setAttribute(session, SESSION_ATTRIBUTE_EMAIL, user.email());
        session.setAttribute(SESSION_ATTRIBUTE_ROLES, new ArrayList<>(user.roles()));
    }

    private void setAttribute(HttpSession session, String name, Object value) {
        if (value == null) {
            session.removeAttribute(name);
            return;
        }

        session.setAttribute(name, value);
    }
}
