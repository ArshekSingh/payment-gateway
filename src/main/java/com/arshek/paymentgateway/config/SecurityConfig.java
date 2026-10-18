package com.arshek.paymentgateway.config;

import com.arshek.paymentgateway.security.ApiKeyAuthenticationFilter;
import com.arshek.paymentgateway.security.RestSecurityErrorHandlers;
import com.arshek.paymentgateway.service.MerchantService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, MerchantService merchantService,
                                                   GatewayProperties properties,
                                                   RestSecurityErrorHandlers errorHandlers) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // stateless API authenticated by bearer/API key, no cookies
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .requestMatchers("/v1/**").hasRole("MERCHANT")
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errorHandlers)
                        .accessDeniedHandler(errorHandlers))
                .addFilterBefore(new ApiKeyAuthenticationFilter(merchantService, properties),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
