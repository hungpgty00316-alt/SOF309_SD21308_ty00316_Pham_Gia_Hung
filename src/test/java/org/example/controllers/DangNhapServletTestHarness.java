package org.example.controllers;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * [Y3] Lớp hỗ trợ chỉ dùng trong test: xem giải thích ở DangKyServletTestHarness.java.
 */
public class DangNhapServletTestHarness extends DangNhapServlet {
    public void callDoGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }

    public void callDoPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
    }
}
