package com.poly.sof309.controllers;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.controllers.DangKyServletTestHarness;
import org.example.utils.RateLimitUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * [Y3] Unit Test cho DangKyServlet (/dangky), dựa theo TC_REG_* trong docs/TEST_CASES.md.
 *
 * Chỉ test các nhánh validate chạy TRƯỚC khi chạm DB (password, họ tên/SĐT, username, rate-limit
 * gửi OTP) bằng cách giả lập HttpServletRequest/Response với Mockito — không cần CSDL thật, test
 * chạy nhanh và ổn định bất kể máy chấm có kết nối được tới SQL Server hay không.
 *
 * Mẹo cô lập: khi một trường cần test (password hoặc username) đã hợp lệ, ta cố tình giữ trường
 * CÒN LẠI ở trạng thái không hợp lệ/đã bị khoá rate-limit để servlet dừng lại TRƯỚC khi gọi
 * AccountDAO (tránh phụ thuộc DB trong unit test).
 */
class DangKyServletTest {

    private static final AtomicInteger IP_COUNTER = new AtomicInteger(1);

    private DangKyServletTestHarness servlet;
    private HttpServletRequest request;
    private HttpServletResponse response;

    @BeforeEach
    void setUp() {
        servlet = new DangKyServletTestHarness();
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        RequestDispatcher dispatcher = mock(RequestDispatcher.class);
        when(request.getRequestDispatcher(anyString())).thenReturn(dispatcher);
    }

    /** IP giả duy nhất cho mỗi lần gọi, tránh đụng chung bucket rate-limit tĩnh giữa các test. */
    private String nextFakeIp() {
        return "203.0.113." + IP_COUNTER.incrementAndGet();
    }

    private void stubClientIp(String fakeIp) {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn(fakeIp);
    }

    private String captureLoi() {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(request, atLeastOnce()).setAttribute(eq("loi"), captor.capture());
        return captor.getValue();
    }

    // ======================================================================
    // NHÓM A — BIÊN MẬT KHẨU (8-16 ký tự, không khoảng trắng)
    // username cố định "ab" (chưa đủ 3 ký tự) để làm "lính canh": nếu mật khẩu vượt qua hết các
    // kiểm tra của chính nó, servlet sẽ dừng lại ở bước kiểm tra username (không chạm DB) và báo
    // lỗi username — nhờ đó ta biết chắc mật khẩu đã PASS mà không cần gọi AccountDAO thật.
    // ======================================================================
    @ParameterizedTest(name = "[{index}] password=\"{0}\" -> chứa \"{1}\"")
    @DisplayName("TC_REG_02..06: Biên độ dài/khoảng trắng của mật khẩu")
    @CsvSource({
            "'',trống",
            "'       ',trống",
            "1234567,8 đến 16",
            "12345678,3–30 ký tự",
            "1234567890123456,3–30 ký tự",
            "12345678901234567,8 đến 16",
            "'1234 678',khoảng trắng"
    })
    void testPasswordBoundary(String password, String expectedSubstring) {
        when(request.getParameter("username")).thenReturn("ab");
        when(request.getParameter("password")).thenReturn(password);
        when(request.getParameter("confirm_password")).thenReturn(password);
        when(request.getParameter("fullname")).thenReturn("Nguyen Van A");
        when(request.getParameter("phone")).thenReturn("0901234567");
        when(request.getParameter("email")).thenReturn("boundary@example.com");

        assertDoesNotThrowAndForward(() -> servlet.callDoPost(request, response));

        String loi = captureLoi();
        assertTrue(loi.contains(expectedSubstring),
                "Mong đợi thông báo chứa \"" + expectedSubstring + "\" nhưng thực tế là: " + loi);
    }

    @Test
    @DisplayName("TC_REG_06b: Mật khẩu và xác nhận mật khẩu không khớp")
    void testPasswordConfirmMismatch() throws Exception {
        when(request.getParameter("username")).thenReturn("ab");
        when(request.getParameter("password")).thenReturn("ValidPass1");
        when(request.getParameter("confirm_password")).thenReturn("ValidPass2");
        when(request.getParameter("fullname")).thenReturn("Nguyen Van A");
        when(request.getParameter("phone")).thenReturn("0901234567");

        servlet.callDoPost(request, response);

        assertTrue(captureLoi().contains("không khớp"));
    }

    // ======================================================================
    // NHÓM B — BIÊN TÊN ĐĂNG NHẬP (3-30 ký tự, chỉ a-z A-Z 0-9 _)
    // password cố định hợp lệ. Để tránh gọi DB thật khi username hợp lệ (servlet sẽ đi tiếp tới
    // bước rate-limit rồi tới AccountDAO), ta CHỦ ĐỘNG khoá sẵn rate-limit gửi OTP cho IP giả của
    // từng lần gọi TRƯỚC khi gọi servlet — nhờ vậy dù username đúng định dạng, servlet vẫn dừng ở
    // bước rate-limit (không chạm DB) và báo đúng 1 thông điệp để ta nhận biết "username đã PASS".
    // ======================================================================
    @ParameterizedTest(name = "[{index}] username=\"{0}\" -> chứa \"{1}\"")
    @DisplayName("TC_REG_07..08: Biên độ dài/ký tự hợp lệ của username")
    @CsvSource({
            "ab,3–30 ký tự",
            "abc,quá nhiều lần",
            "abcdefghij1234567890123456789a,quá nhiều lần",
            "abcdefghij1234567890123456789ab,3–30 ký tự",
            "'abc def',3–30 ký tự",
            "abc-def,3–30 ký tự",
            "abc_123,quá nhiều lần"
    })
    void testUsernameBoundary(String username, String expectedSubstring) throws Exception {
        String fakeIp = nextFakeIp();
        stubClientIp(fakeIp);
        // Khoá sẵn bucket rate-limit gửi OTP cho IP này (vô hiệu hoá trước khi servlet kịp chạy),
        // để nếu username hợp lệ thì servlet dừng lại ở đây thay vì gọi AccountDAO thật.
        RateLimitUtil.recordFailure("regotp:" + fakeIp, 1, 60_000L, 60_000L);

        when(request.getParameter("username")).thenReturn(username);
        when(request.getParameter("password")).thenReturn("ValidPass1");
        when(request.getParameter("confirm_password")).thenReturn("ValidPass1");
        when(request.getParameter("fullname")).thenReturn("Nguyen Van A");
        when(request.getParameter("phone")).thenReturn("0901234567");

        servlet.callDoPost(request, response);

        String loi = captureLoi();
        assertTrue(loi.contains(expectedSubstring),
                "Mong đợi thông báo chứa \"" + expectedSubstring + "\" nhưng thực tế là: " + loi);
    }

    // ======================================================================
    // [Đã sửa lỗi] Thiếu tham số "password" (form submit thiếu field) từng khiến servlet ném
    // NullPointerException thay vì báo lỗi nghiệp vụ "Mật khẩu không được để trống!" như trường
    // hợp password="" — đã vá tại DangKyServlet.java ("password == null ? "" : password.trim()").
    // Test này xác nhận hành vi AN TOÀN sau khi sửa: không còn ném ngoại lệ, trả đúng thông báo.
    // ======================================================================
    @Test
    @DisplayName("TC_REG_09: Thiếu tham số password (null) trả về lỗi nghiệp vụ, không còn crash")
    void testMissingPasswordParamIsHandledGracefully() {
        when(request.getParameter("username")).thenReturn("ab");
        when(request.getParameter("password")).thenReturn(null);
        when(request.getParameter("confirm_password")).thenReturn("whatever");

        assertDoesNotThrowAndForward(() -> servlet.callDoPost(request, response));

        assertTrue(captureLoi().contains("trống"));
    }

    // ======================================================================
    // assertThrows — RateLimitUtil.getClientIp(...) là tiện ích chống brute-force mà CẢ HAI
    // controller (DangKyServlet, DangNhapServlet) đều gọi ngay những dòng đầu của doPost(). Nếu
    // bị gọi với request null (vd lỗi cấu hình filter chain phía trên), nó phải ném
    // NullPointerException ngay thay vì âm thầm NullPointerException ở một chỗ khó debug hơn.
    // ======================================================================
    @Test
    @DisplayName("[Business rule] RateLimitUtil.getClientIp(null) phải ném NullPointerException")
    void testGetClientIpWithNullRequestThrowsNpe() {
        assertThrows(NullPointerException.class, () -> RateLimitUtil.getClientIp(null));
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private void assertDoesNotThrowAndForward(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Exception e) {
            throw new AssertionError("Không mong đợi ngoại lệ ở nhánh validate: " + e, e);
        }
    }
}
