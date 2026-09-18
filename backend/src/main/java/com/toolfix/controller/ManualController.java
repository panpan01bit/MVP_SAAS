package com.toolfix.controller;

import com.toolfix.domain.Manual;
import com.toolfix.domain.Product;
import com.toolfix.dto.ApiResponse;
import com.toolfix.repository.ManualRepository;
import com.toolfix.repository.ProductRepository;
import com.toolfix.service.ManualTrainingService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/manuals")
@RequiredArgsConstructor
@Slf4j
public class ManualController {
    
    private final ManualRepository manualRepository;
    private final ProductRepository productRepository;
    private final ManualTrainingService manualTrainingService;
    
    @Value("${toolfix.storage.upload-dir}")
    private String uploadDir;
    
    @GetMapping
    public ApiResponse<List<Manual>> getAllManuals() {
        List<Manual> manuals = manualRepository.findAll();
        return ApiResponse.success(manuals);
    }
    
    @GetMapping("/{id}")
    public ApiResponse<Manual> getManual(@PathVariable Long id) {
        Manual manual = manualRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Manual not found"));
        return ApiResponse.success(manual);
    }
    
    @GetMapping("/product/{productId}")
    public ApiResponse<Manual> getManualByProduct(@PathVariable Long productId) {
        Manual manual = manualRepository.findByProductId(productId)
            .orElseThrow(() -> new RuntimeException("Manual not found for this product"));
        return ApiResponse.success(manual);
    }
    
    @PostMapping("/upload")
    public ApiResponse<Manual> uploadManual(
            @RequestParam("file") MultipartFile file,
            @RequestParam("productId") Long productId) {
        
        if (file.isEmpty()) {
            return ApiResponse.error("File is empty");
        }
        
        if (!file.getOriginalFilename().toLowerCase().endsWith(".pdf")) {
            return ApiResponse.error("Only PDF files are allowed");
        }
        
        if (file.getSize() > 50 * 1024 * 1024) {
            return ApiResponse.error("File size exceeds 50MB limit");
        }
        
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new RuntimeException("Product not found"));
        
        try {
            Files.createDirectories(Paths.get(uploadDir));
            
            String safeName = sanitizeFileName(file.getOriginalFilename());
            String fileName = UUID.randomUUID().toString() + "_" + safeName;
            Path filePath = Paths.get(uploadDir, fileName);
            file.transferTo(filePath.toFile());
            
            Manual manual = new Manual();
            manual.setProduct(product);
            manual.setOriginalFileName(file.getOriginalFilename());
            manual.setStoredFileName(filePath.toString());
            manual.setFileSize(file.getSize());
            manual.setStatus(Manual.ManualStatus.UNCONFIRMED);
            manual.setParseStatus("UPLOADED");
            manual.setParseProgress(0);
            manual.setParseMessage("已上传，等待 AI 训练...");
            
            manual.setExtractedSafetyWarnings("Processing...");
            manual.setExtractedWarrantyTerms("Processing...");
            
            manual = manualRepository.save(manual);
            
            product.setHasManual(true);
            productRepository.save(product);
            
            manualTrainingService.trainAsync(manual.getId());
            
            log.info("Manual uploaded for product: {}, file: {}, AI training started", productId, fileName);
            
            return ApiResponse.success("Manual uploaded, AI training in progress", manual);
            
        } catch (IOException e) {
            log.error("Failed to upload manual", e);
            return ApiResponse.error("Failed to upload file: " + e.getMessage());
        }
    }
    
    @PostMapping("/{id}/confirm")
    public ApiResponse<Manual> confirmManual(@PathVariable Long id, @RequestBody ConfirmManualRequest request) {
        Manual manual = manualRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Manual not found"));
        
        if (manual.getStatus() == Manual.ManualStatus.LOCKED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot modify locked manual");
        }
        
        if (request.getExtractedProductName() != null) {
            manual.setExtractedProductName(request.getExtractedProductName());
        }
        if (request.getExtractedModel() != null) {
            manual.setExtractedModel(request.getExtractedModel());
        }
        if (request.getExtractedBatteryInfo() != null) {
            manual.setExtractedBatteryInfo(request.getExtractedBatteryInfo());
        }
        if (request.getExtractedPowerInfo() != null) {
            manual.setExtractedPowerInfo(request.getExtractedPowerInfo());
        }
        if (request.getExtractedCompatibleBatteries() != null) {
            manual.setExtractedCompatibleBatteries(request.getExtractedCompatibleBatteries());
        }
        if (request.getExtractedComponentCodes() != null) {
            manual.setExtractedComponentCodes(request.getExtractedComponentCodes());
        }
        
        manual.setStatus(Manual.ManualStatus.LOCKED);
        
        manual = manualRepository.save(manual);
        
        log.info("Manual confirmed and locked: {}", id);
        
        return ApiResponse.success("Manual confirmed and locked successfully", manual);
    }
    
    @PutMapping("/{id}")
    public ApiResponse<Manual> updateManual(@PathVariable Long id, @RequestBody UpdateManualRequest request) {
        Manual manual = manualRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Manual not found"));
        
        if (manual.getStatus() == Manual.ManualStatus.LOCKED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "Cannot modify locked manual. Safety warnings and warranty terms are immutable.");
        }
        
        manual = manualRepository.save(manual);
        return ApiResponse.success("Manual updated successfully", manual);
    }
    
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteManual(@PathVariable Long id) {
        Manual manual = manualRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Manual not found"));
        
        if (manual.getStatus() == Manual.ManualStatus.LOCKED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot delete locked manual");
        }
        
        try {
            File file = new File(manual.getStoredFileName());
            if (file.exists()) {
                file.delete();
            }
        } catch (Exception e) {
            log.warn("Failed to delete file: {}", manual.getStoredFileName(), e);
        }
        
        Product product = manual.getProduct();
        product.setHasManual(false);
        productRepository.save(product);
        
        manualRepository.delete(manual);
        
        return ApiResponse.success("Manual deleted successfully", null);
    }
    
    /** 清洗上传文件名，防路径穿越 */
    private String sanitizeFileName(String original) {
        if (original == null || original.isBlank()) return "manual.pdf";
        String name = Paths.get(original).getFileName().toString();
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
    
    @Data
    public static class ConfirmManualRequest {
        private String extractedProductName;
        private String extractedModel;
        private String extractedBatteryInfo;
        private String extractedPowerInfo;
        private String extractedCompatibleBatteries;
        private String extractedComponentCodes;
    }
    
    @Data
    public static class UpdateManualRequest {
    }
}
