package com.example.products.service;

import com.example.products.model.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class BulkImportServiceImpl implements BulkImportService {

    private static final Pattern URL_PATTERN = Pattern.compile("https?://.*");

    private final ProductService productService;
    private final ProductImageService productImageService;

    @Override
    public ImportResult importProducts(MultipartFile file) throws IOException {
        List<ImportRowResult> rows = new ArrayList<>();
        int rowNumber = 0;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                rowNumber++;
                rows.add(processRow(rowNumber, line));
            }
        }

        int successCount = (int) rows.stream().filter(r -> r.getStatus() == RowStatus.SUCCESS).count();
        int failedCount = rows.size() - successCount;

        return ImportResult.builder()
                .totalRows(rows.size())
                .successCount(successCount)
                .failedCount(failedCount)
                .rows(rows)
                .build();
    }

    private ImportRowResult processRow(int rowNumber, String line) {
        String[] cols = line.split(",", -1);
        if (cols.length != 4) {
            return failed(rowNumber, "Expected 4 columns but found " + cols.length);
        }

        String name = cols[0].trim();
        String description = cols[1].trim();
        String priceStr = cols[2].trim();
        String url = cols[3].trim();

        if (name.isBlank()) {
            return failed(rowNumber, "Name must not be blank");
        }

        BigDecimal price = null;
        if (!priceStr.isEmpty() && !priceStr.equalsIgnoreCase("null")) {
            try {
                price = new BigDecimal(priceStr);
            } catch (NumberFormatException e) {
                return failed(rowNumber, "Price is not a valid decimal number: " + priceStr);
            }
            if (price.compareTo(new BigDecimal("0.01")) < 0) {
                return failed(rowNumber, "Price must be >= 0.01 but was: " + priceStr);
            }
        }

        if (!URL_PATTERN.matcher(url).matches()) {
            return failed(rowNumber, "URL must match https?://.* but was: " + url);
        }

        ProductRequest productRequest = ProductRequest.builder()
                .name(name)
                .description(description.isEmpty() ? null : description)
                .price(price)
                .build();

        ProductResponse productResponse;
        try {
            productResponse = productService.create(productRequest);
        } catch (Exception e) {
            return failed(rowNumber, "Failed to create product: " + e.getMessage());
        }

        try {
            productImageService.addImages(productResponse.getId(), new ImageRequest(List.of(url)));
        } catch (Exception e) {
            return failed(rowNumber, "Failed to add image: " + e.getMessage());
        }

        return ImportRowResult.builder()
                .rowNumber(rowNumber)
                .status(RowStatus.SUCCESS)
                .productId(productResponse.getId())
                .build();
    }

    private ImportRowResult failed(int rowNumber, String message) {
        return ImportRowResult.builder()
                .rowNumber(rowNumber)
                .status(RowStatus.FAILED)
                .errorMessage(message)
                .build();
    }
}
