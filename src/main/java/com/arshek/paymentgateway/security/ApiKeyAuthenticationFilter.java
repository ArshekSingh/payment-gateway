package com.arshek.paymentgateway.security;

import com.arshek.paymentgateway.config.GatewayProperties;
import com.arshek.paymentgateway.service.MerchantService;
import com.arshek.paymentgateway.util.Crypto;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates API calls without sessions:
 * <ul>
 *   <li>{@code /v1/**}: {@code Authorization: Bearer <merchant api key>}</li>
 *   <li>{@code /admin/**}: {@code X-Admin-Key: <configured admin key>}</li>
 * </ul>
 * Missing or bad credentials leave the context empty so the security chain answers 401.
 * Instantiated by the security config (not a bean) so it is not also registered as a servlet filter.
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    static final String ADMIN_HEADER = "X-Admin-Key";
    private static final String BEARER_PREFIX = "Bearer ";

    private final MerchantService merchantService;
    private final String adminApiKey;

    public ApiKeyAuthenticationFilter(MerchantService merchantService, GatewayProperties properties) {
        this.merchantService = merchantService;
        this.adminApiKey = properties.adminApiKey();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (path.startsWith("/admin/")) {
            authenticateAdmin(request);
        } else if (path.startsWith("/v1/")) {
            authenticateMerchant(request);
        }
        chain.doFilter(request, response);
    }

    private void authenticateAdmin(HttpServletRequest request) {
        String provided = request.getHeader(ADMIN_HEADER);
        if (adminApiKey != null && !adminApiKey.isBlank() && Crypto.constantTimeEquals(adminApiKey, provided)) {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "admin", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        }
    }

    private void authenticateMerchant(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return;
        }
        String apiKey = header.substring(BEARER_PREFIX.length()).trim();
        if (apiKey.isEmpty()) {
            return;
        }
        merchantService.authenticate(apiKey).ifPresent(principal ->
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_MERCHANT")))));
    }
}
