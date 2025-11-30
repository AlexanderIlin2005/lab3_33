package org.itmo.controller;

import org.itmo.dto.ImportHistoryResponseDto;
import org.itmo.model.ImportHistory;
import org.itmo.model.User;
import org.itmo.service.ImportHistoryService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;



import org.itmo.model.enums.UserRole;

import io.minio.MinioClient;
import io.minio.GetObjectArgs;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;


import java.util.List;


@RestController
@RequestMapping("/api/import-history")
public class ImportHistoryController {

    private final ImportHistoryService historyService;


    private final MinioClient minioClient;
    private final String minioBucketName;



    public ImportHistoryController(ImportHistoryService historyService,
                                   MinioClient minioClient, // <-- Новое
                                   String minioBucketName) { // <-- Новое
        this.historyService = historyService;
        this.minioClient = minioClient;
        this.minioBucketName = minioBucketName;
    }

    @GetMapping
    public ResponseEntity<List<ImportHistoryResponseDto>> getHistory(@AuthenticationPrincipal User currentUser) {

        
        boolean isAdmin = currentUser != null && currentUser.getRole() == UserRole.ADMIN;

        List<ImportHistoryResponseDto> history = historyService.getImportHistory(currentUser, isAdmin);

        return ResponseEntity.ok(history);
    }


    @GetMapping("/{id}/download")
    public void downloadImportFile(@PathVariable Long id, HttpServletResponse response) throws Exception {
        ImportHistory history = historyService.findById(id);
        if (history.getFileName() == null || history.getFileName().isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }


        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + history.getFileName() + "\"");
        response.setContentType("application/xml");
        response.setCharacterEncoding("UTF-8");


        try (InputStream inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioBucketName)
                        .object(history.getFileName())
                        .build())) {

            OutputStream outputStream = response.getOutputStream();
            inputStream.transferTo(outputStream);
            outputStream.flush();
        }
    }
}