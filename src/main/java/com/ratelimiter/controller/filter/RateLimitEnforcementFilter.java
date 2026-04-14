package com.ratelimiter.controller.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ratelimiter.dto.RequestDecisionDto;
import com.ratelimiter.dto.RequestDto;
import com.ratelimiter.service.RateLimiterService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitEnforcementFilter extends OncePerRequestFilter {

    private static final String REQUEST_PATH = "/request";

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    public RateLimitEnforcementFilter(RateLimiterService rateLimiterService, ObjectMapper objectMapper) {
        this.rateLimiterService = rateLimiterService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String pathWithinApplication = request.getRequestURI().substring(request.getContextPath().length());
        return !HttpMethod.POST.matches(request.getMethod()) || !REQUEST_PATH.equals(pathWithinApplication);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);
        RequestDto requestDto = readRequest(wrappedRequest);
        if (requestDto == null || isBlank(requestDto.userId()) || isBlank(requestDto.endpoint())) {
            filterChain.doFilter(wrappedRequest, response);
            return;
        }

        boolean allowed = rateLimiterService.allowRequest(requestDto.userId(), requestDto.endpoint());
        if (!allowed) {
            writeRejectedResponse(response, requestDto);
            return;
        }

        filterChain.doFilter(wrappedRequest, response);
    }

    private RequestDto readRequest(CachedBodyHttpServletRequest request) throws IOException {
        try {
            return objectMapper.readValue(request.getInputStream(), RequestDto.class);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private void writeRejectedResponse(HttpServletResponse response, RequestDto requestDto) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        RequestDecisionDto body = new RequestDecisionDto(false, requestDto.userId(), requestDto.endpoint());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
