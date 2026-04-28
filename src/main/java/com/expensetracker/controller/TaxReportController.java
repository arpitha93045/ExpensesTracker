package com.expensetracker.controller;

import com.expensetracker.dto.response.TaxReportResponse;
import com.expensetracker.exception.ResourceNotFoundException;
import com.expensetracker.repository.UserRepository;
import com.expensetracker.service.TaxReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/tax-report")
@RequiredArgsConstructor
@Tag(name = "Tax Report", description = "Tax deduction report export APIs")
public class TaxReportController {

    private final TaxReportService taxReportService;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "Get tax deduction summary for a given year")
    public TaxReportResponse getSummary(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Integer year) {
        int taxYear = year != null ? year : LocalDate.now().getYear();
        return taxReportService.buildReport(resolveUserId(userDetails), taxYear);
    }

    @GetMapping("/export/pdf")
    @Operation(summary = "Export tax deduction report as PDF")
    public void exportPdf(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Integer year,
            HttpServletResponse response) throws IOException {

        int taxYear = year != null ? year : LocalDate.now().getYear();
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"tax-report-" + taxYear + ".pdf\"");
        taxReportService.exportPdf(resolveUserId(userDetails), taxYear, response.getOutputStream());
    }

    @GetMapping("/export/excel")
    @Operation(summary = "Export tax deduction report as Excel (.xlsx)")
    public void exportExcel(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(required = false) Integer year,
            HttpServletResponse response) throws IOException {

        int taxYear = year != null ? year : LocalDate.now().getYear();
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition",
                "attachment; filename=\"tax-report-" + taxYear + ".xlsx\"");
        taxReportService.exportExcel(resolveUserId(userDetails), taxYear, response.getOutputStream());
    }

    private UUID resolveUserId(UserDetails userDetails) {
        return userRepository.findByEmail(userDetails.getUsername())
                .map(u -> u.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
