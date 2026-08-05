package com.springmsa.memberbff.stockbff;

import com.springmsa.common.web.response.MsaResponse;
import com.springmsa.memberbff.stockbff.controller.StockBffController;
import com.springmsa.memberbff.stockbff.dto.CandleResponse;
import com.springmsa.memberbff.stockbff.dto.DataStatus;
import com.springmsa.memberbff.stockbff.dto.MarketWorkspaceResponse;
import com.springmsa.memberbff.stockbff.service.StockBffService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockBffControllerTest {

    private final StockBffService service = mock(StockBffService.class);
    private final Authentication authentication = mock(Authentication.class);
    private final HttpServletRequest request = new MockHttpServletRequest();
    private final HttpServletResponse response = new MockHttpServletResponse();

    private StockBffController controller;

    @BeforeEach
    void setUp() {
        controller = new StockBffController(service);
    }

    @Test
    void marketWorkspaceWrapsServiceResponse() {
        MarketWorkspaceResponse workspace = new MarketWorkspaceResponse(
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
        when(service.getMarketWorkspace(authentication, request, response, "005930,AAPL")).thenReturn(workspace);

        ResponseEntity<MsaResponse<MarketWorkspaceResponse>> responseEntity = controller.marketWorkspace(
                authentication,
                request,
                response,
                "005930,AAPL"
        );

        assertThat(responseEntity.getStatusCode().value()).isEqualTo(200);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().data()).isEqualTo(workspace);
        verify(service).getMarketWorkspace(authentication, request, response, "005930,AAPL");
    }

    @Test
    void marketCandlesWrapServiceResponse() {
        List<CandleResponse> candles = List.of(new CandleResponse(
                "2026-07-12T09:00:00+09:00", "71000", "73000", "70500", "72000", "1000", "KRW",
                Instant.parse("2026-07-12T10:15:30Z"), DataStatus.FRESH
        ));
        when(service.getMarketCandles(authentication, request, response, "005930", "1d", 30)).thenReturn(candles);

        ResponseEntity<MsaResponse<List<CandleResponse>>> responseEntity = controller.marketCandles(
                authentication, request, response, "005930", "1d", 30
        );

        assertThat(responseEntity.getStatusCode().value()).isEqualTo(200);
        assertThat(responseEntity.getBody()).isNotNull();
        assertThat(responseEntity.getBody().data()).containsExactlyElementsOf(candles);
        verify(service).getMarketCandles(authentication, request, response, "005930", "1d", 30);
    }
}
