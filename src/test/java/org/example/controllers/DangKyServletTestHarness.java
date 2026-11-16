package org.example.controllers;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * [Y3] Lớp hỗ trợ chỉ dùng trong test: doPost() của DangKyServlet là protected (chuẩn Servlet
 * API), class này nằm CÙNG package org.example.controllers nên gọi được trực tiếp, rồi lộ ra
 * 1 hàm public để test ở package khác (com.poly.sof309.controllers) có thể gọi tới. Không nằm
 * trong src/main nên không đóng gói vào WAR.
 */
public class DangKyServletTestHarness extends DangKyServlet {
    public void callDoPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }
}
