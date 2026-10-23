package com.chefcontrol.api.plan;

import com.chefcontrol.application.service.AccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Aplica {@link RequiresFeature}. La anotación del método gana sobre la del controller.
 * ponytail: una consulta de la cuenta por request gateado; si pesa, el plan puede viajar en el JWT
 * (pero entonces un pago recién acreditado no se ve hasta renovar la sesión).
 */
@Component
@RequiredArgsConstructor
public class PlanFeatureInterceptor implements HandlerInterceptor {

    private final AccountService accountService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method) {
            RequiresFeature required = method.getMethodAnnotation(RequiresFeature.class);
            if (required == null) required = method.getBeanType().getAnnotation(RequiresFeature.class);
            if (required != null) accountService.requireFeature(required.value());
        }
        return true;
    }
}
