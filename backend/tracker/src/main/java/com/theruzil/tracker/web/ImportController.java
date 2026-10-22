package com.theruzil.tracker.web;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.theruzil.tracker.importing.ImportService;
import com.theruzil.tracker.importing.InvalidImportDataException;
import com.theruzil.tracker.importing.dto.ImportResultResponse;

@RestController
@RequestMapping("/imports")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ImportResultResponse importCsv(@RequestAttribute(UserIdFilter.USER_ID_ATTRIBUTE) Long userId,
            @RequestParam("file") MultipartFile file, @RequestParam("year") Integer year,
            @RequestParam("month") Integer month) {
        if (file == null || file.isEmpty()) {
            throw new InvalidImportDataException("Файл не загружен");
        }
        try {
            return importService.importCsv(userId, year, month, file.getInputStream());
        }
        catch (IOException ex) {
            throw new InvalidImportDataException("Не удалось прочитать файл");
        }
    }
}
