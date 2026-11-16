package com.poly.sof309.controllers;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.controllers.DangNhapServletTestHarness;
import org.example.utils.RateLimitUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * [Y3] Unit Test cho DangNhapServlet (/dangnhap), dựa theo TC_AUTH_* trong docs/TEST_CASES.md.
 *
 * Chỉ test các nhánh KHÔNG cần CSDL thật:
 *  - doGet: 2 thông điệp dựng sẵn theo query string (?registered=1, ?error=account_blocked).
 *  - doPost: 2 cổng rate-limit (theo IP và theo tài khoản) — RateLimitUtil là bộ đếm trong bộ
 *    nhớ (không DB), nên ta có thể "khoá trước" 1 key bất kỳ rồi gọi servlet để xác nhận servlet
 *    dừng lại đúng ở cổng rate-limit, KHÔNG đi tiếp tới AccountDAO.DangNhap(...) (vốn cần DB).
 */
class DangNhapServletTest {

    private static final AtomicInteger IP_COUNTER = new AtomicInteger(1000);

    private DangNhapServletTestHarness servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private RequestDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        servlet = new DangNhapServletTestHarness();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher(anyString())).thenReturn(dispatcher);
    }

    private String nextFakeIp() {
        return "198.51.100." + IP_COUNTER.incrementAndGet();
    }

    // ======================================================================
    // doGet — thông điệp dựng sẵn theo query string, không cần DB
    // ======================================================================
    @Test
    @DisplayName("TC_AUTH_GET_01: ?registered=1 hiển thị thông báo đăng ký thành công")
    void testDoGet_RegisteredParam_ShowsSuccessMessage() throws Exception {
        when(request.getParameter("registered")).thenReturn("1");

        servlet.callDoGet(request, response);

        verify(request).setAttribute(eq("thongbao"), contains("Đăng ký thành công"));
        verify(dispatcher).forward(request, response);
    }

    @Test
    @DisplayName("TC_AUTH_GET_02: ?error=account_blocked hiển thị thông báo tài khoản bị khoá")
    void testDoGet_AccountBlockedError_ShowsBlockedMessage() throws Exception {
        when(request.getParameter("error")).thenReturn("account_blocked");

        servlet.callDoGet(request, response);

        verify(request).setAttribute(eq("loi"), contains("đã bị khoá"));
    }

    @Test
    @DisplayName("TC_AUTH_GET_03: Không có query string nào thì không hiển thị thông báo nào")
    void testDoGet_NoParams_NoMessages() throws Exception {
        servlet.callDoGet(request, response);

        verify(request, never()).setAttribute(eq("thongbao"), any());
        verify(request, never()).setAttribute(eq("loi"), any());
        verify(dispatcher).forward(request, response);
    }

    // ======================================================================
    // doPost — cổng rate-limit theo IP (TC_AUTH_03)
    // ======================================================================
    @Test
    @DisplayName("TC_AUTH_03: IP đã bị khoá do đăng nhập sai quá nhiều lần -> chặn ngay, không gọi DB")
    void testDoPost_IpRateLimited_BlocksLoginAttempt() throws Exception {
        String fakeIp = nextFakeIp();
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(fakeIp);
        // Khoá trước bucket rate-limit theo IP (độc lập với ngưỡng MAX_ATTEMPTS thật của servlet).
        RateLimitUtil.recordFailure("login:" + fakeIp, 1, 60_000L, 60_000L);

        when(request.getParameter("username")).thenReturn("bất_kỳ_user_nào");
        when(request.getParameter("password")).thenReturn("bat-ky-password-nao");

        servlet.callDoPost(request, response);

        verify(request).setAttribute(eq("loi"), contains("sai quá nhiều lần"));
    }

    // ======================================================================
    // doPost — cổng rate-limit theo tài khoản, có chuẩn hoá hoa/thường + khoảng trắng
    // (TC_AUTH_03 mở rộng): username gửi lên dù viết hoa/có khoảng trắng thừa vẫn phải khớp
    // đúng bucket đã khoá của tài khoản (so khớp theo username.trim().toLowerCase()).
    // ======================================================================
    @Test
    @DisplayName("TC_AUTH_03b: Tài khoản bị khoá vẫn nhận diện đúng dù username gửi lên khác hoa/thường")
    void testDoPost_AccountRateLimited_NormalizesUsernameCase() throws Exception {
        String fakeIp = nextFakeIp();
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(fakeIp);
        RateLimitUtil.recordFailure("login-account:testuser", 1, 60_000L, 60_000L);

        when(request.getParameter("username")).thenReturn("  TestUser  ");
        when(request.getParameter("password")).thenReturn("khong-quan-trong");

        servlet.callDoPost(request, response);

        verify(request).setAttribute(eq("loi"), contains("sai quá nhiều lần"));
    }
}
