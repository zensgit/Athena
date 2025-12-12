package com.ecm.core.config;

import com.ecm.core.integration.webdav.WebDavResourceFactory;
import io.milton.config.HttpManagerBuilder;
import io.milton.http.HttpManager;
import io.milton.servlet.MiltonFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration to embed Milton WebDAV server.
 */
@Configuration
public class WebDavConfig {

    @Autowired
    private WebDavResourceFactory resourceFactory;

    @Bean
    public HttpManager httpManager() {
        HttpManagerBuilder builder = new HttpManagerBuilder();
        builder.setResourceFactory(resourceFactory);
        builder.setEnableBasicAuth(true);
        return builder.buildHttpManager();
    }

    @Bean
    public FilterRegistrationBean<MiltonFilter> miltonFilter(HttpManager httpManager) {
        FilterRegistrationBean<MiltonFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new MiltonFilter(httpManager));
        registration.addUrlPatterns("/webdav/*"); // Mount WebDAV at /webdav
        registration.setName("MiltonFilter");
        registration.setOrder(1); // Ensure it runs
        return registration;
    }
}
