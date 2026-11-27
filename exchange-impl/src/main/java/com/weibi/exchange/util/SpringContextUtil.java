package com.weibi.exchange.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

@Component
public class SpringContextUtil implements ApplicationContextAware {
	public static ApplicationContext context;

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		context = applicationContext;
	}

	public static ApplicationContext getApplicationContext() {
		return context;
	}

	public static <T> T getBean(Class<T> clz) {
		return context.getBean(clz);
	}

	public static Object getBean(String string) {
		return context.getBean(string);
	}

	public static String[] getBeanNamesForType(@Nullable Class<?> type) {
		return context.getBeanNamesForType(type);
	}
}
