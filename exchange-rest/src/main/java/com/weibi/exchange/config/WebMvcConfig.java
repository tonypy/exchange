package com.weibi.exchange.config;

import com.weibi.exchange.filter.CharacterEncodingFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
	@Autowired
	private MappingJackson2HttpMessageConverter httpMessageConverter;

	@Bean
	public FilterRegistrationBean<CharacterEncodingFilter> filterRegistrationBean() {
		FilterRegistrationBean<CharacterEncodingFilter> filterRegistrationBean = new FilterRegistrationBean<>();
		filterRegistrationBean.setFilter(new CharacterEncodingFilter());
		filterRegistrationBean.addUrlPatterns("/*");
		filterRegistrationBean.setName("characterEncodingFilter11");
		return filterRegistrationBean;
	}

}
