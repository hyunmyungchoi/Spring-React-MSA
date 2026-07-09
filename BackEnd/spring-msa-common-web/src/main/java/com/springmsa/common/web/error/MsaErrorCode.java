package com.springmsa.common.web.error;

import org.springframework.http.HttpStatusCode;

public interface MsaErrorCode {

    HttpStatusCode status();

    String code();

    String message();
}
