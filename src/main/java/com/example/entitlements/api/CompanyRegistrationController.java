package com.example.entitlements.api;

import com.example.entitlements.domain.Tenant;
import com.example.entitlements.request.CompanyRegistrationRequest;
import com.example.entitlements.request.RegistrationPreview;
import com.example.entitlements.service.CompanyRegistrationService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/company-registration")
public class CompanyRegistrationController {
    private final CompanyRegistrationService companyRegistrationService;

    public CompanyRegistrationController(CompanyRegistrationService companyRegistrationService) {
        this.companyRegistrationService = companyRegistrationService;
    }

    @PostMapping("/preview")
    public RegistrationPreview preview(@RequestBody CompanyRegistrationRequest request) {
        return companyRegistrationService.preview(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Tenant register(@RequestBody CompanyRegistrationRequest request) {
        return companyRegistrationService.register(request);
    }
}
