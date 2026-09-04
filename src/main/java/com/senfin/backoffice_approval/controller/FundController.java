package com.senfin.backoffice_approval.controller;

import com.senfin.backoffice_approval.dto.FundDto;
import com.senfin.backoffice_approval.repository.FundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only listing of the 10 predefined Senfin funds.
 */
@RestController
@RequestMapping("/api/funds")
@RequiredArgsConstructor
public class FundController {

    private final FundRepository fundRepository;

    @GetMapping
    public List<FundDto> getAllFunds() {
        return fundRepository.findAll().stream()
                .map(f -> new FundDto(f.getId(), f.getName(), f.getSlug(), f.getUrl()))
                .toList();
    }
}
