package com.weibi.exchange.filter;

import com.weibi.exchange.util.HttpContextUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class CharacterEncodingFilter implements Filter {

	@Override
	public void init(FilterConfig filterConfig) throws ServletException {

	}

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
			throws IOException, ServletException {
		HttpServletResponse httpServletResponse = (HttpServletResponse) response;
		HttpServletRequest httpServletRequest = (HttpServletRequest) request;
		httpServletResponse.setContentType("application/json;charset=utf-8");
		httpServletResponse.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
		httpServletResponse.setHeader("Access-Control-Allow-Headers", "Accept,Authorization,Cache-Control,Content-Type,DNT,If-Modified-Since,Keep-Alive,Origin,User-Agent,X-Requested-With,token,x-access-token");
		httpServletResponse.setHeader("Access-Control-Allow-Credentials", "true");
		httpServletResponse.setHeader("Access-Control-Allow-Origin", HttpContextUtils.getOrigin());
		httpServletResponse.setHeader("Access-Control-Max-Age", "1728000");
		String requestURL = httpServletRequest.getRequestURL().toString();
		Long startTime = System.currentTimeMillis();
		try {
			chain.doFilter(request, response);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		} finally {
			Long endTime = System.currentTimeMillis() - startTime;
		}
	}

	@Override
	public void destroy() {

	}

}
