package com.pranicdoc.cph.admin;

import com.pranicdoc.cph.common.cache.CacheRefreshResult;
import com.pranicdoc.cph.common.cache.CacheRefreshService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Internal cache maintenance. Super admins only: no RBAC permission opens it. */
@RestController
@RequestMapping("/api/v1/admin/cache")
@RequiredArgsConstructor
public class AdminCacheController {

    private final CacheRefreshService cacheRefreshService;
    private final AdminAuthz adminAuthz;

    @GetMapping
    public ResponseEntity<List<String>> cacheNames() {
        adminAuthz.assertSuperAdmin();
        return ResponseEntity.ok(cacheRefreshService.cacheNames());
    }

    /** No params: every cache. cacheName: that cache. key: that key in every cache. Both: one entry. */
    @PostMapping("/refresh")
    public ResponseEntity<CacheRefreshResult> refresh(@RequestParam(required = false) String cacheName,
                                                      @RequestParam(required = false) String key) {
        adminAuthz.assertSuperAdmin();
        return ResponseEntity.ok(cacheRefreshService.refresh(cacheName, key));
    }
}
