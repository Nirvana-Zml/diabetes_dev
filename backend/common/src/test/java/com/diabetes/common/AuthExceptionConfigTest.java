package com.diabetes.common;

import com.diabetes.common.api.ApiResponse;
import com.diabetes.common.auth.CurrentUserId;
import com.diabetes.common.auth.JwtAuthInterceptor;
import com.diabetes.common.auth.UserIdArgumentResolver;
import com.diabetes.common.config.CommonAutoConfiguration;
import com.diabetes.common.config.MinioAutoConfiguration;
import com.diabetes.common.exception.BusinessException;
import com.diabetes.common.exception.GlobalExceptionHandler;
import com.diabetes.common.storage.MinioProperties;
import com.diabetes.common.storage.MinioStorageService;
import com.diabetes.common.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthExceptionConfigTest {

    private static final String JWT_SECRET = "diabetes_jwt_secret_key_at_least_32_chars_test";

    @Test
    @DisplayName("JWT 拦截器校验 Authorization")
    void shouldHandleJwtAuthorization() {
        JwtAuthInterceptor interceptor = new JwtAuthInterceptor(JWT_SECRET);
        MockHttpServletRequest request = new MockHttpServletRequest();
        String token = JwtUtil.generateToken(JWT_SECRET, "u_001", "user", 60);
        request.addHeader("Authorization", "Bearer " + token);

        assertTrue(interceptor.preHandle(request, new MockHttpServletResponse(), new Object()));
        assertEquals("u_001", request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID));

        BusinessException missing = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(new MockHttpServletRequest(), new MockHttpServletResponse(), new Object()));
        assertEquals(401, missing.getCode());
        assertEquals("未登录或 Token 无效", missing.getMessage());

        MockHttpServletRequest basicRequest = new MockHttpServletRequest();
        basicRequest.addHeader("Authorization", "Basic token");
        BusinessException notBearer = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(basicRequest, new MockHttpServletResponse(), new Object()));
        assertEquals(401, notBearer.getCode());

        MockHttpServletRequest badTokenRequest = new MockHttpServletRequest();
        badTokenRequest.addHeader("Authorization", "Bearer bad-token");
        BusinessException invalid = assertThrows(BusinessException.class,
                () -> interceptor.preHandle(badTokenRequest, new MockHttpServletResponse(), new Object()));
        assertEquals(401, invalid.getCode());
        assertEquals("Token 已过期或无效", invalid.getMessage());
    }

    @Test
    @DisplayName("CurrentUserId 参数解析器读取请求属性")
    void shouldResolveCurrentUserId() throws Exception {
        UserIdArgumentResolver resolver = new UserIdArgumentResolver();
        Method method = Controller.class.getDeclaredMethod("handle", String.class, String.class);
        MethodParameter annotated = new MethodParameter(method, 0);
        MethodParameter plain = new MethodParameter(method, 1);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JwtAuthInterceptor.ATTR_USER_ID, "u_001");
        NativeWebRequest webRequest = new ServletWebRequest(request);

        assertTrue(resolver.supportsParameter(annotated));
        assertFalse(resolver.supportsParameter(plain));
        assertEquals("u_001", resolver.resolveArgument(annotated, null, webRequest, null));

        NativeWebRequest emptyWebRequest = mock(NativeWebRequest.class);
        when(emptyWebRequest.getNativeRequest(HttpServletRequest.class)).thenReturn(null);
        assertNull(resolver.resolveArgument(annotated, null, emptyWebRequest, null));
    }

    @Test
    @DisplayName("全局异常处理器转换响应状态")
    void shouldHandleGlobalExceptions() throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        assertBusinessStatus(handler, 400, HttpStatus.BAD_REQUEST);
        assertBusinessStatus(handler, 401, HttpStatus.UNAUTHORIZED);
        assertBusinessStatus(handler, 403, HttpStatus.FORBIDDEN);
        assertBusinessStatus(handler, 404, HttpStatus.NOT_FOUND);
        assertBusinessStatus(handler, 409, HttpStatus.CONFLICT);
        assertBusinessStatus(handler, 500, HttpStatus.INTERNAL_SERVER_ERROR);

        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "name", "不能为空"));
        MethodParameter parameter = new MethodParameter(Controller.class.getDeclaredMethod("validated", String.class), 0);
        ResponseEntity<ApiResponse<Void>> validation =
                handler.handleValidation(new MethodArgumentNotValidException(parameter, binding));
        assertEquals(HttpStatus.BAD_REQUEST, validation.getStatusCode());
        assertEquals("name: 不能为空", validation.getBody().message());

        ResponseEntity<ApiResponse<Void>> notFound = handler.handleNotFound(
                new NoResourceFoundException(HttpMethod.GET, "/missing"));
        assertEquals(HttpStatus.NOT_FOUND, notFound.getStatusCode());
        assertEquals("接口不存在: /missing", notFound.getBody().message());

        ResponseEntity<ApiResponse<Void>> other = handler.handleOther(new RuntimeException("boom"));
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, other.getStatusCode());
        assertEquals("boom", other.getBody().message());

        ResponseEntity<ApiResponse<Void>> otherWithoutMessage = handler.handleOther(new RuntimeException());
        assertEquals("服务器内部错误", otherWithoutMessage.getBody().message());
    }

    @Test
    @DisplayName("公共自动配置创建 Bean 并注册参数解析器")
    void shouldCreateCommonConfigurationBeans() {
        CommonAutoConfiguration config = new CommonAutoConfiguration();
        List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();

        UserIdArgumentResolver resolver = config.userIdArgumentResolver();
        ObjectMapper mapper = config.objectMapper();
        config.addArgumentResolvers(resolvers);

        assertNotNull(resolver);
        assertNotNull(mapper);
        assertEquals(1, resolvers.size());
        assertInstanceOf(UserIdArgumentResolver.class, resolvers.get(0));
    }

    @Test
    @DisplayName("MinIO 自动配置创建存储服务")
    void shouldCreateMinioStorageService() {
        MinioProperties properties = new MinioProperties();
        properties.setEndpoint("http://localhost:9000/");

        MinioStorageService service = new MinioAutoConfiguration().minioStorageService(properties);

        assertNotNull(service);
    }

    private static void assertBusinessStatus(GlobalExceptionHandler handler, int code, HttpStatus status) {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusiness(new BusinessException(code, "message"));

        assertEquals(status, response.getStatusCode());
        assertEquals(code, response.getBody().code());
        assertEquals("message", response.getBody().message());
    }

    static class Controller {
        @SuppressWarnings("unused")
        void handle(@CurrentUserId String userId, String plain) {
        }

        @SuppressWarnings("unused")
        void validated(String name) {
        }
    }
}
