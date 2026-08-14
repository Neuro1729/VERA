package com.example.entitlements.request;

import com.example.entitlements.domain.Resource;

import java.util.List;

public record ResourcesSyncInput(
        SyncMode mode,
        List<Resource> resources
) {}
