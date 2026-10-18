package com.arshek.paymentgateway.web;

import com.arshek.paymentgateway.dto.CreateMerchantRequest;
import com.arshek.paymentgateway.dto.MerchantCreatedResponse;
import com.arshek.paymentgateway.dto.MerchantResponse;
import com.arshek.paymentgateway.dto.PageResponse;
import com.arshek.paymentgateway.service.MerchantService;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/merchants")
@RequiredArgsConstructor
public class AdminMerchantController {

    private final MerchantService merchantService;

    @PostMapping
    public ResponseEntity<MerchantCreatedResponse> create(@Valid @RequestBody CreateMerchantRequest request) {
        MerchantCreatedResponse created = merchantService.create(request);
        return ResponseEntity.created(URI.create("/admin/merchants/" + created.id())).body(created);
    }

    @GetMapping
    public PageResponse<MerchantResponse> list(@RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        return merchantService.list(PageRequests.newestFirst(page, size));
    }
}
